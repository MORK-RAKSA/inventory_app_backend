package com.retailflow.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Value("${retail-flow.cors.origins:*}")
  private String origins;

  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilterRegistrationBean() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(false);
    config.addAllowedOriginPattern("*");
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.addExposedHeader("X-Server-Updated-At");
    config.setMaxAge(86400L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> patterns = new ArrayList<>();
    if (origins != null && !origins.isBlank() && !origins.equals("*")) {
      Arrays.stream(origins.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .forEach(patterns::add);
    }
    // Always permit localhost dev (any port), 127.0.0.1, Vercel deployments, Render, Expo, and
    // wildcards
    patterns.add("http://localhost:[*]");
    patterns.add("http://localhost:*");
    patterns.add("http://127.0.0.1:[*]");
    patterns.add("http://127.0.0.1:*");
    patterns.add("http://192.168.*:[*]");
    patterns.add("http://192.168.*:*");
    patterns.add("http://10.*:[*]");
    patterns.add("http://10.*:*");
    patterns.add("https://*.vercel.app");
    patterns.add("https://*.onrender.com");
    patterns.add("exp://*");
    patterns.add("*");

    registry
        .addMapping("/**")
        .allowedOriginPatterns(patterns.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("X-Server-Updated-At")
        .maxAge(86400);
  }
}
