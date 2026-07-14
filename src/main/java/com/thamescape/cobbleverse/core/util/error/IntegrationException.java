package com.thamescape.cobbleverse.core.util.error;

/** Thrown when a third-party integration fails to initialize or behaves unexpectedly. */
public class IntegrationException extends CoreException {

    public IntegrationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public IntegrationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
