package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.WebSecuritySliceConfig;
import Backend.BrechoAPI.dto.response.PagedResponseDTO;
import Backend.BrechoAPI.dto.response.ProductCardResponseDTO;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetailsService;
import Backend.BrechoAPI.security.JwtAuthenticationFilter;
import Backend.BrechoAPI.security.RestAccessDeniedHandler;
import Backend.BrechoAPI.security.RestAuthenticationEntryPoint;
import Backend.BrechoAPI.security.SecurityConfig;
import Backend.BrechoAPI.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;









@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, WebSecuritySliceConfig.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    
    
    
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void listaPublicaSemTokenResponde200() throws Exception {
        when(productService.listAvailable(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PagedResponseDTO.from(Page.<ProductCardResponseDTO>empty(PageRequest.of(0, 20))));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk());
    }

    @Test
    void detalheInexistenteResponde404ComContratoDeErro() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.findDetail(id)).thenThrow(new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "peça não encontrada"));

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("peça não encontrada"))
                .andExpect(jsonPath("$.path").value("/products/" + id))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
