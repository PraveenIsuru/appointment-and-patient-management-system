package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fillings carry a materials charge on top of the list price.
 *
 * <p>Composite material is consumed per filling and is the part of the cost that actually
 * varies, so it is shown as a loading rather than being buried in the base price. A patient
 * querying the bill can see what they paid for.
 */
@Component
@Order(30)
public class MaterialBasedPricingStrategy implements TreatmentPricingStrategy {

    /** Materials add 12% of the list price. */
    private static final BigDecimal MATERIAL_RATE = new BigDecimal("0.12");

    @Override
    public boolean supports(TreatmentType type) {
        return type == TreatmentType.FILLING;
    }

    @Override
    public BigDecimal priceFor(Appointment appointment) {
        BigDecimal base = appointment.getTreatment().getBaseCost();
        return base.add(base.multiply(MATERIAL_RATE)).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String describe(Appointment appointment) {
        return "List price plus 12% materials";
    }
}
