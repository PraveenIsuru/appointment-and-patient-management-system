package lk.icbt.dentalclinic.dto.report;

import lk.icbt.dentalclinic.model.scheduling.TreatmentType;

import java.math.BigDecimal;

/**
 * One line of the revenue-by-treatment report.
 *
 * @param treatmentType which family of work
 * @param visits        how many visits, cancellations excluded
 * @param revenue       gross takings, zero where no bill has been raised yet
 */
public record RevenueByTreatmentRow(TreatmentType treatmentType, long visits, BigDecimal revenue) {

    /** Average value of a visit, for judging which work is worth the chair time. */
    public BigDecimal averagePerVisit() {
        return visits == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(visits), 2, java.math.RoundingMode.HALF_UP);
    }
}
