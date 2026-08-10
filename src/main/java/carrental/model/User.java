package carrental.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "email"),
           @UniqueConstraint(name = "uk_provider_providerid",
                             columnNames = {"provider", "providerId"})
       })
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 80)
    private String displayName;

    // Nullable: Google-only users have no password.
    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private String role = "USER";

    /** "LOCAL" for email/password users, "GOOGLE" for OAuth users. */
    @Column(nullable = false, length = 20)
    private String provider = "LOCAL";

    /** Google's stable user id ("sub"). Null for LOCAL users. */
    @Column(length = 100)
    private String providerId;

    /** Profile picture URL from Google. */
    @Column(length = 500)
    private String avatarUrl;

    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

    public User(String email, String displayName, String passwordHash, String role) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.provider = "LOCAL";
    }

    /** Convenience factory for Google sign-ups. */
    public static User fromGoogle(String email, String displayName, String providerId, String avatarUrl) {
        User u = new User();
        u.email = email;
        u.displayName = displayName;
        u.provider = "GOOGLE";
        u.providerId = providerId;
        u.avatarUrl = avatarUrl;
        u.role = "USER";
        return u;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
