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
 * Reference in-memory NotifyStore. Behaviorally spec-conformant for ADR 0022:
 * notifications created as {@code unread}; lifecycle machine enforced
 * ({@code dismissed} is terminal); shape validation on create.
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
     * Create a notification. Validates input, assigns id/timestamps/state,
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
     * Fetch a notification by id.
     *
     * @throws NotFoundError if not found
     */
    public Notification getNotification(String id) {
        Notification n = notifications.get(id);
        if (n == null) throw new NotFoundError(id);
        return n;
    }

    /**
     * Transition to {@code read} from {@code unread} or {@code read} (idempotent).
     *
     * @throws NotFoundError      if not found
     * @throws PreconditionError  if the notification is {@code dismissed}
     */
    public void markRead(String id) {
        transition(id, NotificationState.read);
    }

    /**
     * Transition to {@code unread} from {@code read} or {@code unread} (idempotent).
     *
     * @throws NotFoundError      if not found
     * @throws PreconditionError  if the notification is {@code dismissed}
     */
    public void markUnread(String id) {
        transition(id, NotificationState.unread);
    }

    /**
     * Terminate a notification as {@code dismissed}. Terminal — accepts no further transitions.
     *
     * @throws NotFoundError      if not found
     * @throws PreconditionError  if already {@code dismissed}
     */
    public void dismiss(String id) {
        transition(id, NotificationState.dismissed);
    }

    private void transition(String id, NotificationState target) {
        Notification n = getNotification(id);
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
