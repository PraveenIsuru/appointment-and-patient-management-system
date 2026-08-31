package lk.icbt.dentalclinic.model.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A treatment the clinic offers, priced from a published list.
 *
 * <p>{@code baseCost} is the list price. It is not the amount finally charged: the
 * {@code TreatmentPricingStrategy} introduced in M5 resolves the actual figure from
 * {@link TreatmentType}, which is why pricing rules do not live on this class.
 */
@Entity
@Table(name = "treatments")
public class Treatment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TreatmentType type;

    @Column(name = "base_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseCost;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Column(name = "sessions_required", nullable = false)
    private int sessionsRequired = 1;

    /**
     * Withdrawn treatments are deactivated rather than deleted, so historical appointments
     * keep a valid foreign key and old bills stay explicable.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Treatment() {
        // required by JPA
    }

    public Treatment(String code, String name, TreatmentType type, BigDecimal baseCost,
                     int estimatedMinutes, int sessionsRequired) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.baseCost = baseCost;
        this.estimatedMinutes = estimatedMinutes;
        this.sessionsRequired = sessionsRequired;
    }

    public boolean isMultiSession() {
        return sessionsRequired > 1;
    }

    public void deactivate() {
        this.active = false;
    }

    // ------------------------------------------------------------------ accessors ---

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public TreatmentType getType() {
        return type;
    }

    public BigDecimal getBaseCost() {
        return baseCost;
    }

    public void setBaseCost(BigDecimal baseCost) {
        this.baseCost = baseCost;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public int getSessionsRequired() {
        return sessionsRequired;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Treatment treatment)) {
            return false;
        }
        return code != null && code.equals(treatment.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }

    @Override
    public String toString() {
        return "Treatment{" + code + " " + name + "}";
    }
}
