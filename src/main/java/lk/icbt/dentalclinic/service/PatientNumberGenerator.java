package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.repository.PatientRepository;
import org.springframework.stereotype.Component;

/**
 * Issues the next {@code PAT-000000} patient number.
 *
 * <p>Derived from the highest number already issued rather than from a row count, so deleting
 * a patient cannot cause the next registration to collide with an existing number.
 *
 * <p><strong>Known limitation.</strong> Two registrations running concurrently can read the
 * same maximum and generate the same number. The unique index on {@code patient_no} turns
 * that into a failed insert rather than duplicate data — the same
 * belt-and-braces reasoning as assumption A11 — but the loser sees an error rather than
 * being retried. A database sequence would remove the race; at a clinic registering a handful
 * of patients a day, the added machinery is not yet justified.
 */
@Component
public class PatientNumberGenerator {

    private static final String PREFIX = "PAT-";
    private static final int DIGITS = 6;

    private final PatientRepository patients;

    public PatientNumberGenerator(PatientRepository patients) {
        this.patients = patients;
    }

    public String next() {
        long highest = patients.findHighestPatientNo()
                .map(PatientNumberGenerator::sequenceOf)
                .orElse(0L);
        return PREFIX + String.format("%0" + DIGITS + "d", highest + 1);
    }

    private static long sequenceOf(String patientNo) {
        try {
            return Long.parseLong(patientNo.substring(PREFIX.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // A hand-entered number that does not fit the pattern must not stop
            // registration; fall back to the start of the sequence.
            return 0L;
        }
    }
}
