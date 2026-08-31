package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for appointments.
 *
 * <p>An instance of the Repository pattern: the interface states <em>what</em> the
 * application needs, and Spring Data supplies the implementation, so no query construction
 * leaks into the service tier.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Search by appointment number — requirement 3 of the brief. */
    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    boolean existsByAppointmentNo(String appointmentNo);

    /**
     * Availability check backing the friendly "that slot is taken" message.
     *
     * <p>Cancelled appointments are excluded, so a cancelled slot reads as free — matching
     * both {@code Appointment.clashesWith} and the {@code slot_lock} behaviour of the
     * unique index. This check is advisory only; the index is what actually prevents the
     * clash under a concurrent booking.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.dentist.id = :dentistId
              AND a.appointmentDate = :date
              AND a.appointmentTime = :time
              AND a.status <> lk.icbt.dentalclinic.model.scheduling.AppointmentStatus.CANCELLED
            """)
    boolean isSlotTaken(@Param("dentistId") Long dentistId,
                        @Param("date") LocalDate date,
                        @Param("time") LocalTime time);

    List<Appointment> findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(Long patientId);

    /** A dentist's day, for the dentist dashboard. */
    List<Appointment> findByDentistIdAndAppointmentDateOrderByAppointmentTimeAsc(
            Long dentistId, LocalDate date);

    List<Appointment> findByAppointmentDateOrderByAppointmentTimeAsc(LocalDate date);

    List<Appointment> findByStatus(AppointmentStatus status);

    /**
     * Highest sequence number issued in a year, used to generate the next
     * {@code APT-<year>-<0000>} in M4.
     */
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.appointmentNo LIKE CONCAT('APT-', :year, '-%')
            """)
    long countIssuedInYear(@Param("year") String year);

    /**
     * Portable equivalent of the {@code sp_daily_revenue_report} stored procedure.
     *
     * <p>Exists so the reporting logic stays covered by the H2 test suite, where the
     * procedure cannot be created. The procedure remains the version used on MySQL.
     *
     * @return rows of {@code [treatmentType, appointmentCount, grossRevenue]}
     */
    @Query("""
            SELECT a.treatment.type, COUNT(a), COALESCE(SUM(a.bill.totalAmount), 0)
            FROM Appointment a
            WHERE a.appointmentDate = :date
              AND a.status <> lk.icbt.dentalclinic.model.scheduling.AppointmentStatus.CANCELLED
            GROUP BY a.treatment.type
            ORDER BY COALESCE(SUM(a.bill.totalAmount), 0) DESC
            """)
    List<Object[]> summariseRevenueForDate(@Param("date") LocalDate date);
}
