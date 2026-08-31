package lk.icbt.dentalclinic.model.billing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A patient's bill for one appointment.
 *
 * <p>Money is {@link BigDecimal} throughout and never {@code double}: binary floating point
 * cannot represent 0.1 exactly, so a total assembled from doubles drifts by cents and a
 * receipt stops reconciling with the day's takings.
 *
 * <p>Totals are recalculated from the line items rather than trusted from the caller, so a
 * bill cannot be persisted claiming a figure its own lines do not support.
 */
@Entity
@Table(name = "bills")
public class Bill {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true, length = 20)
    private String billNo;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @Column(name = "treatment_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal treatmentCost = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "issued_by", nullable = false, length = 50)
    private String issuedBy;

    /**
     * Composition: {@code Bill "1" *-- "1..*" BillLineItem}. A line item cannot exist
     * outside its bill, so the cascade is total and orphans are removed — the object graph
     * matches the {@code ON DELETE CASCADE} in the migration.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private List<BillLineItem> lineItems = new ArrayList<>();

    protected Bill() {
        // required by JPA
    }

    public Bill(String billNo, BigDecimal consultationFee, String issuedBy) {
        this.billNo = billNo;
        this.consultationFee = scaled(consultationFee);
        this.issuedBy = issuedBy;
    }

    // ---------------------------------------------------------------- behaviour ---

    /** Adds a line and recalculates the total, so the two can never disagree. */
    public void addLine(BillLineItem line) {
        lineItems.add(line);
        recalculateFromLines();
    }

    /**
     * Applies a percentage discount to the pre-discount subtotal.
     *
     * @param percentage 0–100
     */
    public void applyDiscount(BigDecimal percentage) {
        if (percentage == null
                || percentage.compareTo(BigDecimal.ZERO) < 0
                || percentage.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Discount percentage must be between 0 and 100");
        }
        BigDecimal subtotal = consultationFee.add(treatmentCost);
        this.discountAmount = scaled(subtotal.multiply(percentage)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
        this.totalAmount = calculateTotal();
    }

    /**
     * Consultation fee plus treatment cost, less discount, plus tax.
     *
     * <p>Mirrors {@code fn_calculate_bill_total} in the database exactly. Both exist because
     * a report written in SQL and a receipt rendered by the application must not be able to
     * disagree about what a bill is worth.
     */
    public BigDecimal calculateTotal() {
        return scaled(consultationFee
                .add(treatmentCost)
                .subtract(discountAmount)
                .add(taxAmount));
    }

    public void markPaid(PaymentMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("A payment method is required");
        }
        if (status == PaymentStatus.PAID) {
            throw new IllegalStateException("Bill " + billNo + " is already paid");
        }
        if (status == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("A refunded bill cannot be paid again");
        }
        this.status = PaymentStatus.PAID;
        this.paymentMethod = method;
    }

    public void refund() {
        if (status != PaymentStatus.PAID) {
            throw new IllegalStateException("Only a paid bill can be refunded");
        }
        this.status = PaymentStatus.REFUNDED;
    }

    /** Rebuilds the money columns from the line items. */
    private void recalculateFromLines() {
        this.consultationFee = sumOf(LineCategory.CONSULTATION);
        this.treatmentCost = sumOf(LineCategory.TREATMENT);
        this.discountAmount = sumOf(LineCategory.DISCOUNT);
        this.taxAmount = sumOf(LineCategory.TAX);
        this.totalAmount = calculateTotal();
    }

    private BigDecimal sumOf(LineCategory category) {
        return scaled(lineItems.stream()
                .filter(line -> line.getCategory() == category)
                .map(BillLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ accessors ---

    public Long getId() {
        return id;
    }

    public String getBillNo() {
        return billNo;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public BigDecimal getTreatmentCost() {
        return treatmentCost;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public List<BillLineItem> getLineItems() {
        return List.copyOf(lineItems);
    }

    @Override
    public String toString() {
        return "Bill{" + billNo + " " + totalAmount + " " + status + "}";
    }
}
