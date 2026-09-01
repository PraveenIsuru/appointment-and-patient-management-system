package lk.icbt.dentalclinic.exception;

/** No appointment exists with the given number. */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(String appointmentNo) {
        super("No appointment found with number " + appointmentNo);
    }
}
