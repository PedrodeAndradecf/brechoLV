package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.dto.request.LoginRequestDTO;
import Backend.BrechoAPI.dto.request.RegisterRequestDTO;
import Backend.BrechoAPI.dto.response.AuthResponseDTO;
import Backend.BrechoAPI.dto.response.ClientResponseDTO;
import Backend.BrechoAPI.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    
    @PostMapping("/register")
    public ResponseEntity<ClientResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
}
