package Backend.BrechoAPI.exception;



























public enum ErrorCode {

    
    
    VALIDATION_ERROR,
    
    INVALID_UPLOAD,

    
    
    UNAUTHENTICATED,
    

    INVALID_CREDENTIALS,

    
    
    ACCESS_DENIED,

    
    PRODUCT_NOT_FOUND,
    PHOTO_NOT_FOUND,
    CATEGORY_NOT_FOUND,
    SELLER_NOT_FOUND,
    

    ADDRESS_NOT_FOUND,
    


    CART_NOT_FOUND,
    


    ORDER_NOT_FOUND,

    
    CATEGORY_SLUG_ALREADY_EXISTS,
    


    EMAIL_ALREADY_REGISTERED,
    

    CONCURRENT_MODIFICATION,
    

    UNIQUENESS_VIOLATION,
    








    PRODUCT_NOT_AVAILABLE,
    














    ORDER_ALREADY_OPEN,

    
    


    DOMAIN_RULE_VIOLATION,
    




    CART_EMPTY,
    


    SHIPPING_UNAVAILABLE_FOR_UF,

    
    
    STORAGE_FAILURE,
    




























    PERSISTENCE_FAILURE,

    
    



    PAYMENT_GATEWAY_UNAVAILABLE
}
