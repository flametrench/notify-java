// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

/**
 * Raised when a supplied value fails shape validation.
 * The {@code field} names the offending input field per ADR 0022 §Errors.
 */
public class InvalidFormatError extends NotifyError {

    private final String field;

    public InvalidFormatError(String field) {
        super("Invalid format for field: " + field);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
