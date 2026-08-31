package lk.icbt.dentalclinic.model.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The appointment status transition table.
 *
 * <p>These rules decide whether a completed appointment can be quietly reopened, so they are
 * tested exhaustively rather than by example.
 */
class AppointmentStatusTest {

    @Nested
    @DisplayName("from SCHEDULED")
    class FromScheduled {

        @Test
        @DisplayName("may be confirmed, completed, cancelled or marked no-show")
        void allowsForwardTransitions() {
            assertTrue(AppointmentStatus.SCHEDULED.canTransitionTo(AppointmentStatus.CONFIRMED));
            assertTrue(AppointmentStatus.SCHEDULED.canTransitionTo(AppointmentStatus.CANCELLED));
            assertTrue(AppointmentStatus.SCHEDULED.canTransitionTo(AppointmentStatus.NO_SHOW));
        }

        @Test
        @DisplayName("may go straight to COMPLETED, because a walk-in is never confirmed first")
        void allowsDirectCompletion() {
            assertTrue(AppointmentStatus.SCHEDULED.canTransitionTo(AppointmentStatus.COMPLETED));
        }
    }

    @Nested
    @DisplayName("from CONFIRMED")
    class FromConfirmed {

        @Test
        @DisplayName("may be completed, cancelled or marked no-show")
        void allowsForwardTransitions() {
            assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.COMPLETED));
            assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.CANCELLED));
            assertTrue(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.NO_SHOW));
        }

        @Test
        @DisplayName("cannot go back to SCHEDULED")
        void rejectsBackwardTransition() {
            assertFalse(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.SCHEDULED));
        }
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    @DisplayName("terminal statuses permit no further transition at all")
    void terminalStatusesAreFinal(AppointmentStatus terminal) {
        assertTrue(terminal.isTerminal());
        for (AppointmentStatus target : AppointmentStatus.values()) {
            assertFalse(terminal.canTransitionTo(target),
                    terminal + " must not be able to become " + target);
        }
    }

    @ParameterizedTest
    @EnumSource(AppointmentStatus.class)
    @DisplayName("a status is never a transition to itself, and never to null")
    void rejectsSelfAndNull(AppointmentStatus status) {
        assertFalse(status.canTransitionTo(status));
        assertFalse(status.canTransitionTo(null));
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CONFIRMED"})
    @DisplayName("live statuses are not terminal")
    void liveStatusesAreNotTerminal(AppointmentStatus status) {
        assertFalse(status.isTerminal());
    }
}
