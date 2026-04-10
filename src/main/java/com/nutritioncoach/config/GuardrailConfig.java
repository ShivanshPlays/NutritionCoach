package com.nutritioncoach.config;

import com.nutritioncoach.guardrail.ApiKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * GuardrailConfig — Register Phase 6 interceptors with Spring MVC
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Spring MVC does not scan HandlerInterceptors automatically.  They must be
 * registered via a WebMvcConfigurer.  This class does that for Phase 6.
 *
 * InputGuardrailFilter (OncePerRequestFilter) is auto-registered because it
 * is annotated with @Component — Spring Boot's auto-registration picks it up
 * without any explicit FilterRegistrationBean.
 *
 * MERN/Next.js analogy:
 *   In Express this is equivalent to calling app.use() for each middleware:
 *
 *     app.use('/api', apiKeyMiddleware)
 *     app.use('/api', rateLimitMiddleware)
 *
 *   In Next.js it is the middleware.ts file with a matcher config.
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   Centralising interceptor registration makes it easy to audit which
 *   guardrails are in place and in what order they run.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Configuration
public class GuardrailConfig implements WebMvcConfigurer {

    // MERN analogy: const { apiKeyMiddleware } = dependencies
    private final ApiKeyInterceptor apiKeyInterceptor;

    public GuardrailConfig(ApiKeyInterceptor apiKeyInterceptor) {
        this.apiKeyInterceptor = apiKeyInterceptor;
    }

    /**
     * Register ApiKeyInterceptor for all /api/** paths.
     *
     * MERN analogy: app.use('/api', apiKeyMiddleware)
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor).addPathPatterns("/api/**");
    }
}
