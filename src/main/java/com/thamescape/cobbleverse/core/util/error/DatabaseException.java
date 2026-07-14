package com.thamescape.cobbleverse.core.util.error;

/** Thrown when a database operation or migration fails. */
public class DatabaseException extends CoreException {

    public DatabaseException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DatabaseException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
