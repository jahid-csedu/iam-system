package com.example.iamsystem.audit.aspect;

import com.example.iamsystem.audit.AuditService;
import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditOutcome;
import com.example.iamsystem.security.userdetails.AuthenticatedActor;
import com.example.iamsystem.security.userdetails.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Writes an audit event for every {@link Auditable} method, on success and on failure.
 * <p>
 * {@code target} and {@code detailsExpression} are SpEL expressions evaluated against the method arguments
 * (by parameter name), plus {@code #result} and {@code #exception} for the details.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private static final String UNKNOWN_ACTOR = "UNKNOWN";
    private static final String NOT_AVAILABLE = "N/A";
    private static final String TARGET_SPEL_ERROR = "N/A (SpEL Error)";
    private static final String TARGET_NULL = "N/A (Null Target)";
    private static final String CURRENT_USER = "current_user";
    private static final String REASON = "reason";
    private static final String RESULT = "result";
    private static final String EXCEPTION = "exception";

    private final ExpressionParser parser = new SpelExpressionParser();
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final HttpServletRequest request;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        EvaluationContext context = argumentsContext(signature.getParameterNames(), joinPoint.getArgs());
        Optional<String> username = currentUserProvider.currentActor().map(AuthenticatedActor::username);
        String actor = username.orElse(UNKNOWN_ACTOR);
        String target = resolveTarget(auditable.target(), context);

        Map<String, Object> details = auditService.getRequestDetails(request);
        details.put(CURRENT_USER, username.orElse(NOT_AVAILABLE));

        try {
            Object result = joinPoint.proceed();
            context.setVariable(RESULT, result);
            details.putAll(resolveDetails(auditable.detailsExpression(), context));
            auditService.logAuditEvent(auditable.value(), actor, target, AuditOutcome.SUCCESS, details,
                    signature.getDeclaringTypeName(), signature.getName());
            return result;
        } catch (Throwable e) {
            context.setVariable(EXCEPTION, e);
            details.putAll(resolveDetails(auditable.detailsExpression(), context));
            details.put(REASON, e.getMessage());
            auditService.logAuditEvent(auditable.value(), actor, target, AuditOutcome.FAILURE, details,
                    signature.getDeclaringTypeName(), signature.getName());
            throw e;
        }
    }

    private static EvaluationContext argumentsContext(String[] parameterNames, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        return context;
    }

    private String resolveTarget(String expression, EvaluationContext context) {
        if (expression.isBlank()) {
            return NOT_AVAILABLE;
        }
        try {
            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? String.valueOf(value) : TARGET_NULL;
        } catch (Exception e) {
            log.warn("Could not evaluate audit target '{}': {}", expression, e.getMessage());
            return TARGET_SPEL_ERROR;
        }
    }

    private Map<String, Object> resolveDetails(String expression, EvaluationContext context) {
        Map<String, Object> details = new HashMap<>();
        if (expression.isBlank()) {
            return details;
        }
        try {
            if (parser.parseExpression(expression).getValue(context) instanceof Map<?, ?> map) {
                map.forEach((key, value) -> {
                    if (key != null) {
                        details.put(String.valueOf(key), value);
                    }
                });
            } else {
                log.warn("Audit details expression did not return a map: '{}'", expression);
            }
        } catch (Exception e) {
            log.warn("Could not evaluate audit details '{}': {}", expression, e.getMessage());
        }
        return details;
    }
}
