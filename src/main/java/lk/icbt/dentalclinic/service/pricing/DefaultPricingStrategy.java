package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fallback: charge the list price.
 *
 * <p>Ordered last and supports every type, so the factory can never fail to find a strategy.
 * A treatment type added to the enum without its own pricing rule is charged at list price
 * instead of throwing — being unable to invoice completed work would be a worse failure than
 * charging a possibly imprecise amount that a person can then correct.
 */
@Component
@Order(Integer.MAX_VALUE)
public class DefaultPricingStrategy implements TreatmentPricingStrategy {

    @Override
    public boolean supports(TreatmentType type) {
        return true;
    }

    @Override
    public BigDecimal priceFor(Appointment appointment) {
        return appointment.getTreatment().getBaseCost().setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String describe(Appointment appointment) {
        return "List price";
    }
}
