package lk.icbt.dentalclinic.service.notification;

/**
 * The clinic's outbound messaging channel.
 *
 * <p>An interface with one implementation today, because the Task A use case model places
 * {@code Notification Gateway} outside the system boundary (assumption A12). Naming the
 * boundary means a real SMS provider can be dropped in later without touching the service
 * that publishes the event.
 */
public interface NotificationGateway {

    /**
     * Sends an appointment confirmation.
     *
     * <p>Fire and forget. Implementations must not throw for a delivery failure: an SMS
     * provider being down must never invalidate a confirmed appointment.
     */
    void sendConfirmation(String contactNumber, String message);
}
