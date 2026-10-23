package Backend.BrechoAPI.domain;


import Backend.BrechoAPI.domain.enums.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    










    @Column(length = 20)
    private String phone;

    
    
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private User(String name, String email, String passwordHash, Role role, String phone) {
        this.name = Objects.requireNonNull(name, "name é obrigatório");
        this.email = Objects.requireNonNull(email, "email é obrigatório").trim().toLowerCase();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash é obrigatório");
        this.role = Objects.requireNonNull(role, "role é obrigatória");
        this.phone = onlyDigits(phone);
    }

    
    
    public static User createWithRole(String name, String email, String passwordHash, Role role) {
        return new User(name, email, passwordHash, role, null);
    }

    









    public static User createClient(String name, String email, String passwordHash, String phone) {
        return new User(name, email, passwordHash, Role.CLIENT,
                Objects.requireNonNull(phone, "phone é obrigatório para cliente"));
    }

    

    private static String onlyDigits(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User other = (User) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
