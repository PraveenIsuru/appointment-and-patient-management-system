package lk.icbt.dentalclinic.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * Sends the confirmation for a booked appointment — use case UC14, the {@code <<extend>>} of
 * Book Appointment.
 *
 * <p><strong>Observer pattern.</strong> {@code AppointmentService} publishes an event and
 * knows nothing about who consumes it. Adding an email confirmation later means adding a
 * listener, not editing the booking logic.
 *
 * <p>Two annotations carry the whole of assumption A12, and both matter:
 * <ul>
 *   <li>{@code AFTER_COMMIT} — the message is sent only once the appointment is durably
 *       saved. Sending inside the transaction risks telling a patient about a booking that
 *       is then rolled back.</li>
 *   <li>{@code @Async} — delivery happens off the request thread, so a slow or failing
 *       gateway cannot make the patient wait, and cannot fail their booking.</li>
 * </ul>
 *
 * <p>The catch-all is deliberate for the same reason: an exception escaping here must not
 * surface anywhere near the booking that has already succeeded.
 */
@Component
public class AppointmentNotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(AppointmentNotificationListener.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationGateway gateway;

    public AppointmentNotificationListener(NotificationGateway gateway) {
        this.gateway = gateway;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        if (event.contactNumber() == null || event.contactNumber().isBlank()) {
            // The use case is an <<extend>>, conditional on a contact number being held.
            // No number is not an error; there is simply nothing to send.
            return;
        }
        try {
            gateway.sendConfirmation(event.contactNumber(), String.format(
                    "Sunrise Dental Clinic: appointment %s confirmed for %s with %s on %s at %s.",
                    event.appointmentNo(),
                    event.patientName(),
                    event.dentistName(),
                    event.date().format(DATE),
                    event.time().format(TIME)));
        } catch (RuntimeException e) {
            log.warn("Could not send confirmation for {}: {}",
                    event.appointmentNo(), e.getMessage());
        }
    }
}
