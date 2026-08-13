package carrental.api;

import carrental.model.User;
import carrental.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Seeds a default admin account on first startup so the app is never empty.
 *
 * <p>Reads {@code RENTIFY_ADMIN_EMAIL} and {@code RENTIFY_ADMIN_PASSWORD}
 * from the environment (or Spring properties). Falls back to safe local
 * demo defaults when not running in production; refuses to start with
 * the hard-coded defaults when the {@code prod} profile is active.
 *
 * <p>Idempotent — skipped entirely when at least one user already exists.
 */
@Component
public class UserSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeedRunner.class);

    private static final String DEFAULT_EMAIL = "admin@rentify.local";
    private static final String DEFAULT_PASSWORD = "rentify123";
    private static final String DEFAULT_NAME = "Rentify Admin";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Environment env;

    public UserSeedRunner(UserRepository users, PasswordEncoder encoder, Environment env) {
        this.users = users;
        this.encoder = encoder;
        this.env = env;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            log.debug("Users already present — skipping admin seed.");
            return;
        }

        boolean isProd = isProd();
        String email = env.getProperty("RENTIFY_ADMIN_EMAIL",
                isProd ? null : DEFAULT_EMAIL);
        String password = env.getProperty("RENTIFY_ADMIN_PASSWORD",
                isProd ? null : DEFAULT_PASSWORD);

        if (email == null || password == null) {
            throw new IllegalStateException(
                    "RENTIFY_ADMIN_EMAIL and RENTIFY_ADMIN_PASSWORD must be set " +
                    "in the environment when no users exist and the 'prod' " +
                    "profile is active.");
        }

        if (isProd && DEFAULT_EMAIL.equals(email) && DEFAULT_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "Refusing to start: the default admin credentials are still " +
                    "in use. Override RENTIFY_ADMIN_EMAIL and " +
                    "RENTIFY_ADMIN_PASSWORD before deploying.");
        }

        users.save(new User(email, DEFAULT_NAME, encoder.encode(password), "ADMIN"));
        log.info("Seeded admin user '{}'.", email);
    }

    private boolean isProd() {
        return Arrays.asList(env.getActiveProfiles()).contains("prod");
    }
}