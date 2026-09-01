package lk.icbt.dentalclinic.model.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lk.icbt.dentalclinic.model.scheduling.Appointment;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A practising dentist.
 *
 * <p>The brief requires a dentist name on every appointment, and preventing double booking
 * requires identity for the dentist — so this class has to exist whatever else is decided.
 * Giving it a login (assumption A2) is therefore one extra role mapping rather than a new
 * concept.
 */
@Entity
@Table(name = "dentists")
@PrimaryKeyJoinColumn(name = "id")
public class Dentist extends User {

    @Column(name = "dentist_no", nullable = false, unique = true, length = 20)
    private String dentistNo;

    @Column(name = "specialisation", nullable = false, length = 100)
    private String specialisation;

    @Column(name = "licence_number", nullable = false, unique = true, length = 50)
    private String licenceNumber;

    /**
     * Held on the dentist, not the treatment: the brief bills "treatment type <em>and</em>
     * consultation fee", and a senior dentist charges more to see the same problem.
     */
    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "session_start", nullable = false)
    private LocalTime sessionStart;

    @Column(name = "session_end", nullable = false)
    private LocalTime sessionEnd;

    /**
     * Aggregation: {@code Dentist "1" o-- "0..*" Appointment}. Unlike the patient
     * association this is <em>not</em> a composition — appointments outlive a dentist who
     * leaves the practice, so there is no cascade and no orphan removal here.
     */
    @OneToMany(mappedBy = "dentist", fetch = FetchType.LAZY)
    private List<Appointment> appointments = new ArrayList<>();

    protected Dentist() {
        // required by JPA
    }

    public Dentist(String username, String passwordHash, String email, String fullName,
                   String contactNumber, String dentistNo, String specialisation,
                   String licenceNumber, BigDecimal consultationFee,
                   LocalTime sessionStart, LocalTime sessionEnd) {
        super(username, passwordHash, email, fullName, contactNumber);
        this.dentistNo = dentistNo;
        this.specialisation = specialisation;
        this.licenceNumber = licenceNumber;
        this.consultationFee = consultationFee;
        this.sessionStart = sessionStart;
        this.sessionEnd = sessionEnd;
    }

    // ---------------------------------------------------------------- behaviour ---

    /**
     * Whether this dentist is on duty at the given time.
     *
     * <p>The end of the session is exclusive: a dentist finishing at 14:00 is not available
     * for a 14:00 appointment, because that slot runs past the end of their session.
     */
    public boolean worksAt(LocalTime time) {
        return time != null && !time.isBefore(sessionStart) && time.isBefore(sessionEnd);
    }

    @Override
    public String getDashboardUrl() {
        // /dentist/schedule, not /dentist/dashboard: the login sequence diagram names this
        // as the DENTIST redirect target, and a dentist's landing page is their day's list.
        return "/dentist/schedule";
    }

    @Override
    public String getDisplayRole() {
        return "Dentist";
    }

    // ------------------------------------------------------------------ accessors ---

    public String getDentistNo() {
        return dentistNo;
    }

    public String getSpecialisation() {
        return specialisation;
    }

    public void setSpecialisation(String specialisation) {
        this.specialisation = specialisation;
    }

    public String getLicenceNumber() {
        return licenceNumber;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        this.consultationFee = consultationFee;
    }

    public LocalTime getSessionStart() {
        return sessionStart;
    }

    public LocalTime getSessionEnd() {
        return sessionEnd;
    }

    public void setSession(LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Session start must be before session end");
        }
        this.sessionStart = start;
        this.sessionEnd = end;
    }

    public List<Appointment> getAppointments() {
        return List.copyOf(appointments);
    }
}
