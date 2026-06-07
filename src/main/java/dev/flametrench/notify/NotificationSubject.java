// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

/**
 * What the notification is about. {@code kind} is a Flametrench entity type
 * or adopter {@code object_type}; {@code id} is a Flametrench wire id or
 * an opaque adopter string — the primitive does not decode adopter ids.
 */
public record NotificationSubject(String kind, String id) {}
