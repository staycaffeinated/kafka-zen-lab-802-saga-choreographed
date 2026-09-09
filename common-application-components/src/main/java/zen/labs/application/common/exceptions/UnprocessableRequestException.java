package zen.labs.application.common.exceptions;

public class UnprocessableRequestException extends ApplicationException {
    public UnprocessableRequestException(String message) {
        super(message);
    }

    public UnprocessableRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
