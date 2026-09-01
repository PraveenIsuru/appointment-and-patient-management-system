package lk.icbt.dentalclinic.exception;

/**
 * A bill that cannot be raised: the treatment is not finished, or one already exists.
 *
 * <p>Both are refusals of a well-formed request, not faults, so the message is written to be
 * read by the member of staff who tried.
 */
public class BillingNotAllowedException extends RuntimeException {

    public BillingNotAllowedException(String message) {
        super(message);
    }
}
