package lk.icbt.dentalclinic.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Headline figures for one day.
 *
 * @param date        the day reported on
 * @param booked      appointments scheduled
 * @param completed   treatments carried out
 * @param cancelled   called off
 * @param noShows     patients who did not attend
 * @param billed      total value of bills raised
 * @param collected   of that, how much has been paid
 */
public record DailySummary(LocalDate date, long booked, long completed, long cancelled,
                           long noShows, BigDecimal billed, BigDecimal collected) {

    /** Money billed but not yet received. */
    public BigDecimal outstanding() {
        return billed.subtract(collected);
    }

    /**
     * Proportion of booked visits lost to cancellation or non-attendance.
     *
     * <p>Directly measures one of the four problems the clinic reported.
     */
    public BigDecimal lostSlotPercentage() {
        return booked == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf((cancelled + noShows) * 100.0 / booked)
                        .setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
