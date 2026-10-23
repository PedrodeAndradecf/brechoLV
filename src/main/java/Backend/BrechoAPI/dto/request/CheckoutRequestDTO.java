package Backend.BrechoAPI.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;



















public record CheckoutRequestDTO(@NotNull UUID addressId) {}
