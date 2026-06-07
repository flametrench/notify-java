// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

public class NotifyError extends RuntimeException {
    public NotifyError(String message) {
        super(message);
    }
}
