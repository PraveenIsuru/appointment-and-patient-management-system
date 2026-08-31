package lk.icbt.dentalclinic.model.scheduling;

/**
 * Category of dental work.
 *
 * <p>Deliberately carries no price. Pricing is resolved at run time by
 * {@code PricingStrategyFactory} from this value (Strategy pattern, M5), so that a change
 * to how a treatment is priced never means editing this enumeration.
 */
public enum TreatmentType {

    CONSULTATION,
    SCALING_AND_POLISHING,
    FILLING,
    EXTRACTION,
    ROOT_CANAL,
    ORTHODONTIC
}
