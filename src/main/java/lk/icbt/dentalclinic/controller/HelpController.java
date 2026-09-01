package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.security.ClinicUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The help page — requirement 5 of the brief, "step-by-step instructions for new staff".
 *
 * <p>Available to any signed-in user; the page itself explains only the tasks the reader's
 * own role can perform.
 */
@Controller
public class HelpController {

    @GetMapping("/help")
    public String help(@AuthenticationPrincipal ClinicUserDetails principal, Model model) {
        model.addAttribute("principal", principal);
        return "help";
    }
}
