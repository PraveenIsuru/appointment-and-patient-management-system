package lk.icbt.dentalclinic.model.scheduling;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state of an appointment, together with the rules governing movement between
 * states.
 *
 * <p>The permitted transitions live on the enumeration rather than in a service method so
 * that there is exactly one definition of them. A service can then ask the question rather
 * than re-implement the answer, and {@code Appointment.assertTransitionAllowed} enforces it
 * on every state change.
 */
public enum AppointmentStatus {

    /** Booked but not yet confirmed by the clinic. */
    SCHEDULED,

    /** Confirmed with the patient. */
    CONFIRMED,

    /** Treatment carried out. Billable, and terminal. */
    COMPLETED,

    /** Called off by patient or clinic. Terminal. */
    CANCELLED,

    /** Patient did not attend. Terminal. */
    NO_SHOW;

    private static final Set<AppointmentStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(COMPLETED, CANCELLED, NO_SHOW));

    /**
     * Whether this status may legally become {@code next}.
     *
     * <p>{@code SCHEDULED -> COMPLETED} is permitted deliberately: a walk-in patient may be
     * treated without the clinic ever having confirmed the booking separately.
     *
     * @param next the proposed target status
     * @return true when the transition is allowed
     */
    public boolean canTransitionTo(AppointmentStatus next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            case SCHEDULED -> next == CONFIRMED || next == COMPLETED
                              || next == CANCELLED || next == NO_SHOW;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED || next == NO_SHOW;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }

    /** Whether no further transition is possible from this status. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
