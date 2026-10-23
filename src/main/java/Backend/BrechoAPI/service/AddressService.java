package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Address;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.dto.request.AddressRequestDTO;
import Backend.BrechoAPI.dto.response.AddressResponseDTO;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.AddressRepository;
import Backend.BrechoAPI.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;











@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    




    @Transactional
    public AddressResponseDTO create(UUID userId, AddressRequestDTO request) {
        UfBrasileira uf = parseUf(request.uf());
        User user = userRepository.getReferenceById(userId);

        Address address = Address.create(user, request.zipCode(), request.street(), request.number(),
                request.complement(), request.district(), request.city(), uf);

        return toResponse(addressRepository.save(address));
    }

    public List<AddressResponseDTO> listOwn(UUID userId) {
        return addressRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponseDTO update(UUID userId, UUID id, AddressRequestDTO request) {
        Address address = addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ADDRESS_NOT_FOUND, "endereço não encontrado"));

        UfBrasileira uf = parseUf(request.uf());
        address.update(request.zipCode(), request.street(), request.number(), request.complement(),
                request.district(), request.city(), uf);

        return toResponse(address);
    }

    













    private UfBrasileira parseUf(String rawUf) {
        String normalized = rawUf.trim().toUpperCase();
        try {
            return UfBrasileira.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new DomainRuleException(ErrorCode.DOMAIN_RULE_VIOLATION, "uf inválida: " + rawUf);
        }
    }

    private AddressResponseDTO toResponse(Address address) {
        return new AddressResponseDTO(
                address.getId(), address.getZipCode(), address.getStreet(), address.getNumber(),
                address.getComplement(), address.getDistrict(), address.getCity(), address.getUf().name());
    }
}
