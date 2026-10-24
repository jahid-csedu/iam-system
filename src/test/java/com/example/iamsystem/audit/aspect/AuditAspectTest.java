package com.example.iamsystem.audit.aspect;

import com.example.iamsystem.audit.AuditService;
import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.audit.enums.AuditOutcome;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditService auditService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private HttpServletRequest request;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    @InjectMocks
    private AuditAspect aspect;

    @Captor
    private ArgumentCaptor<Map<String, Object>> details;

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"bob"});
        when(signature.getParameterNames()).thenReturn(new String[]{"username"});
        when(signature.getDeclaringTypeName()).thenReturn("SampleService");
        when(signature.getName()).thenReturn("deleteUser");
        when(auditService.getRequestDetails(request)).thenReturn(new HashMap<>(Map.of("ip_address", "127.0.0.1")));
    }

    @Test
    @DisplayName("A successful call logs the actor, target and details")
    void audit_onSuccess_logsActorTargetAndDetails() throws Throwable {
        givenCurrentUser("alice");
        when(joinPoint.proceed()).thenReturn("deleted");

        Object result = aspect.audit(joinPoint, annotationOf("withDetails"));

        assertEquals("deleted", result);
        verify(auditService).logAuditEvent(eq(AuditEventType.USER_DELETE), eq("alice"), eq("bob"),
                eq(AuditOutcome.SUCCESS), details.capture(), eq("SampleService"), eq("deleteUser"));
        assertEquals("127.0.0.1", details.getValue().get("ip_address"));
        assertEquals("alice", details.getValue().get("current_user"));
        assertEquals("deleted", details.getValue().get("outcome_value"));
    }

    @Test
    @DisplayName("A failed call logs the reason and rethrows the exception")
    void audit_onFailure_logsReasonAndRethrows() throws Throwable {
        givenCurrentUser("alice");
        IllegalStateException failure = new IllegalStateException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> aspect.audit(joinPoint, annotationOf("withDetails")));

        assertSame(failure, thrown);
        verify(auditService).logAuditEvent(eq(AuditEventType.USER_DELETE), eq("alice"), eq("bob"),
                eq(AuditOutcome.FAILURE), details.capture(), eq("SampleService"), eq("deleteUser"));
        assertEquals("boom", details.getValue().get("reason"));
    }

    @Test
    @DisplayName("Without a logged-in user, placeholders are logged")
    void audit_withoutLoggedInUser_usesPlaceholders() throws Throwable {
        when(currentUserProvider.currentActor()).thenReturn(Optional.empty());

        aspect.audit(joinPoint, annotationOf("withoutTarget"));

        verify(auditService).logAuditEvent(eq(AuditEventType.USER_DELETE), eq("UNKNOWN"), eq("N/A"),
                eq(AuditOutcome.SUCCESS), details.capture(), eq("SampleService"), eq("deleteUser"));
        assertEquals("N/A", details.getValue().get("current_user"));
    }

    @Test
    @DisplayName("A broken SpEL expression still logs the event")
    void audit_withBrokenExpression_stillLogsTheEvent() throws Throwable {
        givenCurrentUser("alice");

        aspect.audit(joinPoint, annotationOf("withBrokenExpressions"));

        verify(auditService).logAuditEvent(eq(AuditEventType.USER_DELETE), eq("alice"), eq("N/A (SpEL Error)"),
                eq(AuditOutcome.SUCCESS), details.capture(), eq("SampleService"), eq("deleteUser"));
    }

    private void givenCurrentUser(String username) {
        when(currentUserProvider.currentActor()).thenReturn(Optional.of(new AuthenticatedActor(1L, username, 100L, false)));
    }

    private static Auditable annotationOf(String methodName) throws NoSuchMethodException {
        return SampleService.class.getDeclaredMethod(methodName, String.class).getAnnotation(Auditable.class);
    }

    private static class SampleService {
        @Auditable(value = AuditEventType.USER_DELETE, target = "#username",
                detailsExpression = "T(java.util.Map).of('outcome_value', #result)")
        void withDetails(String username) {
        }

        @Auditable(value = AuditEventType.USER_DELETE)
        void withoutTarget(String username) {
        }

        @Auditable(value = AuditEventType.USER_DELETE, target = "#username.missingProperty",
                detailsExpression = "'not a map'")
        void withBrokenExpressions(String username) {
        }
    }
}
