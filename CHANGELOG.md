# Changelog

## [v0.4.0] — 2026-06-07

### Added
- Initial release: notifications primitive implementing [ADR 0022](https://github.com/flametrench/spec/blob/main/decisions/0022-notify-primitive.md).
- `Notification` record: `id` (not_<32hex>/UUIDv7), `scope` (org), `recipient_usr_id`, `type` (opaque), `subject`, `data`, `state` (unread/read/dismissed), `created_at`, `state_changed_at`.
- `InMemoryNotifyStore`: `createNotification`, `getNotification`, `markRead`, `markUnread`, `dismiss` with lifecycle enforcement (`dismissed` is terminal).
- `InvalidFormatError(field)`, `PreconditionError`, `NotFoundError`.
- Conformance test harness wired to `notifications/lifecycle-shape.json` (spec@aaf66ae, 4 tests, superset matching).
