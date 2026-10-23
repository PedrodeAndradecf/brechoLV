package Backend.BrechoAPI.exception;






public class DuplicateResourceException extends ConflictException {

    public DuplicateResourceException(ErrorCode code, String message) {
        super(code, message);
    }
}
