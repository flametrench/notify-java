// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

/** ADR 0022 notification state machine. {@code dismissed} is terminal. */
public enum NotificationState {
    unread,
    read,
    dismissed
}
