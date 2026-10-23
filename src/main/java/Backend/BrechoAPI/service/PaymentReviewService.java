package Backend.BrechoAPI.service;

import Backend.BrechoAPI.config.PaymentReviewProperties;
import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.domain.enums.PaymentStatus;
import Backend.BrechoAPI.dto.response.PendingPaymentResponseDTO;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


























@Service
@RequiredArgsConstructor
public class PaymentReviewService {

    private final PaymentRepository paymentRepository;
    private final PaymentReviewProperties reviewProperties;
    private final Clock clock;

    










    @Transactional(readOnly = true)
    public List<PendingPaymentResponseDTO> pendingReview() {
        Instant createdBefore = clock.instant().minus(reviewProperties.pendingAfter());
        return paymentRepository.findPendingCreatedBefore(createdBefore).stream()
                .map(PaymentReviewService::itemDe)
                .toList();
    }

    




















    @Transactional
    public void review(UUID paymentId, UUID reviewerId, PaymentStatus outcome) {
        Objects.requireNonNull(paymentId, "paymentId é obrigatório");
        Objects.requireNonNull(reviewerId, "reviewerId é obrigatório");
        Objects.requireNonNull(outcome, "outcome é obrigatório");

        if (outcome != PaymentStatus.NOT_CREATED) {
            throw new DomainRuleException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "a decisão sobre uma cobrança pendente só aceita NOT_CREATED por enquanto: "
                            + outcome + " exigiria um artefato a gravar, e não há de onde"
                            + " conferi-lo antes do adaptador real");
        }

        Instant now = clock.instant();
        int linhas = paymentRepository.markNotCreatedByReview(paymentId, reviewerId, now,
                now.minus(reviewProperties.pendingAfter()));
        if (linhas != 1) {
            throw new DomainRuleException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "esta cobrança não está pendente de confirmação há mais de "
                            + reviewProperties.pendingAfter().toMinutes() + " minutos: ou já foi"
                            + " decidida, ou já tem código Pix, ou ainda pode estar em voo;"
                            + " releia a fila");
        }
    }

    private static PendingPaymentResponseDTO itemDe(Payment payment) {
        return new PendingPaymentResponseDTO(payment.getId(), payment.getOrder().getId(),
                payment.getAmount(), payment.getOrderNsu(), payment.getCreatedAt(),
                payment.getOrder().getShipping().getPhone());
    }
}
