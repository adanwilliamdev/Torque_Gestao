package com.torquegestao.config;

import com.torquegestao.domain.Role;
import com.torquegestao.repository.UserRepository;
import com.torquegestao.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.Base64;

/** Cria o usuário administrador inicial, se ainda não existir ({@code app.seed.*}). */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties props;
    private final UserRepository users;
    private final AuthService authService;

    public DataSeeder(AppProperties props, UserRepository users, AuthService authService) {
        this.props = props;
        this.users = users;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = props.getSeed();
        if (!seed.isEnabled()) {
            return;
        }
        String password = seed.getAdminPassword();
        boolean blank = password == null || password.isBlank();
        boolean weak = AppProperties.DEFAULT_ADMIN_PASSWORD.equals(password);
        if (props.isProduction() && (blank || weak)) {
            log.warn("Seed do administrador ignorado: defina SEED_ADMIN_PASSWORD (forte) em produção.");
            return;
        }
        if (users.existsByEmail(seed.getAdminEmail())) {
            log.info("Usuário admin já existe, pulando seed.");
            return;
        }
        if (blank) {
            password = randomPassword();
        }
        authService.createUser("Administrador", seed.getAdminEmail(), password, Role.ADMIN);
        log.info("Usuário admin criado: {}", seed.getAdminEmail());
        if (blank) {
            // Única vez em que a senha aparece; fora de produção, apenas para o primeiro acesso.
            log.warn("Senha inicial gerada para {}: {}  (troque após o primeiro acesso)",
                    seed.getAdminEmail(), password);
        } else if (weak) {
            log.warn("Senha fraca em uso — defina SEED_ADMIN_PASSWORD fora de desenvolvimento.");
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[15];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
