package carrental.security;

import carrental.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Adapter that carries our application {@link User} alongside the standard OidcUser,
 * so Thymeleaf templates and {@code @AuthenticationPrincipal AppOidcUser} see both.
 */
public class AppOidcUser implements OidcUser {

    private final OidcUser delegate;
    private final User appUser;

    public AppOidcUser(OidcUser delegate, User appUser) {
        this.delegate = delegate;
        this.appUser = appUser;
    }

    public User getAppUser() { return appUser; }

    // ── OidcUser contract ──────────────────────────────────────────────────
    @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole()));
    }
    @Override public String getName() { return appUser.getDisplayName(); }
    @Override public OidcIdToken getIdToken() { return delegate.getIdToken(); }
    @Override public Map<String, Object> getClaims() { return delegate.getClaims(); }
    @Override public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }
}
