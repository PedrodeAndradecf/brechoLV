package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.dto.request.AddressRequestDTO;
import Backend.BrechoAPI.dto.response.AddressResponseDTO;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;








@RestController
@RequestMapping("/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<AddressResponseDTO> create(@AuthenticationPrincipal CustomUserDetails user,
                                                       @Valid @RequestBody AddressRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.create(user.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<AddressResponseDTO>> listOwn(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(addressService.listOwn(user.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressResponseDTO> update(@AuthenticationPrincipal CustomUserDetails user,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody AddressRequestDTO request) {
        return ResponseEntity.ok(addressService.update(user.getId(), id, request));
    }
}
