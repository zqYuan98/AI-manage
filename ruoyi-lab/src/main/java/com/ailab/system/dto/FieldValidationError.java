package com.ailab.system.dto;

import java.util.Objects;

/** One client-addressable validation error. */
public final class FieldValidationError {
    private final String field;
    private final String message;

    public FieldValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FieldValidationError)) {
            return false;
        }
        FieldValidationError that = (FieldValidationError) other;
        return Objects.equals(field, that.field) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, message);
    }
}
