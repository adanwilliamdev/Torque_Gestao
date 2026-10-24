package com.torquegestao.service;

import com.torquegestao.config.AppProperties;
import com.torquegestao.domain.RefreshToken;
import com.torquegestao.domain.Role;
import com.torquegestao.domain.User;
import com.torquegestao.dto.CreateUserRequest;
import com.torquegestao.dto.LoginRequest;
import com.torquegestao.dto.LoginResponse;
import com.torquegestao.dto.RefreshTokenRequest;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.exception.TooManyRequestsException;
import com.torquegestao.exception.UnauthorizedException;
import com.torquegestao.repository.RefreshTokenRepository;
import com.torquegestao.repository.UserRepository;
import com.torquegestao.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    /** bcrypt só considera os primeiros 72 bytes; senhas maiores são recusadas. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CacheService cache;
    private final AppProperties props;

    /**
     * Hash válido de uma senha aleatória: usado para gastar o mesmo tempo de CPU quando o e-mail
     * não existe, evitando revelar quais e-mails estão cadastrados pelo tempo de resposta.
     */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder,
            JwtService jwt,
            CacheService cache,
            AppProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.cache = cache;
        this.props = props;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest data, String clientIp) {
        String rateKey = "rl:login:" + (clientIp == null ? "unknown" : clientIp) + ":"
                + data.email().toLowerCase();
        if (cache.hitRateLimit(
                rateKey, props.getLoginRateLimitAttempts(), props.getLoginRateLimitWindowSeconds())) {
            throw new TooManyRequestsException();
        }

        User user = users.findByEmail(data.email()).orElse(null);
        // Mensagem genérica de propósito: não revela se o e-mail existe. O bcrypt roda mesmo sem
        // usuário para que o tempo de resposta também não revele isso.
        boolean passwordOk = verifyPassword(data.password(), user == null ? null : user.getPasswordHash());
        if (user == null || !user.isActive() || !passwordOk) {
            throw new UnauthorizedException("Email ou senha inválidos");
        }

        String access = jwt.createAccessToken(user.getId(), user.getEmail());
        String refresh = issueRefreshToken(user.getId());
        return toResponse(user, access, refresh);
    }

    public LoginResponse refresh(RefreshTokenRequest data) {
        String hash = hashRefreshToken(data.refreshToken());
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("Refresh token inválido"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Refresh token expirado ou revogado. Faça login novamente");
        }

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        if (!user.isActive()) {
            throw new UnauthorizedException("Usuário não encontrado ou inativo");
        }

        // Rotação: o token antigo é revogado com um UPDATE condicional. Se duas requisições
        // tentarem usar o mesmo token ao mesmo tempo, só uma delas consegue revogá-lo.
        if (refreshTokens.revokeIfActive(stored.getId()) == 0) {
            throw new BusinessException("Refresh token expirado ou revogado. Faça login novamente");
        }

        String newRefresh = issueRefreshToken(user.getId());
        String access = jwt.createAccessToken(user.getId(), user.getEmail());
        return toResponse(user, access, newRefresh);
    }

    public void logout(RefreshTokenRequest data) {
        refreshTokens.revokeByHash(hashRefreshToken(data.refreshToken()));
    }

    public void createUserByAdmin(CreateUserRequest data) {
        createUser(data.name(), data.email(), data.password(), data.role());
    }

    /** Cria um usuário (usado também pelo seed do administrador inicial). */
    public User createUser(String name, String email, String password, Role role) {
        if (users.existsByEmail(email)) {
            throw new BusinessException("Este email já está cadastrado");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setActive(true);
        return users.saveAndFlush(user);
    }

    private boolean verifyPassword(String raw, String hash) {
        String effective = hash != null ? hash : dummyHash;
        boolean matches = raw.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && encoder.matches(raw, effective);
        return matches && hash != null;
    }

    private String issueRefreshToken(UUID userId) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(hashRefreshToken(token));
        entity.setUserId(userId);
        entity.setExpiresAt(Instant.now().plus(props.getJwt().getRefreshTokenDays(), ChronoUnit.DAYS));
        refreshTokens.save(entity);
        return token;
    }

    /** O refresh token nunca é guardado em texto puro: um vazamento do banco não entrega sessões. */
    static String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static LoginResponse toResponse(User user, String access, String refresh) {
        return new LoginResponse(
                access, refresh, user.getId().toString(), user.getName(), user.getEmail(), user.getRole());
    }
}
