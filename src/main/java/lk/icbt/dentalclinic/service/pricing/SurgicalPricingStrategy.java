package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Surgical work carries a complexity loading.
 *
 * <p>Extractions and root canals take longer, need more of the dentist's attention and carry
 * more risk than the list price alone reflects. The loading is a single named constant so the
 * clinic can change it in one place.
 */
@Component
@Order(40)
public class SurgicalPricingStrategy implements TreatmentPricingStrategy {

    /** Surgical complexity adds 15%. */
    private static final BigDecimal COMPLEXITY_LOADING = new BigDecimal("0.15");

    @Override
    public boolean supports(TreatmentType type) {
        return type == TreatmentType.EXTRACTION || type == TreatmentType.ROOT_CANAL;
    }

    @Override
    public BigDecimal priceFor(Appointment appointment) {
        BigDecimal base = appointment.getTreatment().getBaseCost();
        return base.add(base.multiply(COMPLEXITY_LOADING)).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String describe(Appointment appointment) {
        return "List price plus 15% surgical complexity loading";
    }
}
