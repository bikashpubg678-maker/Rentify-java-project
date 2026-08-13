package carrental.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link OidcNameInterceptor} so every Thymeleaf view can
 * safely read {@code ${oidcName}} without crashing on anonymous users.
 *
 * <p>Excludes static resources so the interceptor (which expects a
 * {@link org.springframework.web.servlet.ModelAndView}) does not run on
 * CSS/JS/image requests where no view is produced.</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new OidcNameInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**",
                        "/favicon.ico"
                );
    }
}