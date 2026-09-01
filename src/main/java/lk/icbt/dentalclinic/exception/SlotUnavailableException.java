package lk.icbt.dentalclinic.exception;

import java.time.LocalTime;
import java.util.List;

/**
 * The clinic's original problem, caught before anything is written: the requested dentist is
 * already booked at that time.
 *
 * <p>Carries the nearest free slots, so the message can offer a way forward rather than only
 * refusing. A receptionist told "10:30 is taken, try 11:00 or 11:30" can finish the booking
 * in one call; one told only "unavailable" cannot.
 */
public class SlotUnavailableException extends RuntimeException {

    private final transient List<LocalTime> alternatives;

    public SlotUnavailableException(String message, List<LocalTime> alternatives) {
        super(message);
        this.alternatives = List.copyOf(alternatives);
    }

    public List<LocalTime> getAlternatives() {
        return alternatives;
    }

    public boolean hasAlternatives() {
        return !alternatives.isEmpty();
    }
}
