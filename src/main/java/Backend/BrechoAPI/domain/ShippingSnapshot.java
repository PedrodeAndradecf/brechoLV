package Backend.BrechoAPI.domain;

import Backend.BrechoAPI.domain.enums.UfBrasileira;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

























@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingSnapshot {

    @Column(name = "ship_recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "ship_phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "ship_zip_code", nullable = false, length = 8)
    private String zipCode;

    @Column(name = "ship_street", nullable = false, length = 150)
    private String street;

    
    
    @Column(name = "ship_number", nullable = false, length = 20)
    private String number;

    @Column(name = "ship_complement", length = 80)
    private String complement;

    @Column(name = "ship_district", nullable = false, length = 80)
    private String district;

    @Column(name = "ship_city", nullable = false, length = 80)
    private String city;

    
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "ship_uf", nullable = false, length = 2)
    private UfBrasileira uf;

    private ShippingSnapshot(String recipientName, String phone, String zipCode, String street,
                             String number, String complement, String district, String city,
                             UfBrasileira uf) {
        this.recipientName = Objects.requireNonNull(recipientName, "recipientName é obrigatório");
        this.phone = Objects.requireNonNull(phone, "phone é obrigatório");
        this.zipCode = Objects.requireNonNull(zipCode, "zipCode é obrigatório");
        this.street = Objects.requireNonNull(street, "street é obrigatório");
        this.number = Objects.requireNonNull(number, "number é obrigatório");
        this.complement = complement;
        this.district = Objects.requireNonNull(district, "district é obrigatório");
        this.city = Objects.requireNonNull(city, "city é obrigatória");
        this.uf = Objects.requireNonNull(uf, "uf é obrigatória");
    }

    































    public static ShippingSnapshot copyOf(Address address, User buyer) {
        Objects.requireNonNull(address, "address é obrigatório");
        Objects.requireNonNull(buyer, "buyer é obrigatório");
        return new ShippingSnapshot(
                buyer.getName(),
                Objects.requireNonNull(buyer.getPhone(), "phone do comprador é obrigatório no pedido"),
                address.getZipCode(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getDistrict(),
                address.getCity(),
                address.getUf());
    }

    
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShippingSnapshot other = (ShippingSnapshot) o;
        return Objects.equals(recipientName, other.recipientName)
                && Objects.equals(phone, other.phone)
                && Objects.equals(zipCode, other.zipCode)
                && Objects.equals(street, other.street)
                && Objects.equals(number, other.number)
                && Objects.equals(complement, other.complement)
                && Objects.equals(district, other.district)
                && Objects.equals(city, other.city)
                && uf == other.uf;
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientName, phone, zipCode, street, number, complement, district, city, uf);
    }
}
