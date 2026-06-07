// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

/**
 * Raised when a notification is not found, or when a cross-recipient access is
 * attempted (indistinguishable per ADR 0022 non-inference rule).
 */
public class NotFoundError extends NotifyError {
    public NotFoundError(String id) {
        super("Notification not found: " + id);
    }
}
