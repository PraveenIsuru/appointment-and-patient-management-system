package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.repository.BillRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Issues the next {@code BILL-<year>-<0000>} number.
 *
 * <p>Mirrors {@code AppointmentNumberGenerator} deliberately: staff read both numbers off the
 * same receipt, and two different formats would invite transcription errors. Same concurrency
 * caveat — the unique index on {@code bill_no} is what guarantees uniqueness.
 */
@Component
public class BillNumberGenerator {

    private static final String PREFIX = "BILL-";
    private static final int DIGITS = 4;

    private final BillRepository bills;
    private final Clock clock;

    public BillNumberGenerator(BillRepository bills, Clock clock) {
        this.bills = bills;
        this.clock = clock;
    }

    public String next() {
        String year = String.valueOf(Year.now(clock).getValue());
        long issued = bills.countIssuedInYear(year);
        return PREFIX + year + "-" + String.format("%0" + DIGITS + "d", issued + 1);
    }
}
