package dev.felippevaz.exceptions;

public class ApplicationException extends GlobalException {

    public ApplicationException(Errors error, Throwable cause) {
        super(error.getMessage(), error.getInternalCode(), cause);
    }
}
