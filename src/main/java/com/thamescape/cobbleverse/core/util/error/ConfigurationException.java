package com.thamescape.cobbleverse.core.util.error;

/** Thrown when configuration is missing, malformed, or fails validation. */
public class ConfigurationException extends CoreException {

    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
