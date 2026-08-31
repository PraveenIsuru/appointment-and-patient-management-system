package lk.icbt.dentalclinic.model.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * An immutable record of a change to a domain record.
 *
 * <p><strong>Assumption A9.</strong> This entity deliberately holds no foreign keys and no
 * association to {@code User}. An audit record must survive deletion of the thing it
 * describes; a foreign key to {@code Appointment} would either block that deletion or
 * cascade the evidence away. The actor is therefore stored as a username string.
 *
 * <p>Rows are written by the database trigger {@code trg_appointment_audit_*}, not by this
 * application, so that changes made outside it — a direct SQL correction, for example — are
 * captured too. That is exactly when an audit trail matters most. The entity exists so the
 * log can be <em>read</em>; nothing in the application writes to it.
 *
 * <p>There are deliberately no setters: an audit row that can be edited is not evidence.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_name", nullable = false, length = 50)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "performed_by", nullable = false, length = 50)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name = "old_value", length = 1000)
    private String oldValue;

    @Column(name = "new_value", length = 1000)
    private String newValue;

    protected AuditLog() {
        // required by JPA; instances originate from the database trigger
    }

    public Long getId() {
        return id;
    }

    public String getEntityName() {
        return entityName;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    @Override
    public String toString() {
        return "AuditLog{" + entityName + "#" + entityId + " " + action
                + " by " + performedBy + " at " + performedAt + "}";
    }
}
