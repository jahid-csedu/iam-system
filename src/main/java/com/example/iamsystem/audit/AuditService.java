package com.example.iamsystem.audit;

import com.example.iamsystem.audit.enums.AuditEventType;
import com.example.iamsystem.audit.enums.AuditOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("auditLogger");
    private static final String AUDIT_SCHEMA_VERSION = "1.0"; // Define audit log schema version

    public Map<String, Object> getRequestDetails(HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        if (request != null) {
            details.put("ip_address", request.getRemoteAddr());
            details.put("user_agent", request.getHeader("User-Agent"));
        }
        return details;
    }

    @Async
    public void logAuditEvent(AuditEventType eventType, String actor, String target, AuditOutcome outcome, Map<String, Object> details, String callingClass, String callingMethod) {
        try {
            MDC.put("log_type", "audit");
            MDC.put("audit_schema_version", AUDIT_SCHEMA_VERSION);
            MDC.put("event_type", eventType.name());
            MDC.put("actor", actor);
            MDC.put("target", target);
            MDC.put("outcome", outcome.name());
            MDC.put("calling_class", callingClass);
            MDC.put("calling_method", callingMethod);

            if (details != null) {
                details.forEach((key, value) -> MDC.put(key, String.valueOf(value)));
            }

            if (AuditOutcome.SUCCESS == outcome) {
                AUDIT_LOGGER.info("Audit event occurred.");
            } else {
                AUDIT_LOGGER.error("Audit event occurred.");
            }
        } finally {
            MDC.clear();
        }
    }
}
