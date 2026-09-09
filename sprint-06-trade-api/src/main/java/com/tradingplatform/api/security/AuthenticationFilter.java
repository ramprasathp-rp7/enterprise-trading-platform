package com.tradingplatform.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.enums.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class AuthenticationFilter extends OncePerRequestFilter {

    private final AuthTokenVerifier tokenVerifier;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(AuthTokenVerifier tokenVerifier, ObjectMapper objectMapper) {
        this.tokenVerifier = tokenVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authorisationHeader = request.getHeader("Authorization");

        if (authorisationHeader == null || !authorisationHeader.startsWith("Bearer ")) {
            writeUnauthorised(response);
            return;
        }

        String token = authorisationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            writeUnauthorised(response);
            return;
        }

        try {
            AuthenticatedUser authenticatedUser = tokenVerifier.verify(token);
            request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, authenticatedUser);
            filterChain.doFilter(request, response);
        } catch (InvalidTokenException ex) {
            writeUnauthorised(response);
        }
    }

    private void writeUnauthorised(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(ErrorCode.AUTH_401, "Unauthorised"));
    }
}


