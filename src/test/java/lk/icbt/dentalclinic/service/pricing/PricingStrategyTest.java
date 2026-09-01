package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.identity.Dentist;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.model.scheduling.Treatment;
import lk.icbt.dentalclinic.model.scheduling.TreatmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Strategy and Factory patterns behind treatment pricing.
 *
 * <p>Each strategy is a plain object, so its arithmetic is checked directly without a
 * database, a Spring context or a bill. That testability is the main reason the rules live in
 * their own classes instead of in a switch inside {@code BillingService}.
 */
class PricingStrategyTest {

    private static final BigDecimal BASE = new BigDecimal("12000.00");

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    /** An appointment carrying only what a pricing strategy actually reads. */
    private Appointment appointmentFor(TreatmentType type, BigDecimal baseCost, int sessions) {
        Patient patient = new Patient("p", "h", "p@mail.lk", "A Patient", "0770000000",
                "PAT-000900", "Somewhere", LocalDate.of(1990, 1, 1));
        Dentist dentist = new Dentist("d", "h", "d@clinic.lk", "A Dentist", "0770000001",
                "DEN-900", "General", "SLMC-900", new BigDecimal("2500.00"),
                LocalTime.of(8, 0), LocalTime.of(20, 0));
        Treatment treatment = new Treatment("T-900", type.name(), type, baseCost, 30, sessions);
        return new Appointment("APT-2026-9900", patient, dentist, treatment,
                LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), "admin");
    }

    // -------------------------------------------------------------- consultation ---

    @Test
    @DisplayName("a consultation carries no treatment charge")
    void consultationIsFree() {
        var strategy = new ConsultationPricingStrategy();
        assertTrue(strategy.supports(TreatmentType.CONSULTATION));
        assertAmount("0.00", strategy.priceFor(
                appointmentFor(TreatmentType.CONSULTATION, BigDecimal.ZERO, 1)));
    }

    // ------------------------------------------------------------------ flat rate ---

    @Test
    @DisplayName("hygiene work is charged at list price")
    void flatRateChargesListPrice() {
        var strategy = new FlatRatePricingStrategy();
        assertTrue(strategy.supports(TreatmentType.SCALING_AND_POLISHING));
        assertAmount("6500.00", strategy.priceFor(
                appointmentFor(TreatmentType.SCALING_AND_POLISHING, new BigDecimal("6500.00"), 1)));
    }

    // ------------------------------------------------------------------- material ---

    @Test
    @DisplayName("a filling adds 12% for materials")
    void fillingAddsMaterials() {
        var strategy = new MaterialBasedPricingStrategy();
        assertTrue(strategy.supports(TreatmentType.FILLING));
        // 4500 + 12% = 5040
        assertAmount("5040.00", strategy.priceFor(
                appointmentFor(TreatmentType.FILLING, new BigDecimal("4500.00"), 1)));
    }

    // ------------------------------------------------------------------- surgical ---

    @Nested
    @DisplayName("surgical pricing")
    class Surgical {

        private final SurgicalPricingStrategy strategy = new SurgicalPricingStrategy();

        @Test
        @DisplayName("covers extractions and root canals")
        void coversSurgicalFamilies() {
            assertTrue(strategy.supports(TreatmentType.EXTRACTION));
            assertTrue(strategy.supports(TreatmentType.ROOT_CANAL));
            assertFalse(strategy.supports(TreatmentType.SCALING_AND_POLISHING));
        }

        @Test
        @DisplayName("adds a 15% complexity loading — 12,000 becomes 13,800")
        void addsComplexityLoading() {
            // The worked example from the Generate Bill sequence diagram.
            assertAmount("13800.00", strategy.priceFor(
                    appointmentFor(TreatmentType.EXTRACTION, BASE, 1)));
        }

        @Test
        @DisplayName("rounds to two decimal places")
        void roundsToCents() {
            BigDecimal price = strategy.priceFor(
                    appointmentFor(TreatmentType.ROOT_CANAL, new BigDecimal("28000.33"), 1));
            assertEquals(2, price.scale());
            assertAmount("32200.38", price);
        }
    }

    // ------------------------------------------------------------------ instalment ---

    @Test
    @DisplayName("a multi-session course is billed one instalment at a time")
    void instalmentDividesTheCourse() {
        var strategy = new InstalmentPlanPricingStrategy();
        assertTrue(strategy.supports(TreatmentType.ORTHODONTIC));
        // 9600 over 3 sessions = 3200 per visit
        assertAmount("3200.00", strategy.priceFor(
                appointmentFor(TreatmentType.ORTHODONTIC, new BigDecimal("9600.00"), 3)));
    }

    @Test
    @DisplayName("a single-session course is charged in full, and never divides by zero")
    void instalmentHandlesSingleSession() {
        var strategy = new InstalmentPlanPricingStrategy();
        assertAmount("9500.00", strategy.priceFor(
                appointmentFor(TreatmentType.ORTHODONTIC, new BigDecimal("9500.00"), 1)));
        assertAmount("9500.00", strategy.priceFor(
                appointmentFor(TreatmentType.ORTHODONTIC, new BigDecimal("9500.00"), 0)));
    }

    // --------------------------------------------------------------------- factory ---

    @Nested
    @DisplayName("the factory")
    class Factory {

        private final PricingStrategyFactory factory = new PricingStrategyFactory(List.of(
                new ConsultationPricingStrategy(),
                new FlatRatePricingStrategy(),
                new MaterialBasedPricingStrategy(),
                new SurgicalPricingStrategy(),
                new InstalmentPlanPricingStrategy(),
                new DefaultPricingStrategy()));

        @Test
        @DisplayName("resolves a root canal to the surgical strategy, as the diagram specifies")
        void resolvesRootCanalToSurgical() {
            assertInstanceOf(SurgicalPricingStrategy.class,
                    factory.strategyFor(TreatmentType.ROOT_CANAL));
        }

        @Test
        @DisplayName("routes each family to its own strategy")
        void routesEveryFamily() {
            assertInstanceOf(ConsultationPricingStrategy.class,
                    factory.strategyFor(TreatmentType.CONSULTATION));
            assertInstanceOf(FlatRatePricingStrategy.class,
                    factory.strategyFor(TreatmentType.SCALING_AND_POLISHING));
            assertInstanceOf(MaterialBasedPricingStrategy.class,
                    factory.strategyFor(TreatmentType.FILLING));
            assertInstanceOf(SurgicalPricingStrategy.class,
                    factory.strategyFor(TreatmentType.EXTRACTION));
            assertInstanceOf(InstalmentPlanPricingStrategy.class,
                    factory.strategyFor(TreatmentType.ORTHODONTIC));
        }

        @ParameterizedTest
        @EnumSource(TreatmentType.class)
        @DisplayName("every treatment type resolves to something — the factory never fails")
        void everyTypeResolves(TreatmentType type) {
            assertNotNull(factory.strategyFor(type));
        }

        @Test
        @DisplayName("the fallback catches a type with no rule of its own")
        void fallbackCatchesUnknownTypes() {
            var onlyDefault = new PricingStrategyFactory(List.of(new DefaultPricingStrategy()));
            assertInstanceOf(DefaultPricingStrategy.class,
                    onlyDefault.strategyFor(TreatmentType.ROOT_CANAL));
        }
    }
}
