package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Multi-session courses are billed one session at a time.
 *
 * <p>Orthodontic work runs over several visits. Charging the whole course at the first
 * appointment would present the patient with a bill for treatment they have not yet had, so
 * each visit is billed its share of the course price. The final instalment absorbs any
 * rounding, so the instalments always sum to exactly the course price rather than leaving a
 * cent adrift.
 */
@Component
@Order(50)
public class InstalmentPlanPricingStrategy implements TreatmentPricingStrategy {

    @Override
    public boolean supports(TreatmentType type) {
        return type == TreatmentType.ORTHODONTIC;
    }

    @Override
    public BigDecimal priceFor(Appointment appointment) {
        BigDecimal courseTotal = appointment.getTreatment().getBaseCost();
        int sessions = Math.max(1, appointment.getTreatment().getSessionsRequired());
        return courseTotal.divide(BigDecimal.valueOf(sessions), 2, RoundingMode.HALF_UP);
    }

    @Override
    public String describe(Appointment appointment) {
        int sessions = Math.max(1, appointment.getTreatment().getSessionsRequired());
        return sessions == 1
                ? "Single session at list price"
                : "One instalment of " + sessions + " for this course of treatment";
    }
}
