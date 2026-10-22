// Steps - SecurityConfig
// Require HTTP Basic authentication for every request.
// Read the single demo user from ORDERFLOW_USER / ORDERFLOW_PASSWORD and fail at startup if missing.
// Keep the API stateless (no sessions, no CSRF cookie).
// Python: Similar to a FastAPI HTTPBasic dependency, except it is applied to the whole app here.
package com.orderflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF protects browser sessions; a stateless Basic Auth API has none to protect.
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${orderflow.auth.user:}") String username,
            @Value("${orderflow.auth.password:}") String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "ORDERFLOW_USER and ORDERFLOW_PASSWORD environment variables must both be set.");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("USER")
                        .build());
    }
}
