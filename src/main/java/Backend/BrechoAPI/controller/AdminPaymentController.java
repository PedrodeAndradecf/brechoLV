package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.dto.request.PaymentReviewRequestDTO;
import Backend.BrechoAPI.dto.response.PendingPaymentResponseDTO;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.PaymentReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;










@RestController
@RequestMapping("/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentReviewService paymentReviewService;

    

    @GetMapping("/pending-review")
    public ResponseEntity<List<PendingPaymentResponseDTO>> pendingReview() {
        return ResponseEntity.ok(paymentReviewService.pendingReview());
    }

    

    @PostMapping("/{id}/review")
    public ResponseEntity<Void> review(@AuthenticationPrincipal CustomUserDetails user,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody PaymentReviewRequestDTO request) {
        paymentReviewService.review(id, user.getId(), request.outcome());
        return ResponseEntity.noContent().build();
    }
}
