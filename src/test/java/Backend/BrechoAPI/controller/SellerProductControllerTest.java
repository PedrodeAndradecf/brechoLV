package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.WebSecuritySliceConfig;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetailsService;
import Backend.BrechoAPI.security.JwtAuthenticationFilter;
import Backend.BrechoAPI.security.RestAccessDeniedHandler;
import Backend.BrechoAPI.security.RestAuthenticationEntryPoint;
import Backend.BrechoAPI.security.SecurityConfig;
import Backend.BrechoAPI.service.SellerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;












@WebMvcTest(SellerProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, WebSecuritySliceConfig.class})
class SellerProductControllerTest {

    private static final String CORPO_VALIDO = """
            {"title":"Vestido floral","description":"lindo","brand":"Zara",
             "size":"M","color":"azul","condition":"GOOD","price":89.90,
             "categoryId":"11111111-1111-1111-1111-111111111111"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerService sellerService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post("/seller/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/seller/products"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void comPapelErradoResponde403() throws Exception {
        mockMvc.perform(post("/seller/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/seller/products"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
