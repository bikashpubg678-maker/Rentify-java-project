package carrental.controller;

import carrental.security.AppOidcUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal AppOidcUser user,
                        @RequestParam(value = "error", required = false) String error,
                        Model model) {
        if (user != null) {
            return "redirect:/";
        }
        model.addAttribute("error", error != null);
        return "login";
    }
}
