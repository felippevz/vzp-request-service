package dev.felippevaz.exceptions;

public class GlobalException extends RuntimeException{

    private final String errorCode;

    protected GlobalException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " | ErrorCode: " + this.errorCode;
    }

    public String getErrorCode() {
        return this.errorCode;
    }
}
