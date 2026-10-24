package com.torquegestao.config;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurações da aplicação (prefixo {@code app.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    public static final String PLACEHOLDER_JWT_SECRET =
            "troque-este-segredo-em-producao-para-uma-string-longa-e-aleatoria";
    /** Senha fraca conhecida: recusada em produção. */
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private String environment = "development";
    private String timezone = "America/Sao_Paulo";
    private Jwt jwt = new Jwt();
    private String corsAllowedOrigins = "http://localhost:8080";
    private int loginRateLimitAttempts = 10;
    private int loginRateLimitWindowSeconds = 60;
    private int dashboardCacheTtlSeconds = 30;
    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Jwt {
        private String secret = PLACEHOLDER_JWT_SECRET;
        private long expirationMs = 900_000L; // 15 min; renovação via refresh token
        private int refreshTokenDays = 7;
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = true;
        private String adminEmail = "admin@torquegestao.com";
        /** Vazio = gera uma senha aleatória (só fora de produção) e a exibe uma única vez no log. */
        private String adminPassword = "";
    }

    @PostConstruct
    void validate() {
        if (isProduction()
                && (PLACEHOLDER_JWT_SECRET.equals(jwt.getSecret()) || jwt.getSecret().length() < 32)) {
            throw new IllegalStateException(
                    "JWT_SECRET precisa ser definido com um valor forte (32+ caracteres) em produção");
        }
        if (jwt.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 bytes");
        }
    }

    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment);
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public List<String> corsOrigins() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public long accessTokenTtlSeconds() {
        return jwt.getExpirationMs() / 1000;
    }
}
