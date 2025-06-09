package com.ashcollege;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // ✅ אפשר גם במקום setAllowedOrigins להשתמש ב־setAllowedOriginPatterns
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "https://mathjourney-front.onrender.com"
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setExposedHeaders(Arrays.asList("Authorization"));
        config.setAllowCredentials(false);  // או true אם אי פעם תשתמשי בקוקיז / login-session

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
