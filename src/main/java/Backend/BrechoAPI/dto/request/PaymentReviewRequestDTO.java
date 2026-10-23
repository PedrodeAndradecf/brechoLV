package Backend.BrechoAPI.dto.request;

import Backend.BrechoAPI.domain.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;














public record PaymentReviewRequestDTO(@NotNull PaymentStatus outcome) {}
