package dev.felippevaz.exceptions;

public class GlobalException extends RuntimeException{

    private final String errorCode;
    private final int httpCode;

    protected GlobalException(String message, String errorCode, int httpCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpCode = httpCode;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " | ErrorCode: " + this.errorCode;
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public int getHttpCode() {
        return this.httpCode;
    }
}
