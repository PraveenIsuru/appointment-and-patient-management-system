package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.repository.AppointmentRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Issues the next {@code APT-<year>-<0000>} number — use case UC12.
 *
 * <p>The sequence restarts each calendar year, which is what the format in the class diagram
 * implies and what makes the number readable to staff: the year is visible at a glance.
 *
 * <p>Same concurrency caveat as {@code PatientNumberGenerator}: two simultaneous bookings can
 * read the same count and propose the same number. The unique index on
 * {@code appointment_no} turns that into a rejected insert rather than duplicate data.
 */
@Component
public class AppointmentNumberGenerator {

    private static final String PREFIX = "APT-";
    private static final int DIGITS = 4;

    private final AppointmentRepository appointments;
    private final Clock clock;

    public AppointmentNumberGenerator(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    public String next() {
        String year = String.valueOf(Year.now(clock).getValue());
        long issued = appointments.countIssuedInYear(year);
        return PREFIX + year + "-" + String.format("%0" + DIGITS + "d", issued + 1);
    }
}
