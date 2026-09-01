package lk.icbt.dentalclinic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Supplies the application's notion of "now".
 *
 * <p>Injecting a {@link Clock} rather than calling {@code LocalDate.now()} inside business
 * code makes time an input like any other. Rules such as "an appointment cannot be in the
 * past" can then be tested at a fixed instant, instead of the suite behaving differently
 * depending on the hour it runs.
 *
 * <p>Fixed to Asia/Colombo: the clinic's day starts and ends in Sri Lanka regardless of where
 * the server is hosted, which matters once M8 deploys this to a cloud host running in UTC.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Colombo");

    @Bean
    public Clock clock() {
        return Clock.system(CLINIC_ZONE);
    }
}
