package dev.felippevaz.exceptions;

public enum Errors {

    SERVER_INIT_ERROR("Error starting Https server", "ERROR_1001"),
    VALUE_METHOD_CONTROLLER_ERROR("Error when invoking value method", "ERROR_1002"),
    RESPONSE_SEND_ERROR("Unable to establish contact with the requester", "ERROR_1003"),
    METHOD_INVOKE_ERROR("Error when trying to invoke the method", "ERROR_1004"),
    ID_NOT_FOUND("Any @Id found in Entity Repository", "ERROR_1005"),

    ROUTE_NOT_FOUND("Route not found", 404);


    private final String message;
    private final String internalCode;
    private final int httpCode;

    Errors(String message, int code) {
        this.message = message;
        this.internalCode = "";
        this.httpCode = code;
    }

    Errors(String message, String code) {
        this.message = message;
        this.internalCode = code;
        this.httpCode = 0;
    }

    public int getHttpCode() {
        return this.httpCode;
    }

    public String getMessage() {
        return this.message;
    }

    public String getInternalCode() {
        return this.internalCode;
    }
}
