package lk.icbt.dentalclinic.security;

import lk.icbt.dentalclinic.model.identity.RoleType;
import lk.icbt.dentalclinic.model.identity.User;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Row-level access rules for appointments — <strong>assumption A8</strong>.
 *
 * <p>Role-based URL rules protect <em>pages</em>. They do not stop a signed-in patient from
 * changing the appointment number in the address bar and reading someone else's clinical
 * history: an insecure direct object reference, and a data-protection failure in a health
 * context. This class is what makes access depend on the row as well as the role.
 *
 * <p><strong>Why a policy object rather than SpEL in {@code @PreAuthorize}.</strong> The rule
 * "this appointment belongs to this user" can be written as an expression string, but an
 * expression cannot be unit tested on its own, gets no compiler checking, and fails at run
 * time when a property is renamed. A small bean is testable in isolation and refactors
 * safely — and for a rule this important, being able to test every combination directly is
 * worth more than the brevity of an annotation.
 */
@Component("appointmentAccess")
public class AppointmentAccessPolicy {

    /**
     * Whether {@code user} may see {@code appointment}.
     *
     * <ul>
     *   <li>An administrator sees everything.</li>
     *   <li>A dentist sees only appointments assigned to them.</li>
     *   <li>A patient sees only their own.</li>
     * </ul>
     *
     * <p>Anything else is refused. The default is deny, so a future role added without
     * thinking about this method gets no access rather than full access.
     */
    public boolean canView(User user, Appointment appointment) {
        if (user == null || appointment == null) {
            return false;
        }
        if (user.hasRole(RoleType.ADMIN)) {
            return true;
        }
        if (user.hasRole(RoleType.DENTIST)) {
            return isAssignedDentist(user, appointment);
        }
        if (user.hasRole(RoleType.PATIENT)) {
            return isOwningPatient(user, appointment);
        }
        return false;
    }

    /**
     * Whether {@code user} may change {@code appointment} — reschedule or cancel.
     *
     * <p>Deliberately narrower than {@link #canView}: a dentist may read an appointment
     * assigned to them but not move or cancel it. Rearranging the diary is the clinic's job,
     * and a dentist silently cancelling a visit the front desk still expects is precisely the
     * kind of confusion the paper system produced.
     */
    public boolean canModify(User user, Appointment appointment) {
        if (user == null || appointment == null) {
            return false;
        }
        if (user.hasRole(RoleType.ADMIN)) {
            return true;
        }
        if (user.hasRole(RoleType.PATIENT)) {
            return isOwningPatient(user, appointment);
        }
        return false;
    }

    /** Whether {@code user} may record clinical notes and complete the visit. */
    public boolean canRecordTreatment(User user, Appointment appointment) {
        if (user == null || appointment == null) {
            return false;
        }
        return user.hasRole(RoleType.ADMIN) || isAssignedDentist(user, appointment);
    }

    private boolean isOwningPatient(User user, Appointment appointment) {
        return appointment.getPatient() != null
                && Objects.equals(appointment.getPatient().getId(), user.getId());
    }

    private boolean isAssignedDentist(User user, Appointment appointment) {
        return appointment.getDentist() != null
                && Objects.equals(appointment.getDentist().getId(), user.getId());
    }
}
