package lk.icbt.dentalclinic.repository;

import lk.icbt.dentalclinic.model.identity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Data access for patients. */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPatientNo(String patientNo);

    /**
     * Highest patient number issued so far, backing the {@code PAT-000000} generator.
     *
     * <p>Uses MAX rather than COUNT so that a deleted record cannot cause the next number to
     * collide with one already in use.
     */
    @Query("SELECT MAX(p.patientNo) FROM Patient p")
    Optional<String> findHighestPatientNo();

    boolean existsByPatientNo(String patientNo);

    Optional<Patient> findByUsername(String username);

    /** Case-insensitive name search for the admin patient list. */
    List<Patient> findByFullNameContainingIgnoreCase(String fragment);

    /**
     * Patients with at least one unsettled bill.
     *
     * <p>The set-based equivalent of calling {@code Patient.hasOutstandingBills()} in a
     * loop, which would issue a query per patient.
     */
    @Query("""
            SELECT DISTINCT a.patient FROM Appointment a
            WHERE a.bill.status = lk.icbt.dentalclinic.model.billing.PaymentStatus.UNPAID
            """)
    List<Patient> findWithOutstandingBills();
}
