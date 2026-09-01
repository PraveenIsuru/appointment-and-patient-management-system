package lk.icbt.dentalclinic.exception;

/**
 * A registration that cannot proceed for a reason the user can act on — a username already
 * taken, for example.
 *
 * <p>Unchecked, because it signals a rejected request rather than a recoverable fault, and
 * because forcing every caller to declare it would add nothing.
 */
public class RegistrationException extends RuntimeException {

    private final String field;

    public RegistrationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The form field the message belongs against, so the UI can show it in the right place. */
    public String getField() {
        return field;
    }
}
