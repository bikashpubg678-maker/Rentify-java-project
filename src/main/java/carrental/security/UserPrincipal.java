package carrental.security;

import carrental.model.User;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lightweight, serializable stand-in for {@link User} used as the
 * {@code Authentication} principal in the JWT security context.
 *
 * <p>Holding a managed JPA entity as the principal is an anti-pattern: it
 * forces lazy-proxy initialization on every {@code equals}/{@code hashCode}
 * and pulls the whole entity graph through any JSON serializer. This record
 * carries only the fields the API actually needs.
 */
public record UserPrincipal(Long id, String email, String displayName, String role) implements Serializable {

    public static UserPrincipal from(User u) {
        Objects.requireNonNull(u, "user");
        return new UserPrincipal(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole());
    }
}