// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

import java.time.Instant;
import java.util.Map;

/**
 * A per-recipient notification record (ADR 0022).
 *
 * <p>Instances are returned by {@link InMemoryNotifyStore} operations. Use
 * {@link NotificationInput} as the create input — the store assigns {@code id},
 * {@code createdAt}, {@code stateChangedAt}, and the initial {@code state}.
 */
public record Notification(
        String id,                          // not_<32hex>; UUIDv7 underneath
        String scope,                       // org_<32hex> owning tenancy scope
        String recipientUsrId,             // usr_<32hex>
        String type,                        // adopter-namespaced; opaque
        NotificationSubject subject,
        Map<String, Object> data,           // verbatim record content; ≤ 16 KB
        NotificationState state,
        Instant createdAt,
        Instant stateChangedAt
) {}
