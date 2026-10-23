package Backend.BrechoAPI.exception;

import lombok.Getter;






















































































@Getter
public class DomainRuleException extends RuntimeException {

    private final ErrorCode code;

    public DomainRuleException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
