package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


















@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenTest {

    private static final String SECRET = "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=";
    
    private static final String SECRET_FORJADO = "Zm9yamFkbzEyMzQ1Njc4OTBhYmNkZWZnaGlqa2xtbm8=";
    private static final long TRINTA_MIN = 1_800_000L;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> SECRET);
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    
    @Autowired Clock clock;

    





    @Test
    void tokenExpiradoNaoAbreRotaProtegida() throws Exception {
        User vendedora = seed(Role.SELLER);

        
        Clock passado = Clock.fixed(clock.instant().minus(Duration.ofDays(2)), ZoneOffset.UTC);
        String token = new JwtService(SECRET, TRINTA_MIN, passado).generate(vendedora);

        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    
    @Test
    void tokenDentroDaValidadeAbre() throws Exception {
        User vendedora = seed(Role.SELLER);
        String token = new JwtService(SECRET, TRINTA_MIN, clock).generate(vendedora);

        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    



    @Test
    void tokenAssinadoComOutraChaveNaoAbre() throws Exception {
        User vendedora = seed(Role.SELLER);
        String forjado = new JwtService(SECRET_FORJADO, TRINTA_MIN, clock).generate(vendedora);

        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + forjado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    






    @Test
    void papelRebaixadoNoBancoFechaRotaMesmoComTokenAntigoDeSeller() throws Exception {
        User pessoa = seed(Role.SELLER);
        String token = new JwtService(SECRET, TRINTA_MIN, clock).generate(pessoa);

        
        jdbcTemplate.update("UPDATE users SET role = 'CLIENT' WHERE id = ?", pessoa.getId());

        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    
    @Test
    void papelPromovidoNoBancoAbreRotaMesmoComTokenAntigoDeClient() throws Exception {
        User pessoa = seed(Role.CLIENT);
        String token = new JwtService(SECRET, TRINTA_MIN, clock).generate(pessoa);

        jdbcTemplate.update("UPDATE users SET role = 'SELLER' WHERE id = ?", pessoa.getId());

        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    






















    @Test
    void contaArquivadaInvalidaTokenJaEmitido() throws Exception {
        User vendedora = seed(Role.SELLER);
        String token = new JwtService(SECRET, TRINTA_MIN, clock).generate(vendedora);

        
        
        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET deleted_at = now() WHERE id = ?", vendedora.getId());

        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private User seed(Role role) {
        return userRepository.save(User.createWithRole(
                "Pessoa", "p-" + UUID.randomUUID() + "@teste.local", "hash", role));
    }
}
