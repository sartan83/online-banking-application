package com.devilsvault.api.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP advice that records every successful {@link
 * org.springframework.web.bind.annotation.RestController} invocation into {@code audit_log}.
 *
 * <p>The advice intentionally records only successful executions; exceptions propagate untouched
 * so error responses keep their original 4xx/5xx semantics. Auth/swagger endpoints are excluded
 * because they fire before {@link SecurityContextHolder} is populated and would otherwise produce
 * anonymous noise.
 */
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogService service;

    public AuditLogAspect(AuditLogService service) {
        this.service = service;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)"
            + " && !within(com.devilsvault.api.auth.AuthController)"
            + " && !within(com.devilsvault.api.audit..*)")
    public Object recordInvocation(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String action = sig.getDeclaringType().getSimpleName() + "." + sig.getName();
            String username = currentUsername();
            String details = describe(pjp);
            String ip = currentIp();
            service.record(username, action, details, ip);
        } catch (RuntimeException ignored) {
            // Audit logging must never mask a successful business operation.
        }
        return result;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        return "anonymousUser".equals(name) ? null : name;
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
            }
            return req.getRemoteAddr();
        }
        return null;
    }

    private static String describe(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] names = sig.getParameterNames();
        Object[] args = pjp.getArgs();
        if (names == null || names.length == 0) {
            return "()";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names[i]).append('=').append(safe(args[i]));
        }
        sb.append(')');
        return sb.toString();
    }

    private static String safe(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof java.security.Principal p) {
            return p.getName();
        }
        if (o instanceof CharSequence || o instanceof Number || o instanceof Boolean) {
            return o.toString();
        }
        // Avoid logging full DTO bodies (may contain passwords/codes); just record the type.
        return o.getClass().getSimpleName();
    }
}
