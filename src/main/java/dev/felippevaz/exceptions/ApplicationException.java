package dev.felippevaz.exceptions;

public class ApplicationException extends GlobalException {

    private final Errors error;

    public ApplicationException(Errors error, Throwable cause) {
        super(error.getMessage(), error.getInternalCode(), error.getHttpCode(), cause);
        this.error = error;
    }

    public Errors getError() {
        return this.error;
    }
}
