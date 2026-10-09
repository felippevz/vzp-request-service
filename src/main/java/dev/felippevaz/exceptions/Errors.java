package dev.felippevaz.exceptions;

public enum Errors {

    SERVER_INIT_ERROR("Error starting HTTP server", "ERROR_1001", 500),
    VALUE_METHOD_CONTROLLER_ERROR("Error when invoking value method", "ERROR_1002", 500),
    RESPONSE_SEND_ERROR("Unable to establish contact with the requester", "ERROR_1003", 500),
    METHOD_INVOKE_ERROR("Error when trying to invoke the method", "ERROR_1004", 500),
    ID_NOT_FOUND("No @Id found in Entity Repository", "ERROR_1005", 500),
    ENTITY_NOT_FOUND("Entity not found", "ERROR_1006", 404),
    FIELD_COPY_ERROR("Error copying field to entity", "ERROR_1007", 500),
    INTERNAL_SERVER_ERROR("Internal server error", "ERROR_1008", 500),
    PAYLOAD_TOO_LARGE("Request payload too large", "ERROR_1009", 413),
    ROUTE_NOT_FOUND("Route not found", "ERROR_1010", 404),
    BAD_REQUEST("Invalid request", "ERROR_1011", 400),
    UNAUTHORIZED("Unauthorized", "ERROR_1012", 401),
    FORBIDDEN("Forbidden", "ERROR_1013", 403),
    CONFLICT("Conflict", "ERROR_1014", 409),
    DATABASE_ERROR("Database error", "ERROR_1015", 500),
    DATABASE_DRIVER_NOT_FOUND("Database driver not found", "ERROR_1016", 500),
    CLIENT_REQUEST_ERROR("Error sending HTTP request", "ERROR_1017", 500);


    private final String message;
    private final String internalCode;
    private final int httpCode;

    Errors(String message, String internalCode, int httpCode) {
        this.message = message;
        this.internalCode = internalCode;
        this.httpCode = httpCode;
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
