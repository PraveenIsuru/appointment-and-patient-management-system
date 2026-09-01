package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;

import java.math.BigDecimal;

/**
 * How one family of treatments is priced — the <strong>Strategy</strong> pattern.
 *
 * <p>The brief requires the total to be calculated "based on treatment type and consultation
 * fee". Written as a switch inside the billing service, that becomes a statement that grows a
 * branch every time the clinic changes its price list, and cannot be tested without the
 * service around it. Each implementation here is a few lines, testable on its own, and named
 * after the rule it encodes.
 *
 * <p>Implementations must be stateless: one instance is shared as a Spring singleton across
 * every request.
 */
public interface TreatmentPricingStrategy {

    /** Whether this strategy prices the given family of treatments. */
    boolean supports(TreatmentType type);

    /**
     * The treatment cost, excluding the dentist's consultation fee.
     *
     * <p>The consultation fee is added by the billing service from the dentist, because a
     * senior dentist charges more to see the same problem.
     */
    BigDecimal priceFor(Appointment appointment);

    /** How this figure was arrived at, printed on the receipt so the patient can check it. */
    String describe(Appointment appointment);
}
