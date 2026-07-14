package com.thamescape.cobbleverse.core.util.error;

/** Thrown when a value fails a validation rule. Typically aggregated into a startup report. */
public class ValidationException extends CoreException {

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
