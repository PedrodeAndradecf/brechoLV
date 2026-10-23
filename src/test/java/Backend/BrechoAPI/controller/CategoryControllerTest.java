package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.WebSecuritySliceConfig;
import Backend.BrechoAPI.dto.response.CategoryResponseDTO;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetailsService;
import Backend.BrechoAPI.security.JwtAuthenticationFilter;
import Backend.BrechoAPI.security.RestAccessDeniedHandler;
import Backend.BrechoAPI.security.RestAuthenticationEntryPoint;
import Backend.BrechoAPI.security.SecurityConfig;
import Backend.BrechoAPI.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;









@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, WebSecuritySliceConfig.class})
class CategoryControllerTest {

    private static final String CORPO_VALIDO = """
            {"name":"Vestidos","slug":"vestidos","sizeType":"ALPHA"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void listaPublicaSemTokenResponde200() throws Exception {
        when(categoryService.list()).thenReturn(List.of());

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void criarSemTokenResponde401() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void criarComPapelErradoResponde403() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void criarComPapelCertoAtravessaAAutorizacao() throws Exception {
        when(categoryService.create(any()))
                .thenReturn(new CategoryResponseDTO(UUID.randomUUID(), "Vestidos", "vestidos", CategorySizeType.ALPHA));

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated());
    }
}
