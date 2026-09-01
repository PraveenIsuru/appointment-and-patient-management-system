/**
 * Treatment pricing — the Strategy and Factory patterns.
 *
 * <p>Each treatment family carries its own arithmetic in its own class, and
 * {@code PricingStrategyFactory} chooses between them. {@code BillingService} never names a
 * concrete pricing class, so adding a new family means adding a class rather than editing a
 * conditional that grows every time the clinic changes its price list.
 */
package lk.icbt.dentalclinic.service.pricing;
