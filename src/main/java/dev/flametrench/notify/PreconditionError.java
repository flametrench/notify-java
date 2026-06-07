// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

/**
 * Raised when a state-machine transition is invalid (e.g. transitioning out
 * of the terminal {@code dismissed} state).
 */
public class PreconditionError extends NotifyError {
    public PreconditionError(String message) {
        super(message);
    }
}
