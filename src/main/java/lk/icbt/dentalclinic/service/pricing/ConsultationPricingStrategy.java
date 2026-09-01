package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A consultation on its own carries no treatment cost.
 *
 * <p>The patient is charged the dentist's consultation fee and nothing more, which is why the
 * seeded {@code CONS-01} has a base cost of zero. Stated as its own strategy rather than
 * relying on that zero, so the rule survives someone editing the price list.
 */
@Component
@Order(10)
public class ConsultationPricingStrategy implements TreatmentPricingStrategy {

    @Override
    public boolean supports(TreatmentType type) {
        return type == TreatmentType.CONSULTATION;
    }

    @Override
    public BigDecimal priceFor(Appointment appointment) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String describe(Appointment appointment) {
        return "Consultation only — no treatment charge";
    }
}
