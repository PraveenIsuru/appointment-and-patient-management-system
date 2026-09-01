package lk.icbt.dentalclinic.controller;

import jakarta.validation.Valid;
import lk.icbt.dentalclinic.dto.PatientRegistrationForm;
import lk.icbt.dentalclinic.exception.RegistrationException;
import lk.icbt.dentalclinic.model.identity.Patient;
import lk.icbt.dentalclinic.service.RegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Sign-in and patient self-registration.
 *
 * <p>Sign-in itself is handled entirely by Spring Security's filter chain; this controller
 * only renders the form and translates the query parameters the chain redirects back with
 * into messages a person can read.
 */
@Controller
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String expired,
                        @RequestParam(required = false) String registered,
                        Model model) {
        if (error != null) {
            // One message for every kind of failure. Saying "no such user" would let the
            // form be used to discover which accounts exist.
            model.addAttribute("errorMessage", "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "You have been signed out.");
        }
        if (expired != null) {
            model.addAttribute("errorMessage", "Your session has expired. Please sign in again.");
        }
        if (registered != null) {
            model.addAttribute("successMessage",
                    "Your account has been created. Your patient number is " + registered + ".");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        model.addAttribute("form", new PatientRegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") PatientRegistrationForm form,
                           BindingResult binding,
                           RedirectAttributes redirectAttributes) {
        // Field-level annotation failures first; no point checking uniqueness against a
        // username that is not even well formed.
        if (binding.hasErrors()) {
            return "auth/register";
        }
        try {
            Patient created = registrationService.register(form);
            redirectAttributes.addAttribute("registered", created.getPatientNo());
            return "redirect:/login";
        } catch (RegistrationException e) {
            // Attach the message to the field it belongs to, so the form highlights the
            // offending input rather than showing a banner at the top.
            binding.rejectValue(e.getField(), "registration.rejected", e.getMessage());
            return "auth/register";
        }
    }
}
