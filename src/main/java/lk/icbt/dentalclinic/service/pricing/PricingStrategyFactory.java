package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the pricing strategy for a treatment type — the <strong>Factory</strong> pattern.
 *
 * <p>Spring injects every {@link TreatmentPricingStrategy} bean in {@code @Order} sequence,
 * so registering a new family is a matter of adding a class with an {@code @Component}
 * annotation. Nothing here changes, and neither does the billing service.
 *
 * <p>{@link DefaultPricingStrategy} is ordered last and supports everything, so the list can
 * never be exhausted: a treatment type added to the enum without a matching strategy is
 * charged at its list price rather than throwing during billing. Failing to price a completed
 * treatment would leave the clinic unable to invoice work it has already done.
 */
@Component
public class PricingStrategyFactory {

    private final List<TreatmentPricingStrategy> strategies;

    public PricingStrategyFactory(List<TreatmentPricingStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /** The first registered strategy that supports this type. */
    public TreatmentPricingStrategy strategyFor(TreatmentType type) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No pricing strategy supports " + type
                                + ". DefaultPricingStrategy should have caught this."));
    }

    /** Registered strategies, in precedence order. Used by the design-pattern documentation. */
    public List<String> registeredStrategies() {
        return strategies.stream().map(s -> s.getClass().getSimpleName()).toList();
    }
}
