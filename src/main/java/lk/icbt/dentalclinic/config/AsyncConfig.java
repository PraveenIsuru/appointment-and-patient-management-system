package lk.icbt.dentalclinic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async}, which the appointment notification listener relies on.
 *
 * <p>Without this the listener would run on the request thread, and a slow SMS gateway would
 * make the patient wait for a confirmation the system has already committed.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
