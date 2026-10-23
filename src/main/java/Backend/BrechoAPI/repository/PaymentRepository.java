package Backend.BrechoAPI.repository;

import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    






















    @Query("select p from Payment p where p.order.id = :orderId"
            + " and p.status <> Backend.BrechoAPI.domain.enums.PaymentStatus.NOT_CREATED")
    Optional<Payment> findLiveByOrderId(@Param("orderId") UUID orderId);

    


    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    












    


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.OPEN,"
            + " p.txid = :txid, p.pixCopyPaste = :copyPaste, p.updatedAt = :now"
            + " where p.id = :id"
            + " and p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.PENDING_CONFIRMATION")
    int openWithCharge(@Param("id") UUID id, @Param("txid") String txid,
                       @Param("copyPaste") String copyPaste, @Param("now") Instant now);

    




    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.NOT_CREATED,"
            + " p.updatedAt = :now"
            + " where p.id = :id"
            + " and p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.PENDING_CONFIRMATION")
    int markNotCreated(@Param("id") UUID id, @Param("now") Instant now);

    





    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.NOT_CREATED,"
            + " p.reviewedBy = :reviewer, p.reviewedAt = :now,"
            + " p.reviewOutcome = Backend.BrechoAPI.domain.enums.PaymentStatus.NOT_CREATED,"
            + " p.updatedAt = :now"
            + " where p.id = :id"
            + " and p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.PENDING_CONFIRMATION"
            + " and p.createdAt < :createdBefore")
    int markNotCreatedByReview(@Param("id") UUID id, @Param("reviewer") UUID reviewer,
                               @Param("now") Instant now,
                               @Param("createdBefore") Instant createdBefore);

    



    @Query("select p from Payment p join fetch p.order"
            + " where p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.PENDING_CONFIRMATION"
            + " and p.createdAt < :createdBefore"
            + " order by p.createdAt asc, p.id asc")
    List<Payment> findPendingCreatedBefore(@Param("createdBefore") Instant createdBefore);
}
