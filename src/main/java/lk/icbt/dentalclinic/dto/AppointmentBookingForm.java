package lk.icbt.dentalclinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The booking form — brief requirement 2.
 *
 * <p>The brief lists address and contact number among the fields captured when registering an
 * appointment, and they appear here for exactly that reason. Per assumption A6 they are
 * persisted once on the patient record rather than copied onto every appointment, so
 * submitting the form updates the patient. The user-visible requirement is met as written;
 * only the storage differs.
 *
 * <p>{@code patientId} is present for staff booking on a patient's behalf. It is ignored when
 * a patient books for themselves — the service uses the signed-in user, so no submitted value
 * can book an appointment onto someone else's record.
 */
public class AppointmentBookingForm {

    /** Set by an administrator booking for a patient; ignored for a patient's own booking. */
    private Long patientId;

    @NotNull(message = "Please choose a dentist")
    private Long dentistId;

    @NotNull(message = "Please choose a treatment")
    private Long treatmentId;

    @NotNull(message = "Please choose a date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate appointmentDate;

    @NotNull(message = "Please choose a time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime appointmentTime;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^(?:0|\\+94)[1-9]\\d{8}$",
             message = "Enter a valid Sri Lankan number, for example 0771234567")
    private String contactNumber;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must be 255 characters or fewer")
    private String address;

    @Size(max = 255, message = "Reason for visit must be 255 characters or fewer")
    private String reasonForVisit;

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Long getDentistId() {
        return dentistId;
    }

    public void setDentistId(Long dentistId) {
        this.dentistId = dentistId;
    }

    public Long getTreatmentId() {
        return treatmentId;
    }

    public void setTreatmentId(Long treatmentId) {
        this.treatmentId = treatmentId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getReasonForVisit() {
        return reasonForVisit;
    }

    public void setReasonForVisit(String reasonForVisit) {
        this.reasonForVisit = reasonForVisit;
    }
}
