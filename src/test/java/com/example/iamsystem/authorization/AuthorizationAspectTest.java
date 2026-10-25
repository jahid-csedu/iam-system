package com.example.iamsystem.authorization;

import com.example.iamsystem.common.exception.NoAccessException;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.iamsystem.permission.PermissionKey.USERS_UPDATE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationAspectTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AuthorizationAspect aspect;

    private final AuthenticatedActor actor = new AuthenticatedActor(1L, "alice", 100L, false, 10L);

    @Test
    @DisplayName("The annotated permission is checked for the current user")
    void checkPermission_checksTheAnnotatedPermissionForTheCurrentUser() throws NoSuchMethodException {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor);

        aspect.checkPermission(annotationOf("protectedEndpoint"));

        verify(authorizationService).checkPermission(actor, USERS_UPDATE);
    }

    @Test
    @DisplayName("Without a logged-in user, access is denied")
    void checkPermission_whenNobodyLoggedIn_isDenied() {
        when(currentUserProvider.requireCurrentActor()).thenThrow(new NoAccessException("No logged in user found"));

        assertThrows(NoAccessException.class, () -> aspect.checkPermission(annotationOf("protectedEndpoint")));
        verifyNoInteractions(authorizationService);
    }

    @Test
    @DisplayName("A missing permission propagates the denial")
    void checkPermission_whenPermissionMissing_propagatesDenial() throws NoSuchMethodException {
        when(currentUserProvider.requireCurrentActor()).thenReturn(actor);
        doThrow(new NoAccessException("denied")).when(authorizationService).checkPermission(actor, USERS_UPDATE);
        RequirePermission annotation = annotationOf("protectedEndpoint");

        assertThrows(NoAccessException.class, () -> aspect.checkPermission(annotation));
    }

    private static RequirePermission annotationOf(String methodName) throws NoSuchMethodException {
        return Endpoints.class.getDeclaredMethod(methodName).getAnnotation(RequirePermission.class);
    }

    private static class Endpoints {
        @RequirePermission(USERS_UPDATE)
        void protectedEndpoint() {
        }
    }
}
