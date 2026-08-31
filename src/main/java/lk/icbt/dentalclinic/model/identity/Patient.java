package lk.icbt.dentalclinic.model.identity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lk.icbt.dentalclinic.model.billing.PaymentStatus;
import lk.icbt.dentalclinic.model.scheduling.Appointment;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * A registered patient of the clinic.
 *
 * <p><strong>Assumption A6.</strong> Address and contact number are held here once, not
 * copied onto every appointment. The brief lists them among the fields captured when
 * booking, and the booking form still shows and edits them — only the storage differs.
 * Copying them per appointment would leave a patient who moves with a history that
 * disagrees with itself and no single value to correct.
 */
@Entity
@Table(name = "patients")
@PrimaryKeyJoinColumn(name = "id")
public class Patient extends User {

    @Column(name = "patient_no", nullable = false, unique = true, length = 20)
    private String patientNo;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "registered_on", nullable = false)
    private LocalDate registeredOn = LocalDate.now();

    @Column(name = "allergies", length = 255)
    private String allergies;

    /**
     * Composition: {@code Patient "1" *-- "0..*" Appointment}. An appointment has no
     * meaning without the patient who booked it, so the collection is owned here and
     * removal is an orphan removal.
     */
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("appointmentDate DESC, appointmentTime DESC")
    private List<Appointment> appointments = new ArrayList<>();

    protected Patient() {
        // required by JPA
    }

    public Patient(String username, String passwordHash, String email, String fullName,
                   String contactNumber, String patientNo, String address,
                   LocalDate dateOfBirth) {
        super(username, passwordHash, email, fullName, contactNumber);
        this.patientNo = patientNo;
        this.address = address;
        this.dateOfBirth = dateOfBirth;
    }

    // ---------------------------------------------------------------- behaviour ---

    /** Age in completed years, derived rather than stored so it cannot go stale. */
    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /**
     * Whether this patient has any bill still awaiting settlement.
     *
     * <p>Walks appointments to their bills, following the navigability of the class
     * diagram. This is a lazy traversal, so callers that need it across many patients at
     * once should use the aggregate query on the repository rather than looping over
     * entities.
     */
    public boolean hasOutstandingBills() {
        return appointments.stream()
                .map(Appointment::getBill)
                .filter(java.util.Objects::nonNull)
                .anyMatch(bill -> bill.getStatus() == PaymentStatus.UNPAID);
    }

    /** Keeps both ends of the association consistent. */
    public void addAppointment(Appointment appointment) {
        appointments.add(appointment);
        appointment.setPatient(this);
    }

    @Override
    public String getDashboardUrl() {
        return "/patient/dashboard";
    }

    @Override
    public String getDisplayRole() {
        return "Patient";
    }

    // ------------------------------------------------------------------ accessors ---

    public String getPatientNo() {
        return patientNo;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public LocalDate getRegisteredOn() {
        return registeredOn;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public List<Appointment> getAppointments() {
        return List.copyOf(appointments);
    }
}
