package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.billing.Bill;
import lk.icbt.dentalclinic.model.billing.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Data access for bills. */
@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    /**
     * Loads a bill with its line items.
     *
     * <p>The receipt prints every line, and {@code open-in-view} is false, so the lines must
     * be fetched by the query rather than lazily in the template.
     */
    @EntityGraph(attributePaths = {"lineItems"})
    Optional<Bill> findByBillNo(String billNo);

    /** Count issued in a year, backing the {@code BILL-<year>-<0000>} generator. */
    @Query("SELECT COUNT(b) FROM Bill b WHERE b.billNo LIKE CONCAT('BILL-', :year, '-%')")
    long countIssuedInYear(@Param("year") String year);

    boolean existsByBillNo(String billNo);

    List<Bill> findByStatusOrderByIssuedAtDesc(PaymentStatus status);

    /** Takings between two instants, for the daily and monthly revenue reports. */
    @Query("""
            SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b
            WHERE b.status = lk.icbt.dentalclinic.model.billing.PaymentStatus.PAID
              AND b.issuedAt BETWEEN :from AND :to
            """)
    BigDecimal sumPaidBetween(@Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);
}
