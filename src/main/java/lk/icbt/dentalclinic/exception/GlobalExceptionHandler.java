package lk.icbt.dentalclinic.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Turns exceptions escaping a controller into pages a person can read.
 *
 * <p>Centralised here rather than repeated as try/catch in every handler, which is the
 * standard use of {@code @ControllerAdvice} and keeps the controllers about their own job.
 *
 * <p>Two rules govern what these pages say:
 * <ul>
 *   <li><strong>Never leak internals.</strong> A stack trace on screen tells an attacker the
 *       framework, the versions and often the schema.</li>
 *   <li><strong>Never confirm existence.</strong> "Not found" and "not yours" produce the
 *       same 404 for an unauthorised caller. Distinguishing them would let a patient discover
 *       which appointment numbers are real by watching the difference — assumption A8 would
 *       hold for the data and leak through the error page instead.</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppointmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(AppointmentNotFoundException e, Model model) {
        model.addAttribute("errorTitle", "Appointment not found");
        model.addAttribute("errorMessage",
                "No appointment exists with that number. Please check it and try again.");
        return "error/message";
    }

    /**
     * A refused row-level check renders as "not found", not "forbidden".
     *
     * <p>Telling a patient that an appointment exists but is not theirs is itself a
     * disclosure: they could enumerate valid numbers by the difference between the two
     * responses.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleAccessDenied(AccessDeniedException e, HttpServletRequest request,
                                     Model model) {
        log.warn("Access denied for {} on {}", request.getRemoteUser(), request.getRequestURI());
        model.addAttribute("errorTitle", "Appointment not found");
        model.addAttribute("errorMessage",
                "No appointment exists with that number. Please check it and try again.");
        return "error/message";
    }

    @ExceptionHandler(SlotUnavailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleSlotUnavailable(SlotUnavailableException e, Model model) {
        model.addAttribute("errorTitle", "That time is no longer free");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/message";
    }

    @ExceptionHandler(InvalidAppointmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidAppointment(InvalidAppointmentException e, Model model) {
        model.addAttribute("errorTitle", "That appointment cannot be made");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/message";
    }

    /**
     * An illegal lifecycle transition — cancelling an already-completed visit, for example.
     *
     * <p>The message comes from the domain and is written to be read by staff, so it is shown
     * as it is rather than replaced with something vaguer.
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleIllegalState(IllegalStateException e, Model model) {
        model.addAttribute("errorTitle", "That change is not allowed");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/message";
    }
}
