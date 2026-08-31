package lk.icbt.dentalclinic.model.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bill arithmetic and payment state.
 *
 * <p>Totals are compared with {@code compareTo} rather than {@code equals}: {@code
 * BigDecimal.equals} also compares scale, so 6500.00 and 6500.0 are unequal by that measure
 * while being the same amount of money.
 */
class BillTest {

    private static final String ISSUER = "admin";

    private Bill newBill() {
        return new Bill("BILL-2026-0001", new BigDecimal("2500.00"), ISSUER);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("total is consultation fee plus treatment cost, less discount, plus tax")
    void calculatesTotalFromLines() {
        Bill bill = newBill();
        bill.addLine(new BillLineItem("Consultation", LineCategory.CONSULTATION,
                new BigDecimal("2500.00"), 1));
        bill.addLine(new BillLineItem("Composite Filling", LineCategory.TREATMENT,
                new BigDecimal("4500.00"), 1));

        assertAmount("2500.00", bill.getConsultationFee());
        assertAmount("4500.00", bill.getTreatmentCost());
        assertAmount("7000.00", bill.getTotalAmount());
    }

    @Test
    @DisplayName("quantity multiplies the line, and the bill total follows")
    void multipliesByQuantity() {
        Bill bill = newBill();
        bill.addLine(new BillLineItem("Root canal session", LineCategory.TREATMENT,
                new BigDecimal("28000.00"), 3));

        assertAmount("84000.00", bill.getTreatmentCost());
        assertAmount("84000.00", bill.getTotalAmount());
    }

    @Test
    @DisplayName("a percentage discount is taken off the pre-discount subtotal")
    void appliesPercentageDiscount() {
        Bill bill = newBill();
        bill.addLine(new BillLineItem("Consultation", LineCategory.CONSULTATION,
                new BigDecimal("2500.00"), 1));
        bill.addLine(new BillLineItem("Scaling and Polishing", LineCategory.TREATMENT,
                new BigDecimal("6500.00"), 1));

        bill.applyDiscount(new BigDecimal("10"));

        assertAmount("900.00", bill.getDiscountAmount());
        assertAmount("8100.00", bill.getTotalAmount());
    }

    @Test
    @DisplayName("a discount outside 0-100 is rejected")
    void rejectsImpossibleDiscount() {
        Bill bill = newBill();
        assertThrows(IllegalArgumentException.class,
                () -> bill.applyDiscount(new BigDecimal("-5")));
        assertThrows(IllegalArgumentException.class,
                () -> bill.applyDiscount(new BigDecimal("101")));
        assertThrows(IllegalArgumentException.class, () -> bill.applyDiscount(null));
    }

    @Test
    @DisplayName("a new bill is unpaid and has no payment method")
    void startsUnpaid() {
        Bill bill = newBill();
        assertEquals(PaymentStatus.UNPAID, bill.getStatus());
        assertTrue(bill.getPaymentMethod() == null);
    }

    @Test
    @DisplayName("recording payment sets the status and the method")
    void marksPaid() {
        Bill bill = newBill();
        bill.markPaid(PaymentMethod.CARD);

        assertEquals(PaymentStatus.PAID, bill.getStatus());
        assertEquals(PaymentMethod.CARD, bill.getPaymentMethod());
    }

    @Test
    @DisplayName("a bill cannot be paid twice")
    void rejectsDoublePayment() {
        Bill bill = newBill();
        bill.markPaid(PaymentMethod.CASH);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> bill.markPaid(PaymentMethod.CASH));
        assertTrue(thrown.getMessage().contains("already paid"));
    }

    @Test
    @DisplayName("payment requires a method")
    void rejectsPaymentWithoutMethod() {
        assertThrows(IllegalArgumentException.class, () -> newBill().markPaid(null));
    }

    @Test
    @DisplayName("only a paid bill can be refunded, and a refunded bill cannot be paid again")
    void guardsRefund() {
        Bill unpaid = newBill();
        assertThrows(IllegalStateException.class, unpaid::refund);

        Bill paid = newBill();
        paid.markPaid(PaymentMethod.INSURANCE);
        paid.refund();
        assertEquals(PaymentStatus.REFUNDED, paid.getStatus());
        assertThrows(IllegalStateException.class, () -> paid.markPaid(PaymentMethod.CASH));
    }

    @Test
    @DisplayName("a line item must have a quantity of at least one")
    void rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new BillLineItem("Nothing", LineCategory.TREATMENT, BigDecimal.TEN, 0));
    }
}
