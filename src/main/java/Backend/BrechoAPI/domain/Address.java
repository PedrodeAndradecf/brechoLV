package Backend.BrechoAPI.domain;

import Backend.BrechoAPI.domain.enums.UfBrasileira;
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
@Table(name = "addresses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    

    @Column(name = "zip_code", nullable = false, length = 8)
    private String zipCode;

    @Column(nullable = false, length = 150)
    private String street;

    
    @Column(nullable = false, length = 20)
    private String number;

    @Column(length = 80)
    private String complement;

    @Column(nullable = false, length = 80)
    private String district;

    @Column(nullable = false, length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private UfBrasileira uf;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Address(User user, String zipCode, String street, String number, String complement,
                     String district, String city, UfBrasileira uf) {
        this.user = Objects.requireNonNull(user, "user é obrigatório");
        this.zipCode = onlyDigits(Objects.requireNonNull(zipCode, "zipCode é obrigatório"));
        this.street = Objects.requireNonNull(street, "street é obrigatório");
        this.number = Objects.requireNonNull(number, "number é obrigatório");
        this.complement = complement;
        this.district = Objects.requireNonNull(district, "district é obrigatório");
        this.city = Objects.requireNonNull(city, "city é obrigatório");
        this.uf = Objects.requireNonNull(uf, "uf é obrigatória");
    }

    public static Address create(User user, String zipCode, String street, String number, String complement,
                                  String district, String city, UfBrasileira uf) {
        return new Address(user, zipCode, street, number, complement, district, city, uf);
    }

    
    public void update(String zipCode, String street, String number, String complement,
                        String district, String city, UfBrasileira uf) {
        this.zipCode = onlyDigits(Objects.requireNonNull(zipCode, "zipCode é obrigatório"));
        this.street = Objects.requireNonNull(street, "street é obrigatório");
        this.number = Objects.requireNonNull(number, "number é obrigatório");
        this.complement = complement;
        this.district = Objects.requireNonNull(district, "district é obrigatório");
        this.city = Objects.requireNonNull(city, "city é obrigatório");
        this.uf = Objects.requireNonNull(uf, "uf é obrigatória");
    }

    

    private static String onlyDigits(String value) {
        return value.replaceAll("\\D", "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address other = (Address) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
