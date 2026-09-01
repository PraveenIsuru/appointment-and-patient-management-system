package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.exception.InvalidAppointmentException;
import lk.icbt.dentalclinic.model.identity.Dentist;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Clinic booking rules — use case UC13, "Validate Appointment Details".
 *
 * <p>Separate from the controller and from {@code AppointmentService} because these rules are
 * the part most likely to be argued over and changed, and because they can then be tested
 * exhaustively without a database or an HTTP request.
 *
 * <p><strong>Assumption A10.</strong> The brief does not state opening hours, but validation
 * cannot be specified without a boundary. Consulting hours are 08:00 to 20:00 on 30-minute
 * boundaries. The slot granularity is what makes the uniqueness rule of A11 meaningful:
 * without fixed slots, two appointments one minute apart would not collide in the index even
 * though they plainly overlap in reality.
 *
 * <p>A {@link Clock} is injected rather than calling {@code LocalDate.now()} directly, so
 * "is this in the past" can be tested at a fixed instant instead of depending on when the
 * suite happens to run.
 */
@Component
public class AppointmentValidator {

    /** First bookable time of day. */
    public static final LocalTime CLINIC_OPEN = LocalTime.of(8, 0);

    /**
     * Last bookable time of day, exclusive — a slot at 20:00 would run past closing, so the
     * final bookable slot is 19:30.
     */
    public static final LocalTime CLINIC_CLOSE = LocalTime.of(20, 0);

    /** Slot granularity in minutes. */
    public static final int SLOT_MINUTES = 30;

    private final Clock clock;

    public AppointmentValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Checks a proposed date and time against every clinic rule.
     *
     * @throws InvalidAppointmentException on the first rule broken
     */
    public void validate(LocalDate date, LocalTime time) {
        requirePresent(date, time);
        requireOnSlotBoundary(time);
        requireWithinClinicHours(time);
        requireNotInThePast(date, time);
    }

    /**
     * Additionally checks that the chosen dentist is actually consulting then.
     *
     * <p>Kept separate from {@link #validate} so the general rules can be checked before a
     * dentist has been selected — the booking form validates as the user fills it in.
     */
    public void validateForDentist(LocalDate date, LocalTime time, Dentist dentist) {
        validate(date, time);
        if (dentist == null) {
            throw new InvalidAppointmentException("dentistId", "Please choose a dentist");
        }
        if (!dentist.isActive()) {
            throw new InvalidAppointmentException("dentistId",
                    dentist.getFullName() + " is no longer taking appointments");
        }
        if (!dentist.worksAt(time)) {
            throw new InvalidAppointmentException("appointmentTime", String.format(
                    "%s consults between %s and %s. Please choose a time in that session.",
                    dentist.getFullName(), dentist.getSessionStart(), dentist.getSessionEnd()));
        }
    }

    private void requirePresent(LocalDate date, LocalTime time) {
        if (date == null) {
            throw new InvalidAppointmentException("appointmentDate", "Please choose a date");
        }
        if (time == null) {
            throw new InvalidAppointmentException("appointmentTime", "Please choose a time");
        }
    }

    private void requireOnSlotBoundary(LocalTime time) {
        if (time.getMinute() % SLOT_MINUTES != 0 || time.getSecond() != 0) {
            throw new InvalidAppointmentException("appointmentTime",
                    "Appointments start on the hour or the half hour");
        }
    }

    private void requireWithinClinicHours(LocalTime time) {
        if (time.isBefore(CLINIC_OPEN) || !time.isBefore(CLINIC_CLOSE)) {
            throw new InvalidAppointmentException("appointmentTime", String.format(
                    "The clinic is open from %s to %s. The last appointment starts at %s.",
                    CLINIC_OPEN, CLINIC_CLOSE, CLINIC_CLOSE.minusMinutes(SLOT_MINUTES)));
        }
    }

    private void requireNotInThePast(LocalDate date, LocalTime time) {
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock))) {
            throw new InvalidAppointmentException("appointmentDate",
                    "Appointments cannot be made in the past");
        }
    }

    /** Every slot the clinic offers in a day, in order. */
    public List<LocalTime> allSlots() {
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime t = CLINIC_OPEN; t.isBefore(CLINIC_CLOSE); t = t.plusMinutes(SLOT_MINUTES)) {
            slots.add(t);
        }
        return slots;
    }

    /** Whether a time is one the clinic offers at all, without throwing. */
    public boolean isBookableTime(LocalTime time) {
        return time != null
                && time.getSecond() == 0
                && time.getMinute() % SLOT_MINUTES == 0
                && !time.isBefore(CLINIC_OPEN)
                && time.isBefore(CLINIC_CLOSE);
    }
}
