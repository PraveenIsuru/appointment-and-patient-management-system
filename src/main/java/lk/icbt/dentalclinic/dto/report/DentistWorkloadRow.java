package lk.icbt.dentalclinic.dto.report;

import java.math.BigDecimal;

/**
 * One line of the dentist workload report.
 *
 * @param dentistNo   clinic reference
 * @param dentistName who
 * @param booked      appointments in the period, cancellations excluded
 * @param completed   of those, how many were carried out
 * @param revenue     what their work billed
 */
public record DentistWorkloadRow(String dentistNo, String dentistName,
                                 long booked, long completed, BigDecimal revenue) {

    /**
     * Proportion of booked visits actually carried out, as a percentage.
     *
     * <p>The number the clinic actually cares about: a dentist with many bookings and few
     * completions is losing chair time to cancellations and no-shows.
     */
    public BigDecimal completionRate() {
        return booked == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed * 100.0 / booked)
                        .setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
