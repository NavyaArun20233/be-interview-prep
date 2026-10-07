package com.interviewprep.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 and 403 responses raised by the Spring Security filter chain (before any controller runs, so
 * {@link GlobalExceptionHandler} cannot see them) as RFC 9457 Problem Details with the same fields as that handler.
 *
 * <p>Status and the {@code WWW-Authenticate: Bearer ...} header are set by Spring's bearer-token handlers; this class
 * only adds the JSON body. Details are fixed strings so that token parsing errors are never echoed to clients.
 */
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String AUTHENTICATION_REQUIRED = "Authentication is required to access this resource";
    static final String INVALID_TOKEN = "The access token is invalid or has expired";
    static final String ACCESS_DENIED = "You do not have permission to access this resource";

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    public ProblemDetailsSecurityHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, authException);
        String detail =
                authException instanceof OAuth2AuthenticationException ? INVALID_TOKEN : AUTHENTICATION_REQUIRED;
        write(request, response, HttpStatus.UNAUTHORIZED, detail);
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
        write(request, response, HttpStatus.FORBIDDEN, ACCESS_DENIED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException, ServletException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
