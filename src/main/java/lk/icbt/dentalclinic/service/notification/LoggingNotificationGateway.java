package lk.icbt.dentalclinic.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development stand-in for a real SMS or email provider: writes the message to the log.
 *
 * <p>Deliberately not a no-op. Logging the message makes the notification path observable in
 * a demonstration and in the M7 test evidence, which a silent implementation would not.
 */
@Component
public class LoggingNotificationGateway implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationGateway.class);

    @Override
    public void sendConfirmation(String contactNumber, String message) {
        log.info("[notification] to {} : {}", contactNumber, message);
    }
}
