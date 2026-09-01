package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Routine hygiene work is charged at the published list price.
 *
 * <p>Scaling and polishing takes the same time and the same materials whoever is in the
 * chair, so there is nothing to vary.
 */
@Component
@Order(20)
public class FlatRatePricingStrategy implements TreatmentPricingStrategy {

    @Override
    public boolean supports(TreatmentType type) {
        return type == TreatmentType.SCALING_AND_POLISHING;
    }

    @Override
    public BigDecimal priceFor(Appointment appointment) {
        return appointment.getTreatment().getBaseCost().setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String describe(Appointment appointment) {
        return "Flat rate at list price";
    }
}
