package carrental.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceProdGuardTest {

    private static MockEnvironment env(String... profiles) {
        MockEnvironment e = new MockEnvironment();
        e.setActiveProfiles(profiles);
        return e;
    }

    private static JwtService build(String secretValue, String... profiles) {
        JwtService svc = new JwtService(env(profiles));
        ReflectionTestUtils.setField(svc, "secret", secretValue);
        ReflectionTestUtils.setField(svc, "ttlMillis", 60_000L);
        return svc;
    }

    @Test
    void devProfileBootsWithPlaceholderSecret() {
        JwtService svc = build(
                "CHANGE-ME-rentify-default-secret-please-override-32bytes!!");
        assertDoesNotThrow(svc::init);
    }

    @Test
    void prodProfileRejectsPlaceholderSecret() {
        JwtService svc = build(
                "CHANGE-ME-rentify-default-secret-please-override-32bytes!!",
                "prod");
        assertThrows(IllegalStateException.class, svc::init);
    }

    @Test
    void prodProfileRejectsShortSecret() {
        JwtService svc = build("too-short", "prod");
        assertThrows(IllegalStateException.class, svc::init);
    }

    @Test
    void prodProfileAcceptsStrongSecret() {
        JwtService svc = build(
                "a-very-long-random-secret-with-at-least-thirty-two-bytes-OK",
                "prod");
        assertDoesNotThrow(svc::init);
    }
}
