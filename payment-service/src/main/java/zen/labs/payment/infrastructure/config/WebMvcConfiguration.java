/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.payment.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@SuppressWarnings({"java:S6857" // false positive; property names may contain dashes
})
public class WebMvcConfiguration implements WebMvcConfigurer {

    /**
     * This allows you to set the allowed-origins in an environment variable or
     * application.yaml/properties. Its not unusual to have a variety of origins in
     * a cloud environment; for example, something like: api.acme.com,
     * admin.acme.com, some-application.acme.com.
     * The default, localhost, is usually suitable for development.
     *
     * There are no other dependencies on this property name; rename it if you want.
     */
    @Value("${application.cors.allowed-origins:http://*.localhost}")
    private String allowedOriginsPattern;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .allowedOriginPatterns(allowedOriginsPattern);
    }
}
