package Backend.BrechoAPI.security;

import Backend.BrechoAPI.config.AmbienteDeExecucao;
import Backend.BrechoAPI.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

























@Service
public class JwtService {

    







    private static final String RESUMO_DO_SEGREDO_DE_DESENVOLVIMENTO =
            "1b46d5ec550ef88f1e3c9a6cc5b83c6cd782c869adb3081f8acbc97ae5484c20";

    private final SecretKey key;
    private final long expirationMillis;
    private final Clock clock;

    





    public JwtService(String secret, long expirationMillis, Clock clock) {
        this(secret, expirationMillis, clock, new AmbienteDeExecucao("desenvolvimento"));
    }

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expirationMillis,
                      Clock clock,
                      AmbienteDeExecucao ambiente) {
        byte[] bytes = Decoders.BASE64.decode(secret);
        if (!ambiente.isDesenvolvimento() && ehOSegredoDeDesenvolvimento(bytes)) {
            throw new IllegalStateException(
                    "JWT_SECRET é o valor de DESENVOLVIMENTO versionado na .run/ — em produção"
                            + " (brecho.ambiente ausente ou vazio) a aplicação recusa subir com"
                            + " ele. Defina JWT_SECRET"
                            + " com um segredo próprio deste ambiente, ou declare"
                            + " brecho.ambiente=desenvolvimento se esta for uma máquina de"
                            + " desenvolvimento");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMillis = expirationMillis;
        this.clock = clock;
    }

    




    private static boolean ehOSegredoDeDesenvolvimento(byte[] chave) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(chave);
            return RESUMO_DO_SEGREDO_DE_DESENVOLVIMENTO.equalsIgnoreCase(
                    HexFormat.of().formatHex(resumo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    public String generate(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(key)
                .compact();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public String extractEmail(String token) {
        return parse(token).get("email", String.class);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
