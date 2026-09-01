package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.AppointmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
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
 *
 * <p><strong>Why the entity graphs.</strong> {@code spring.jpa.open-in-view} is false, so the
 * persistence context closes before a view renders, and a lazy association touched in a
 * template throws {@code LazyInitializationException}. Every query whose result is displayed
 * therefore fetches patient, dentist and treatment up front. That is a correctness
 * requirement here rather than a performance tweak — though it also removes the N+1 select a
 * list view would otherwise issue.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Search by appointment number — requirement 3 of the brief. */
    @EntityGraph(attributePaths = {"patient", "dentist", "treatment", "bill"})
    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    boolean existsByAppointmentNo(String appointmentNo);

    /**
     * Availability check backing the friendly "that slot is taken" message.
     *
     * <p>Cancelled appointments are excluded, so a cancelled slot reads as free — matching
     * both {@code Appointment.clashesWith} and the {@code slot_lock} behaviour of the unique
     * index. This check is advisory only; the index is what actually prevents the clash
     * under a concurrent booking.
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

    /** A patient's own history, newest first. */
    @EntityGraph(attributePaths = {"patient", "dentist", "treatment", "bill"})
    List<Appointment> findByPatientIdOrderByAppointmentDateDescAppointmentTimeDesc(Long patientId);

    /** A dentist's day, for the dentist dashboard. */
    @EntityGraph(attributePaths = {"patient", "dentist", "treatment", "bill"})
    List<Appointment> findByDentistIdAndAppointmentDateOrderByAppointmentTimeAsc(
            Long dentistId, LocalDate date);

    /** The whole clinic's day, for the administrator's dashboard. */
    @EntityGraph(attributePaths = {"patient", "dentist", "treatment", "bill"})
    List<Appointment> findByAppointmentDateOrderByAppointmentTimeAsc(LocalDate date);

    /** Every appointment, with its associations, for the administrator's list. */
    @EntityGraph(attributePaths = {"patient", "dentist", "treatment", "bill"})
    @Query("SELECT a FROM Appointment a")
    List<Appointment> findAllWithDetails();

    List<Appointment> findByStatus(AppointmentStatus status);

    /** Count issued in a year, backing the {@code APT-<year>-<0000>} generator. */
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
