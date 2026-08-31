package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read access to the audit trail.
 *
 * <p>Rows are written by database triggers, never by this application, so although
 * {@code JpaRepository} exposes save and delete they are not used. The trail is read-only
 * by intent — see assumption A9.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityNameAndEntityIdOrderByPerformedAtDesc(String entityName, Long entityId);

    List<AuditLog> findByPerformedByOrderByPerformedAtDesc(String performedBy);
}
