package com.ailab.system.exception;

import com.ailab.system.dto.FieldValidationError;
import java.util.ArrayList;
import java.util.List;

/** Business validation failure that preserves field-addressable errors for the UI. */
public class LabValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final List<FieldValidationError> fieldErrors;

    public LabValidationException(List<FieldValidationError> fieldErrors) {
        super("Business validation failed");
        this.fieldErrors = fieldErrors == null
                ? new ArrayList<FieldValidationError>() : new ArrayList<FieldValidationError>(fieldErrors);
    }

    public List<FieldValidationError> getFieldErrors() {
        return new ArrayList<FieldValidationError>(fieldErrors);
    }
}
