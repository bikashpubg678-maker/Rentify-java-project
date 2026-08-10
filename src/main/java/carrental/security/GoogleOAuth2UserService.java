package carrental.security;

import carrental.model.User;
import carrental.repository.UserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Upserts a Google sign-in into the users table.
 *
 * <p>Logic:
 * <ol>
 *   <li>If a user with the same {@code provider=GOOGLE, providerId=sub} exists, refresh name/avatar.</li>
 *   <li>Else, if a LOCAL user already exists with the same email, link it to Google.</li>
 *   <li>Else, create a fresh Google user.</li>
 * </ol>
 *
 * <p>The returned {@link OidcUser} is wrapped in {@link AppOidcUser} so controllers and
 * templates can pull our application {@link User} via {@code @AuthenticationPrincipal}.
 */
@Service
public class GoogleOAuth2UserService extends OidcUserService {

    private final UserRepository users;

    public GoogleOAuth2UserService(UserRepository users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidc = super.loadUser(request);
        OidcIdToken token = oidc.getIdToken();

        String sub       = oidc.getAttribute("sub");
        String email     = oidc.getAttribute("email");
        String name      = oidc.getAttribute("name");
        String picture   = oidc.getAttribute("picture");
        Boolean verified = oidc.getAttribute("email_verified");

        if (sub == null || email == null || !Boolean.TRUE.equals(verified)) {
            throw new OAuth2AuthenticationException(
                "Google account is missing required claims or email is not verified.");
        }

        User user = upsert(sub, email.toLowerCase().trim(),
                           name != null ? name : email, picture);
        return new AppOidcUser(oidc, user);
    }

    private User upsert(String providerId, String email, String displayName, String avatarUrl) {
        Optional<User> byProvider = users.findByProviderAndProviderId("GOOGLE", providerId);
        if (byProvider.isPresent()) {
            User u = byProvider.get();
            u.setDisplayName(displayName);
            u.setAvatarUrl(avatarUrl);
            return users.save(u);
        }
        Optional<User> byEmail = users.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User u = byEmail.get();
            u.setProvider("GOOGLE");
            u.setProviderId(providerId);
            u.setDisplayName(displayName);
            u.setAvatarUrl(avatarUrl);
            return users.save(u);
        }
        return users.save(User.fromGoogle(email, displayName, providerId, avatarUrl));
    }
}
