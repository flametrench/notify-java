// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flametrench.ids.Id;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reference in-memory NotifyStore. Behaviorally spec-conformant for ADR 0022 Option 2:
 * recipient-scoped operations enforce existence non-disclosure (foreign caller →
 * {@code NotFoundError}, indistinguishable from non-existent); ownership check precedes
 * terminal-state check so the dismissed state is never leaked to a foreign caller.
 */
public class InMemoryNotifyStore {

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[a-z0-9._-]{1,64}$");
    private static final int DATA_MAX_BYTES = 16 * 1024; // 16 KB
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Notification> notifications = new LinkedHashMap<>();
    private final Clock clock;

    public InMemoryNotifyStore() {
        this(Clock.systemUTC());
    }

    public InMemoryNotifyStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * Create a notification. Validates input, assigns id/timestamps/state=unread,
     * and stores durably before returning.
     *
     * @return the {@code not_<32hex>} id
     * @throws InvalidFormatError if input fails shape validation
     */
    public String createNotification(NotificationInput input) {
        validate(input);

        String id = Id.generate("not");
        Instant now = Instant.now(clock);

        Notification notification = new Notification(
                id,
                input.scope(),
                input.recipientUsrId(),
                input.type(),
                input.subject(),
                input.data(),
                NotificationState.unread,
                now,
                now
        );

        notifications.put(id, notification);
        return id;
    }

    /**
     * Fetch a notification by id, optionally scoped to a recipient.
     *
     * <p>When {@code recipientUsrId} is non-null the lookup is recipient-scoped:
     * a foreign {@code recipientUsrId} returns the same {@link NotFoundError} as a
     * genuinely non-existent id (existence non-disclosure, ADR 0022 Option 2).
     *
     * @param recipientUsrId authenticated caller; {@code null} disables scoping
     * @throws NotFoundError if not found or if the caller is not the recipient
     */
    public Notification getNotification(String recipientUsrId, String id) {
        Notification n = notifications.get(id);
        if (n == null) throw new NotFoundError(id);
        if (recipientUsrId != null && !recipientUsrId.equals(n.recipientUsrId())) {
            throw new NotFoundError(id);
        }
        return n;
    }

    /**
     * Transition to {@code read} from {@code unread} or {@code read} (idempotent).
     *
     * @param recipientUsrId authenticated caller; {@code null} disables scoping
     * @throws NotFoundError     if not found or if the caller is not the recipient
     * @throws PreconditionError if the notification is {@code dismissed}
     */
    public void markRead(String recipientUsrId, String id) {
        transition(recipientUsrId, id, NotificationState.read);
    }

    /**
     * Transition to {@code unread} from {@code read} or {@code unread} (idempotent).
     *
     * @param recipientUsrId authenticated caller; {@code null} disables scoping
     * @throws NotFoundError     if not found or if the caller is not the recipient
     * @throws PreconditionError if the notification is {@code dismissed}
     */
    public void markUnread(String recipientUsrId, String id) {
        transition(recipientUsrId, id, NotificationState.unread);
    }

    /**
     * Terminate a notification as {@code dismissed}. Terminal — accepts no further transitions.
     *
     * <p>Ownership is checked BEFORE terminal-state: a foreign caller gets
     * {@link NotFoundError}, not {@link PreconditionError}, even if the notification
     * is already dismissed (existence non-disclosure, ADR 0022 §Errors).
     *
     * @param recipientUsrId authenticated caller; {@code null} disables scoping
     * @throws NotFoundError     if not found or if the caller is not the recipient
     * @throws PreconditionError if already {@code dismissed} (recipient callers only)
     */
    public void dismiss(String recipientUsrId, String id) {
        transition(recipientUsrId, id, NotificationState.dismissed);
    }

    /**
     * Count unread notifications for a recipient.
     *
     * @param recipientUsrId the recipient's usr_id
     * @return count of notifications in {@code unread} state owned by this recipient
     */
    public long countUnread(String recipientUsrId) {
        return notifications.values().stream()
                .filter(n -> recipientUsrId.equals(n.recipientUsrId())
                        && n.state() == NotificationState.unread)
                .count();
    }

    private void transition(String recipientUsrId, String id, NotificationState target) {
        Notification n = notifications.get(id);
        if (n == null) throw new NotFoundError(id);
        // Ownership check BEFORE terminal-state (non-disclosure precedence: ADR 0022 §Errors)
        if (recipientUsrId != null && !recipientUsrId.equals(n.recipientUsrId())) {
            throw new NotFoundError(id);
        }
        if (n.state() == NotificationState.dismissed) {
            throw new PreconditionError(
                    "Notification " + id + " is dismissed and accepts no further transitions");
        }
        notifications.put(id, new Notification(
                n.id(), n.scope(), n.recipientUsrId(), n.type(),
                n.subject(), n.data(), target, n.createdAt(), Instant.now(clock)
        ));
    }

    private void validate(NotificationInput input) {
        if (!Id.isValid(input.scope(), "org")) {
            throw new InvalidFormatError("scope");
        }
        if (!Id.isValid(input.recipientUsrId(), "usr")) {
            throw new InvalidFormatError("recipient_usr_id");
        }
        if (input.type() == null || !TYPE_PATTERN.matcher(input.type()).matches()) {
            throw new InvalidFormatError("type");
        }
        if (input.subject() == null
                || input.subject().kind() == null || input.subject().kind().isBlank()
                || input.subject().id() == null || input.subject().id().isBlank()) {
            throw new InvalidFormatError("subject");
        }
        if (input.data() == null) {
            throw new InvalidFormatError("data");
        }
        try {
            byte[] serialized = MAPPER.writeValueAsBytes(input.data());
            if (serialized.length > DATA_MAX_BYTES) {
                throw new InvalidFormatError("data");
            }
        } catch (JsonProcessingException e) {
            throw new InvalidFormatError("data");
        }
    }
}
