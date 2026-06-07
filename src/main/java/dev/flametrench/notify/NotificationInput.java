// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

import java.util.Map;

/** Input for {@link InMemoryNotifyStore#createNotification}. */
public record NotificationInput(
        String scope,                  // org_<32hex>
        String recipientUsrId,        // usr_<32hex>
        String type,                   // adopter-namespaced, opaque
        NotificationSubject subject,
        Map<String, Object> data       // free-form; ≤ 16 KB
) {}
