package carrental.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Three filter chains, evaluated in order. The first matcher wins.
 *
 * <ol>
 *   <li><b>apiChain</b> — {@code /api/v1/**} and {@code /api/chat}. Stateless JWT, public login + register.</li>
 *   <li><b>oauth2Chain</b> — {@code /login}, {@code /oauth2/**}, {@code /logout}. Enables Google OAuth2 login.</li>
 *   <li><b>webChain</b> — everything else. Existing Thymeleaf app, fully open.</li>
 * </ol>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final GoogleOAuth2UserService googleOAuth2UserService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, GoogleOAuth2UserService googleOAuth2UserService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.googleOAuth2UserService = googleOAuth2UserService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── 1. REST API: stateful JWT, no OAuth ───────────────────────────────
    @Bean
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/**", "/api/chat")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ── 2. OAuth2 web login ───────────────────────────────────────────────
    @Bean
    public SecurityFilterChain oauth2Chain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/login", "/login/**", "/oauth2/**", "/logout/**", "/error")
            .csrf(c -> c.ignoringRequestMatchers("/oauth2/**", "/login/**", "/logout/**"))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(u -> u.oidcUserService(googleOAuth2UserService))
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error"))
            .logout(l -> l
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true));
        return http.build();
    }

    // ── 3. Public Thymeleaf pages (REST + chat excluded above) ────────────
    @Bean
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
