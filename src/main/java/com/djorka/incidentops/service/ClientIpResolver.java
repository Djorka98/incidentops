package com.djorka.incidentops.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class ClientIpResolver {

    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");
    private final boolean vercelEnvironment;

    public ClientIpResolver(@Value("${VERCEL:}") String vercelEnvironment) {
        this.vercelEnvironment = "1".equals(vercelEnvironment)
                || "true".equalsIgnoreCase(vercelEnvironment);
    }

    public Optional<String> resolve(HttpServletRequest request) {
        String candidate = vercelEnvironment
                ? request.getHeader("x-vercel-forwarded-for")
                : request.getRemoteAddr();

        if (!StringUtils.hasText(candidate)) {
            return Optional.empty();
        }

        String value = candidate.trim();
        return IP_LITERAL.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }
}
