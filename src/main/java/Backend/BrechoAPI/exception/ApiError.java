package Backend.BrechoAPI.exception;

import java.time.Instant;

















public record ApiError(
        String timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path
) {
    public static ApiError of(int status, String error, ErrorCode code, String message,
                              String path, Instant now) {
        return new ApiError(now.toString(), status, error, code,
                message == null ? "" : message, path);
    }
}
