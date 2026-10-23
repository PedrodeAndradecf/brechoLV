package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Cart;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.dto.request.LoginRequestDTO;
import Backend.BrechoAPI.dto.request.RegisterRequestDTO;
import Backend.BrechoAPI.dto.response.AuthResponseDTO;
import Backend.BrechoAPI.dto.response.ClientResponseDTO;
import Backend.BrechoAPI.exception.DuplicateResourceException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.InvalidCredentialsException;
import Backend.BrechoAPI.repository.CartRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;










@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    














    @Transactional
    public ClientResponseDTO register(RegisterRequestDTO request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "já existe conta com esse e-mail");
        }

        User client = userRepository.save(User.createClient(
                request.name(), email, passwordEncoder.encode(request.password()), request.phone()));

        cartRepository.save(Cart.create(client));

        return new ClientResponseDTO(client.getId(), client.getName(),
                client.getEmail(), client.getPhone());
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        
        
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email().trim().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("credenciais inválidas");
        }

        String accessToken = jwtService.generate(user);
        return AuthResponseDTO.bearer(
                accessToken,
                jwtService.getExpirationMillis() / 1000,
                user.getId(),
                user.getName(),
                user.getRole());
    }
}
