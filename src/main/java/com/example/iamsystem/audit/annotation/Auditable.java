package com.example.iamsystem.audit.annotation;

import com.example.iamsystem.audit.enums.AuditEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
    AuditEventType value();
    String target() default ""; // Optional: to specify target if not derivable from method args
    String detailsExpression() default ""; // SpEL expression to generate a Map<String, Object> for additional details
}
