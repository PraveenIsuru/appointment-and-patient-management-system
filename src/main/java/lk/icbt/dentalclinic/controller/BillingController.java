package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.model.billing.Bill;
import lk.icbt.dentalclinic.model.billing.PaymentMethod;
import lk.icbt.dentalclinic.security.ClinicUserDetails;
import lk.icbt.dentalclinic.service.BillingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * Billing and receipts — brief requirement 4.
 *
 * <p>Raising a bill and recording payment are restricted to administrators: money is the
 * clinic's business, not the patient's or the dentist's. Viewing and printing a receipt is
 * open to whoever may see the underlying appointment, so a patient can print their own.
 */
@Controller
@RequestMapping("/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** UC30 — raise the bill for a completed visit. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String generate(@RequestParam String appointmentNo,
                           @RequestParam(required = false) BigDecimal discountPercent,
                           @AuthenticationPrincipal ClinicUserDetails principal,
                           RedirectAttributes flash) {
        Bill bill = billingService.generateBill(appointmentNo, discountPercent,
                principal.getUser());
        flash.addFlashAttribute("successMessage",
                "Bill " + bill.getBillNo() + " raised for " + appointmentNo + ".");
        return "redirect:/billing/" + bill.getBillNo();
    }

    /** The itemised receipt on screen. */
    @GetMapping("/{billNo}")
    @PreAuthorize("isAuthenticated()")
    public String view(@PathVariable String billNo,
                       @AuthenticationPrincipal ClinicUserDetails principal,
                       Model model) {
        model.addAttribute("bill", billingService.findForUser(billNo, principal.getUser()));
        model.addAttribute("appointment", billingService.appointmentForBill(billNo));
        model.addAttribute("principal", principal);
        return "billing/receipt";
    }

    /**
     * UC33 {@code <<extend>>} — the print-optimised receipt.
     *
     * <p>A separate view rather than a print stylesheet on the same page: the printed receipt
     * drops the navigation and the action buttons entirely, which a media query would only
     * hide. What goes on paper is then exactly what was intended to.
     */
    @GetMapping("/{billNo}/print")
    @PreAuthorize("isAuthenticated()")
    public String print(@PathVariable String billNo,
                        @AuthenticationPrincipal ClinicUserDetails principal,
                        Model model) {
        model.addAttribute("bill", billingService.findForUser(billNo, principal.getUser()));
        model.addAttribute("appointment", billingService.appointmentForBill(billNo));
        return "billing/print";
    }

    @PostMapping("/{billNo}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public String recordPayment(@PathVariable String billNo,
                                @RequestParam PaymentMethod method,
                                RedirectAttributes flash) {
        billingService.recordPayment(billNo, method);
        flash.addFlashAttribute("successMessage", "Payment recorded.");
        return "redirect:/billing/" + billNo;
    }
}
