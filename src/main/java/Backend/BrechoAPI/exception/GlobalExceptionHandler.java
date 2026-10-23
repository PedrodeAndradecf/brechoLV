package Backend.BrechoAPI.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.Clock;













@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    
    private static final String UNIQUE_VIOLATION = "23505";

    private final Clock clock;

    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("dados inválidos");
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleParamValidation(ConstraintViolationException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, ex.getMessage(), request);
    }

    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
                                                    HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request);
    }

    
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex,
                                                    HttpServletRequest request) {
        
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request);
    }

    

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "esta peça foi alterada por outra operação; recarregue e tente de novo", request);
    }

    

































    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                     HttpServletRequest request) {
        if (UNIQUE_VIOLATION.equals(sqlStateDe(ex))) {
            return build(HttpStatus.CONFLICT, ErrorCode.UNIQUENESS_VIOLATION,
                    "operação viola uma restrição de unicidade", request);
        }
        log.error("violação de integridade sem SQLSTATE 23505 alcançou a rede de segurança", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.PERSISTENCE_FAILURE,
                "não foi possível concluir a operação; tente de novo em instantes", request);
    }

    






































    @ExceptionHandler(PersistenceIntegrityException.class)
    public ResponseEntity<ApiError> handlePersistenceIntegrity(PersistenceIntegrityException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.PERSISTENCE_FAILURE,
                ex.getMessage(), request);
    }

    
    








    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleDomainRule(RuntimeException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.DOMAIN_RULE_VIOLATION, ex.getMessage(), request);
    }

    


    @ExceptionHandler(DomainRuleException.class)
    public ResponseEntity<ApiError> handleDomainRuleWithCode(DomainRuleException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage(), request);
    }

    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, ex.getMessage(), request);
    }

    
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiError> handleStorage(StorageException ex,
                                                   HttpServletRequest request) {
        
        
        boolean culpaDoCliente = ex.getCause() == null;
        HttpStatus status = culpaDoCliente ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCode code = culpaDoCliente ? ErrorCode.INVALID_UPLOAD : ErrorCode.STORAGE_FAILURE;
        return build(status, code, ex.getMessage(), request);
    }

    
    




















    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiError> handlePaymentGateway(PaymentGatewayException ex,
                                                          HttpServletRequest request) {
        if (ex.isOutcomeUnknown()) {
            return build(HttpStatus.BAD_GATEWAY, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE,
                    PaymentGatewayException.MENSAGEM_DO_DESCONHECIDO, request);
        }
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "o provedor de pagamento não respondeu; tente de novo em instantes"
                : ex.getMessage();
        return build(HttpStatus.BAD_GATEWAY, ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, message, request);
    }

    














    private static String sqlStateDe(Throwable erro) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            if (atual instanceof SQLException sql) {
                return sql.getSQLState();
            }
            if (atual.getCause() == atual) {
                break;  
            }
        }
        return null;
    }

    private ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                ApiError.of(status.value(), status.getReasonPhrase(), code, message,
                        request == null ? "" : request.getRequestURI(), clock.instant()));
    }
}
