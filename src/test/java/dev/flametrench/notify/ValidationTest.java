// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryNotifyStore} validation and lifecycle enforcement (ADR 0022).
 * Covers every {@link InvalidFormatError} field path and {@link PreconditionError} on
 * post-dismissed transitions.
 */
class ValidationTest {

    private static final String VALID_SCOPE       = "org_0190f2a81b3c7abc8123000000000001";
    private static final String VALID_RECIPIENT   = "usr_0190f2a81b3c7abc8123000000000002";
    private static final String VALID_TYPE        = "account.invite";
    private static final NotificationSubject VALID_SUBJECT =
            new NotificationSubject("user", "usr_0190f2a81b3c7abc8123000000000003");
    private static final Map<String, Object> VALID_DATA = Map.of("foo", "bar");

    private NotificationInput valid() {
        return new NotificationInput(
                VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT, VALID_DATA);
    }

    private NotificationInput with(String scope, String recipient, String type,
                                   NotificationSubject subject, Map<String, Object> data) {
        return new NotificationInput(scope, recipient, type, subject, data);
    }

    // ── scope ─────────────────────────────────────────────────────────────────

    @Test
    void scope_null_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(null, VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT, VALID_DATA)));
        assertEquals("scope", ex.field());
    }

    @Test
    void scope_wrong_prefix_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with("usr_0190f2a81b3c7abc8123000000000001",
                                VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT, VALID_DATA)));
        assertEquals("scope", ex.field());
    }

    @Test
    void scope_bare_uuid_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with("0190f2a81b3c7abc8123000000000001",
                                VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT, VALID_DATA)));
        assertEquals("scope", ex.field());
    }

    // ── recipient_usr_id ──────────────────────────────────────────────────────

    @Test
    void recipient_null_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, null, VALID_TYPE, VALID_SUBJECT, VALID_DATA)));
        assertEquals("recipient_usr_id", ex.field());
    }

    @Test
    void recipient_wrong_prefix_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, "org_0190f2a81b3c7abc8123000000000002",
                                VALID_TYPE, VALID_SUBJECT, VALID_DATA)));
        assertEquals("recipient_usr_id", ex.field());
    }

    // ── type ──────────────────────────────────────────────────────────────────

    @Test
    void type_null_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, null, VALID_SUBJECT, VALID_DATA)));
        assertEquals("type", ex.field());
    }

    @Test
    void type_uppercase_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, "Account.Invite",
                                VALID_SUBJECT, VALID_DATA)));
        assertEquals("type", ex.field());
    }

    @Test
    void type_over_64_chars_throws() {
        String longType = "a".repeat(65);
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, longType, VALID_SUBJECT, VALID_DATA)));
        assertEquals("type", ex.field());
    }

    @Test
    void type_exactly_64_chars_accepted() {
        String maxType = "a".repeat(64);
        assertDoesNotThrow(() -> new InMemoryNotifyStore().createNotification(
                with(VALID_SCOPE, VALID_RECIPIENT, maxType, VALID_SUBJECT, VALID_DATA)));
    }

    // ── subject ───────────────────────────────────────────────────────────────

    @Test
    void subject_null_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE, null, VALID_DATA)));
        assertEquals("subject", ex.field());
    }

    @Test
    void subject_null_kind_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE,
                                new NotificationSubject(null, "usr_0190f2a81b3c7abc8123000000000003"),
                                VALID_DATA)));
        assertEquals("subject", ex.field());
    }

    @Test
    void subject_blank_kind_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE,
                                new NotificationSubject("  ", "usr_0190f2a81b3c7abc8123000000000003"),
                                VALID_DATA)));
        assertEquals("subject", ex.field());
    }

    @Test
    void subject_null_id_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE,
                                new NotificationSubject("user", null),
                                VALID_DATA)));
        assertEquals("subject", ex.field());
    }

    // ── data ──────────────────────────────────────────────────────────────────

    @Test
    void data_null_throws() {
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT, null)));
        assertEquals("data", ex.field());
    }

    @Test
    void data_empty_map_accepted() {
        assertDoesNotThrow(() -> new InMemoryNotifyStore().createNotification(
                with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT,
                        Collections.emptyMap())));
    }

    @Test
    void data_over_16kb_throws() {
        // Build a map whose JSON serialization exceeds 16 KB
        Map<String, Object> big = new HashMap<>();
        big.put("payload", "x".repeat(17_000));
        var ex = assertThrows(InvalidFormatError.class,
                () -> new InMemoryNotifyStore().createNotification(
                        with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT, big)));
        assertEquals("data", ex.field());
    }

    @Test
    void data_exactly_at_16kb_accepted() {
        // 16384 bytes in UTF-8. Build a string that keeps serialized JSON just at/below the limit.
        // Key "k" + quotes + colon + string value + quotes + braces = overhead ~8 bytes
        Map<String, Object> borderline = new HashMap<>();
        borderline.put("k", "x".repeat(16_370));
        assertDoesNotThrow(() -> new InMemoryNotifyStore().createNotification(
                with(VALID_SCOPE, VALID_RECIPIENT, VALID_TYPE, VALID_SUBJECT, borderline)));
    }

    // ── lifecycle: dismissed is terminal ──────────────────────────────────────

    private String createAndDismiss(InMemoryNotifyStore store) {
        String id = store.createNotification(valid());
        store.dismiss(id);
        return id;
    }

    @Test
    void markRead_after_dismiss_throws() {
        InMemoryNotifyStore store = new InMemoryNotifyStore();
        String id = createAndDismiss(store);
        assertThrows(PreconditionError.class, () -> store.markRead(id));
    }

    @Test
    void markUnread_after_dismiss_throws() {
        InMemoryNotifyStore store = new InMemoryNotifyStore();
        String id = createAndDismiss(store);
        assertThrows(PreconditionError.class, () -> store.markUnread(id));
    }

    @Test
    void dismiss_after_dismiss_throws() {
        InMemoryNotifyStore store = new InMemoryNotifyStore();
        String id = createAndDismiss(store);
        assertThrows(PreconditionError.class, () -> store.dismiss(id));
    }

    // ── happy-path lifecycle ──────────────────────────────────────────────────

    @Test
    void create_sets_unread_state() {
        InMemoryNotifyStore store = new InMemoryNotifyStore();
        String id = store.createNotification(valid());
        assertEquals(NotificationState.unread, store.getNotification(id).state());
    }

    @Test
    void markRead_transitions_to_read() {
        InMemoryNotifyStore store = new InMemoryNotifyStore();
        String id = store.createNotification(valid());
        store.markRead(id);
        assertEquals(NotificationState.read, store.getNotification(id).state());
    }

    @Test
    void markUnread_from_read_returns_to_unread() {
        InMemoryNotifyStore store = new InMemoryNotifyStore();
        String id = store.createNotification(valid());
        store.markRead(id);
        store.markUnread(id);
        assertEquals(NotificationState.unread, store.getNotification(id).state());
    }

    @Test
    void getNotification_unknown_id_throws() {
        assertThrows(NotFoundError.class,
                () -> new InMemoryNotifyStore().getNotification("not_000000000000000000000000000000ff"));
    }
}
