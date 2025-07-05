package com.example.iamsystem.audit.aspect;

import com.example.iamsystem.audit.AuditService;
import com.example.iamsystem.audit.annotation.Auditable;
import com.example.iamsystem.audit.enums.AuditOutcome;
import com.example.iamsystem.security.user.DefaultUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    public static final String REASON = "reason";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String NA = "N/A";
    public static final String NA_SPEL_ERROR = "N/A (SpEL Error)";
    public static final String RESULT = "result";
    public static final String EXCEPTION = "exception";
    public static final String NA_NULL_TARGET = "N/A (Null Target)";
    private final AuditService auditService;
    private final HttpServletRequest request;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.example.iamsystem.audit.annotation.Auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Auditable auditable = signature.getMethod().getAnnotation(Auditable.class);

        String actor = getActor();
        String target = resolveTarget(auditable.target(), joinPoint.getArgs(), signature.getParameterNames());
        Map<String, Object> commonDetails = auditService.getRequestDetails(request);
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal instanceof DefaultUserDetails userDetails) {
            commonDetails.put("current_user", userDetails.getUsername());
        } else {
            commonDetails.put("current_user", NA);
        }

        Object result;
        try {
            result = joinPoint.proceed();
            Map<String, Object> additionalDetails = resolveDetails(auditable.detailsExpression(), joinPoint.getArgs(), signature.getParameterNames(), result, null);
            Map<String, Object> finalDetails = new HashMap<>(commonDetails);
            finalDetails.putAll(additionalDetails);
            auditService.logAuditEvent(auditable.value(), actor, target, AuditOutcome.SUCCESS, finalDetails, signature.getDeclaringTypeName(), signature.getName());
        } catch (Throwable e) {
            Map<String, Object> additionalDetails = resolveDetails(auditable.detailsExpression(), joinPoint.getArgs(), signature.getParameterNames(), null, e);
            Map<String, Object> finalDetails = new HashMap<>(commonDetails);
            finalDetails.putAll(additionalDetails);
            finalDetails.put(REASON, e.getMessage());
            auditService.logAuditEvent(auditable.value(), actor, target, AuditOutcome.FAILURE, finalDetails, signature.getDeclaringTypeName(), signature.getName());
            throw e;
        }
        return result;
    }

    private String getActor() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof DefaultUserDetails userDetails) {
                return userDetails.getUsername();
            }
        } catch (Exception e) {
            log.warn("Could not determine actor from SecurityContext: {}", e.getMessage());
        }
        return UNKNOWN;
    }

    private String resolveTarget(String spelExpression, Object[] args, String[] parameterNames) {
        if (spelExpression == null || spelExpression.isEmpty()) {
            return NA;
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        try {
            // Attempt to parse and evaluate the SpEL expression
            Object value = parser.parseExpression(spelExpression).getValue(context);
            return value != null ? String.valueOf(value) : NA_NULL_TARGET;
        } catch (Exception e) {
            log.warn("Error evaluating SpEL expression '{}' for target: {}", spelExpression, e.getMessage());
            return NA_SPEL_ERROR;
        }
    }

    private Map<String, Object> resolveDetails(String spelExpression,
                                               Object[] args,
                                               String[] parameterNames,
                                               Object result,
                                               Throwable exception) {
        if (spelExpression == null || spelExpression.trim().isEmpty()) {
            return new HashMap<>();
        }

        StandardEvaluationContext context = new StandardEvaluationContext();

        // Inject method parameters into context
        if (parameterNames != null && args != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // Add #result and #exception as variables (if available)
        context.setVariable(RESULT, result);
        context.setVariable(EXCEPTION, exception);

        try {
            Object value = parser.parseExpression(spelExpression).getValue(context);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> casted = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    if (key != null) {
                        casted.put(String.valueOf(key), entry.getValue());
                    }
                }
                return casted;
            } else {
                log.warn("SpEL expression did not return a Map. Expression: '{}'", spelExpression);
                return new HashMap<>();
            }
        } catch (Exception e) {
            log.warn("Error evaluating SpEL expression for audit details: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}

