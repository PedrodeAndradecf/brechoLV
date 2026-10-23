package Backend.BrechoAPI.repository;

import Backend.BrechoAPI.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    
    
    
    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    List<Address> findAllByUserId(UUID userId);
}
