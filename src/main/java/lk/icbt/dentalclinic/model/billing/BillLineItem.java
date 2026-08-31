package lk.icbt.dentalclinic.model.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One printed line on a receipt.
 *
 * <p>Assumption A7: the brief requires a printed receipt, and a receipt showing only a
 * single total is not a receipt. Discounts and tax are lines in their own right rather than
 * hidden adjustments, so the arithmetic on the paper is checkable by the patient holding it.
 */
@Entity
@Table(name = "bill_line_items")
public class BillLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private LineCategory category;

    /** Negative for a DISCOUNT line, so the total is a plain sum of every line. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    protected BillLineItem() {
        // required by JPA
    }

    public BillLineItem(String description, LineCategory category,
                        BigDecimal unitPrice, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        this.description = description;
        this.category = category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public LineCategory getCategory() {
        return category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public String toString() {
        return "BillLineItem{" + description + " x" + quantity + " = " + getLineTotal() + "}";
    }
}
