package com.example.iamsystem.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditService {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("auditLogger");
    private static final String AUDIT_SCHEMA_VERSION = "1.0"; // Define audit log schema version

    @Async
    public void logAuditEvent(String eventType, String actor, String target, String outcome, Map<String, Object> details, String callingClass, String callingMethod) {
        try {
            MDC.put("log_type", "audit");
            MDC.put("audit_schema_version", AUDIT_SCHEMA_VERSION);
            MDC.put("event_type", eventType);
            MDC.put("actor", actor);
            MDC.put("target", target);
            MDC.put("outcome", outcome);
            MDC.put("calling_class", callingClass);
            MDC.put("calling_method", callingMethod);

            if (details != null) {
                details.forEach((key, value) -> MDC.put(key, String.valueOf(value)));
            }

            if ("SUCCESS".equalsIgnoreCase(outcome)) {
                AUDIT_LOGGER.info("Audit event occurred.");
            } else {
                AUDIT_LOGGER.error("Audit event occurred.");
            }
        } finally {
            MDC.clear();
        }
    }
}
