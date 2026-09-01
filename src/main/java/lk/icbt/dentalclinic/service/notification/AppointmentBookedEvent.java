package lk.icbt.dentalclinic.service.notification;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Published when an appointment is committed.
 *
 * <p>A record of plain values, not the {@code Appointment} entity. The listener runs after
 * the transaction commits and possibly on another thread, where a detached entity could no
 * longer load its lazy associations. Copying what the listener needs avoids that entirely.
 *
 * @param appointmentNo  the clinic's reference for the visit
 * @param patientName    who the confirmation is for
 * @param contactNumber  where to send it; null when the patient has none on record
 * @param dentistName    who they are seeing
 * @param date           day of the visit
 * @param time           start of the slot
 */
public record AppointmentBookedEvent(String appointmentNo,
                                     String patientName,
                                     String contactNumber,
                                     String dentistName,
                                     LocalDate date,
                                     LocalTime time) {
}
