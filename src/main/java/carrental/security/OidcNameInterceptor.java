package carrental.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Injects the current OIDC user's display name (or empty string when the
 * request is unauthenticated) into every Thymeleaf model as {@code oidcName}.
 *
 * <p>This avoids the brittle {@code ${#authentication?.principal?.attributes?.name}}
 * expression in {@code layout.html}, which throws a SpEL/OGNL evaluation
 * error when the principal is the string {@code "anonymousUser"} (i.e. when
 * the visitor is not signed in). Anonymous visitors have no {@code .attributes}
 * property on their principal, so the template crashes with a 500 error.</p>
 *
 * <p>Run after the controller method but before the view renders, so the
 * model already carries controller-added attributes when the template sees
 * {@code ${oidcName}}.</p>
 */
public class OidcNameInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           @Nullable ModelAndView modelAndView) {
        if (modelAndView == null) return;

        String name = "";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppOidcUser oidc) {
            name = oidc.getName() != null ? oidc.getName() : "";
        }

        // Only set if not already populated by the controller (defensive)
        if (!modelAndView.getModel().containsKey("oidcName")) {
            modelAndView.addObject("oidcName", name);
        }
    }
}
