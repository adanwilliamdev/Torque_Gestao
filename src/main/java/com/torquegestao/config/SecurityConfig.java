package com.torquegestao.config;

import com.torquegestao.repository.UserRepository;
import com.torquegestao.security.ApiErrorWriter;
import com.torquegestao.security.JwtAuthenticationFilter;
import com.torquegestao.security.JwtService;
import com.torquegestao.security.LoginRateLimitFilter;
import com.torquegestao.security.SessionUserRefreshFilter;
import com.torquegestao.service.CacheService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Duas cadeias de segurança:
 * <ul>
 *   <li>{@code /api/**}: stateless, JWT Bearer, erros em JSON;</li>
 *   <li>demais rotas (interface web): sessão + formulário de login + CSRF.</li>
 * </ul>
 * O controle por papel é feito com {@code @PreAuthorize} nos controllers (ver {@code Access}).
 *
 * <p><strong>Nota sobre CORS:</strong> o bean {@link #corsConfigurationSource(AppProperties)}
 * declara o tipo concreto {@link UrlBasedCorsConfigurationSource} (e não a interface
 * {@code CorsConfigurationSource}) de propósito. O Spring MVC registra automaticamente um
 * {@code mvcHandlerMappingIntrospector}, que também implementa {@code CorsConfigurationSource};
 * se o parâmetro aqui fosse a interface, o Spring encontraria <em>dois</em> beans candidatos e
 * falharia na inicialização. Tipar pelo concreto elimina a ambiguidade, pois o introspector do
 * MVC não é um {@code UrlBasedCorsConfigurationSource}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Fonte de CORS para a API. Retorna o tipo concreto {@link UrlBasedCorsConfigurationSource}
     * para evitar conflito com o {@code mvcHandlerMappingIntrospector} do Spring MVC, que também
     * implementa a interface {@code CorsConfigurationSource}.
     */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(
            HttpSecurity http,
            JwtService jwtService,
            UserRepository users,
            ApiErrorWriter errorWriter,
            UrlBasedCorsConfigurationSource cors)
            throws Exception {
        http.securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                // UrlBasedCorsConfigurationSource é um CorsConfigurationSource: aceito pelo
                // configurationSource(...) sem conversão explícita.
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/swagger-ui",
                                "/api/swagger-ui/**",
                                "/api/openapi.json",
                                "/api/openapi.json/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorWriter)
                        .accessDeniedHandler(errorWriter))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, users),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(
            HttpSecurity http, UserRepository users, CacheService cache, AppProperties props)
            throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/login", "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                "/webjars/**", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(f -> f
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .headers(h -> h.frameOptions(f -> f.deny()))
                .addFilterBefore(
                        new LoginRateLimitFilter(cache, props),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SessionUserRefreshFilter(users), AuthorizationFilter.class);
        return http.build();
    }
}