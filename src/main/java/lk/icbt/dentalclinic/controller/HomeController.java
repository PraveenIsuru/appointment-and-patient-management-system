package lk.icbt.dentalclinic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the public landing page.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("clinicName", "Sunrise Dental Clinic");
        model.addAttribute("tagline", "Appointment & Patient Management System");
        return "index";
    }
}
