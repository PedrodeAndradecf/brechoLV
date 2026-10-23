package Backend.BrechoAPI.dto.response;































public record ShippingResponseDTO(String recipientName, String phone, String zipCode,
                                  String street, String number, String complement,
                                  String district, String city, String uf) {}
