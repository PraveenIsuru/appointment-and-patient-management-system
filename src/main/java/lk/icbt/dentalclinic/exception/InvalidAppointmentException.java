package lk.icbt.dentalclinic.exception;

/**
 * A booking that breaks a clinic rule — a date in the past, a time outside consulting
 * hours, a slot off the half-hour boundary (assumption A10).
 *
 * <p>Distinct from a Bean Validation failure, which catches a malformed <em>field</em>. This
 * signals a well-formed request that the clinic will not accept.
 */
public class InvalidAppointmentException extends RuntimeException {

    private final String field;

    public InvalidAppointmentException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The form field to attach the message to, so the offending input is highlighted. */
    public String getField() {
        return field;
    }
}
