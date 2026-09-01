package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.model.identity.Dentist;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.security.ClinicUserDetails;
import lk.icbt.dentalclinic.service.AppointmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * The three role-specific landing pages.
 *
 * <p>Each method carries a {@code @PreAuthorize} as well as being covered by a URL rule in
 * {@code SecurityConfig}. The duplication is deliberate: the URL rule is easy to read but
 * lives far from the code it protects, and a later change to a request mapping would silently
 * move a handler out from under it. The annotation travels with the method.
 * <p>Each handler tolerates a null principal. The URL rules mean an unauthenticated
 * request cannot reach these methods, but {@code @AuthenticationPrincipal} also binds null
 * when the principal is some other {@code UserDetails} implementation. Sending such a
 * request back to sign in is a great deal better than a 500 with a stack trace.
 */
@Controller
public class DashboardController {

    private final AppointmentService appointmentService;

    public DashboardController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDashboard(@AuthenticationPrincipal ClinicUserDetails principal,
                                 Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (principal == null) {
            return "redirect:/login";
        }
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("principal", principal);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("todaysAppointments",
                appointmentService.scheduleForDate(LocalDate.now()));
        return "dashboard/admin";
    }

    @GetMapping("/dentist/schedule")
    @PreAuthorize("hasAnyRole('DENTIST', 'ADMIN')")
    public String dentistSchedule(@AuthenticationPrincipal ClinicUserDetails principal,
                                  Model model) {
        model.addAttribute("principal", principal);
        model.addAttribute("today", LocalDate.now());
        if (principal.getUser() instanceof Dentist dentist) {
            model.addAttribute("dentist", dentist);
            model.addAttribute("todaysAppointments",
                    appointmentService.scheduleFor(dentist.getId(), LocalDate.now()));
        }
        return "dashboard/dentist";
    }

    @GetMapping("/patient/dashboard")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public String patientDashboard(@AuthenticationPrincipal ClinicUserDetails principal,
                                   Model model) {
        model.addAttribute("principal", principal);
        model.addAttribute("today", LocalDate.now());
        if (principal.getUser() instanceof Patient patient) {
            model.addAttribute("patient", patient);
            model.addAttribute("myAppointments",
                    appointmentService.findVisibleTo(patient));
        }
        return "dashboard/patient";
    }
}
