package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.exception.AppointmentNotFoundException;
import lk.icbt.dentalclinic.exception.BillingNotAllowedException;
import lk.icbt.dentalclinic.model.billing.Bill;
import lk.icbt.dentalclinic.model.billing.BillLineItem;
import lk.icbt.dentalclinic.model.billing.LineCategory;
import lk.icbt.dentalclinic.model.billing.PaymentMethod;
import lk.icbt.dentalclinic.model.identity.User;
import lk.icbt.dentalclinic.model.scheduling.Appointment;
import lk.icbt.dentalclinic.repository.AppointmentRepository;
import lk.icbt.dentalclinic.repository.BillRepository;
import lk.icbt.dentalclinic.security.AppointmentAccessPolicy;
import lk.icbt.dentalclinic.service.pricing.PricingStrategyFactory;
import lk.icbt.dentalclinic.service.pricing.TreatmentPricingStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

/**
 * Billing — brief requirement 4, "calculate the total treatment cost based on treatment type
 * and consultation fee, and print the patient bill".
 *
 * <p>Realises use case UC30 and the sequence diagram {@code sequence-generate-bill.puml}.
 * Note what this class does <em>not</em> contain: any arithmetic that depends on the kind of
 * treatment. That lives behind {@link PricingStrategyFactory}, so a change to how root canals
 * are priced never reaches this file.
 */
@Service
public class BillingService {

    /**
     * The largest discount anyone may authorise.
     *
     * <p>A cap rather than an open field: a mistyped percentage should be refused, not
     * silently applied to a patient's bill. Every discount is stored as its own line, so the
     * reason for a reduced total is visible on the receipt and in the audit trail.
     */
    public static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("25");

    private final AppointmentRepository appointments;
    private final BillRepository bills;
    private final PricingStrategyFactory pricingStrategies;
    private final BillNumberGenerator numberGenerator;
    private final AppointmentAccessPolicy accessPolicy;

    public BillingService(AppointmentRepository appointments,
                          BillRepository bills,
                          PricingStrategyFactory pricingStrategies,
                          BillNumberGenerator numberGenerator,
                          AppointmentAccessPolicy accessPolicy) {
        this.appointments = appointments;
        this.bills = bills;
        this.pricingStrategies = pricingStrategies;
        this.numberGenerator = numberGenerator;
        this.accessPolicy = accessPolicy;
    }

    /**
     * Raises the bill for a completed appointment (UC30).
     *
     * @param appointmentNo the visit to bill
     * @param discountPercent optional discount, 0–25; the {@code <<extend>>} of UC32
     * @throws BillingNotAllowedException if the visit is not complete, or already billed
     */
    @Transactional
    public Bill generateBill(String appointmentNo, BigDecimal discountPercent, User issuedBy) {
        Appointment appointment = appointments.findByAppointmentNo(normalise(appointmentNo))
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentNo));

        // A bill exposes the patient's treatment and address, so the same row-level rule
        // that guards the appointment guards its bill.
        if (!accessPolicy.canView(issuedBy, appointment)) {
            throw new AppointmentNotFoundException(appointmentNo);
        }
        if (!appointment.isBillable()) {
            throw new BillingNotAllowedException(
                    "A bill can only be raised for a completed treatment. Appointment "
                            + appointment.getAppointmentNo() + " is " + appointment.getStatus() + ".");
        }
        if (appointment.getBill() != null) {
            throw new BillingNotAllowedException("Appointment " + appointment.getAppointmentNo()
                    + " has already been billed as " + appointment.getBill().getBillNo() + ".");
        }

        BigDecimal consultationFee = appointment.getDentist().getConsultationFee();

        // UC31 — the Factory picks the rule, the Strategy applies it. No treatment type is
        // named anywhere in this method.
        TreatmentPricingStrategy strategy =
                pricingStrategies.strategyFor(appointment.getTreatment().getType());
        BigDecimal treatmentCost = strategy.priceFor(appointment);

        Bill bill = new Bill(numberGenerator.next(), consultationFee, issuedBy.getUsername());
        bill.addLine(new BillLineItem(
                "Consultation — " + appointment.getDentist().getFullName(),
                LineCategory.CONSULTATION, consultationFee, 1));
        bill.addLine(new BillLineItem(
                appointment.getTreatment().getName() + " (" + strategy.describe(appointment) + ")",
                LineCategory.TREATMENT, treatmentCost, 1));

        if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {
            applyDiscount(bill, discountPercent);
        }

        Bill saved = bills.saveAndFlush(bill);

        // Navigability runs Appointment -> Bill in the class diagram, so the appointment
        // holds the link and the unique index on bill_id enforces "at most one".
        appointment.setBill(saved);
        appointments.saveAndFlush(appointment);

        return saved;
    }

    /** UC32 {@code <<extend>>} — a capped, audited discount. */
    private void applyDiscount(Bill bill, BigDecimal discountPercent) {
        if (discountPercent.compareTo(MAX_DISCOUNT_PERCENT) > 0) {
            throw new BillingNotAllowedException(
                    "A discount may not exceed " + MAX_DISCOUNT_PERCENT + "%.");
        }
        bill.applyDiscount(discountPercent);
    }

    /** Records payment against a bill. */
    @Transactional
    public Bill recordPayment(String billNo, PaymentMethod method) {
        Bill bill = bills.findByBillNo(billNo)
                .orElseThrow(() -> new NoSuchElementException("No bill numbered " + billNo));
        bill.markPaid(method);
        return bills.saveAndFlush(bill);
    }

    /**
     * Finds a bill for display, applying the same row-level rule as the appointment.
     *
     * <p>Looked up through the appointment rather than directly, because that is where
     * ownership is recorded — a bill on its own does not know whose it is.
     */
    @Transactional(readOnly = true)
    public Bill findForUser(String billNo, User currentUser) {
        Bill bill = bills.findByBillNo(billNo)
                .orElseThrow(() -> new NoSuchElementException("No bill numbered " + billNo));
        Appointment appointment = appointments.findAllWithDetails().stream()
                .filter(a -> a.getBill() != null && a.getBill().getId().equals(bill.getId()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Bill " + billNo + " is not linked to an appointment"));
        if (!accessPolicy.canView(currentUser, appointment)) {
            throw new NoSuchElementException("No bill numbered " + billNo);
        }
        return bill;
    }

    /** The appointment a bill belongs to, for the receipt header. */
    @Transactional(readOnly = true)
    public Appointment appointmentForBill(String billNo) {
        return appointments.findAllWithDetails().stream()
                .filter(a -> a.getBill() != null && billNo.equals(a.getBill().getBillNo()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Bill " + billNo + " is not linked to an appointment"));
    }

    private String normalise(String appointmentNo) {
        return appointmentNo == null ? "" : appointmentNo.trim().toUpperCase();
    }
}
