package lk.icbt.dentalclinic.controller;

import lk.icbt.dentalclinic.security.ClinicUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The access-denied page.
 *
 * <p>Reached when an authenticated user asks for something their role does not permit. It
 * says only that access was refused — never what the resource was or who may see it — so a
 * refusal cannot be used to map out the parts of the system a user cannot reach.
 */
@Controller
public class ErrorPageController {

    @GetMapping("/error/403")
    public String accessDenied(@AuthenticationPrincipal ClinicUserDetails principal,
                               Model model) {
        if (principal != null) {
            model.addAttribute("principal", principal);
            model.addAttribute("returnUrl", principal.getDashboardUrl());
        } else {
            model.addAttribute("returnUrl", "/");
        }
        return "error/403";
    }
}
