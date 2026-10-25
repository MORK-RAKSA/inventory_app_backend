package com.retailflow.config;

import com.retailflow.controller.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
  private final AuthController auth;

  public AuthFilter(AuthController auth) {
    this.auth = auth;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return !(path.startsWith("/api/")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs"))
        || path.equals("/api/health")
        || path.equals("/api/auth/login")
        || path.equals("/api/auth/refresh")
        || path.equals("/api/auth/logout");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var currentUser = auth.userForAccess(request.getHeader("Authorization"));
    if (currentUser == null) {
      response.setStatus(401);
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"Authentication required\"}");
      return;
    }
    request.setAttribute("currentUser", currentUser);
    chain.doFilter(request, response);
  }
}
