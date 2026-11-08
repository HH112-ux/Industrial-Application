# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-08

First production-ready release after the S0–S8 refactor of the discrete-manufacturing MES.
Focus: reliability, concurrency correctness, traceability, identity/data-scope,
observability and a read-only AI sidecar. **AI is off by default** — set `AI_ENABLED=true`
to enable; `AI_PROVIDER=fake` is the offline default, `external` requires `AI_MODEL_URL`
and `AI_API_KEY`.

### Added

- **Device access reliability (S1)**: persistent MQTT session with automatic reconnect,
  re-subscribe and exponential backoff; `last_seen_at` based online/offline state instead of
  status-string guessing.
- **Device credentials (S1)**: `key_id` + HMAC-SHA256 request signing, AES-256-GCM
  encrypted secret storage, issue/revoke lifecycle.
- **Telemetry store (S2)**: `eq_telemetry` table with 90-day retention, range query with
  hard cap, `ingest_key` unique index for idempotent device uploads.
- **Work order consistency (S3)**: optimistic locking on status transition,
  `wo_status_history` audit trail, strict report quantity validation
  (`qualified + defect == qty`, cumulative ≤ order qty), idempotent report submission keyed
  by per-order idempotency key with unique-index fallback.
- **Process & BOM versioning (S4)**: `pr_process` rows carry version; single-level operations
  with sequence and standard hours; BOM scoped to process version; published versions frozen
  and copied into a new draft on change; kit check is locked to the work order's process
  version.
- **Finite-capacity scheduling (S5)**: greedy schedule bound by device availability and
  operation standard hours; dispatch requires an explicit published process; missing
  standard hours rejects scheduling (no silent fallback).
- **Identity & data scope (S6)**: per-request DB check of `enabled` and `token_version` so
  disable/reset takes effect on the next request; deny-by-default `workshop_scope` for
  non-admins (no scope → no data); admin workshop assignment endpoint; single-instance login
  rate limiter behind a replaceable port; audit log query by operator/path/date.
- **Observability (S7)**: Micrometer metrics for device online rate, telemetry ingest,
  report count, work-order takt time and kit rate; `traceId` in request logs and response
  header `X-Request-Id`.
- **AI sidecar (S8)**: three read-only suggestion endpoints — device-issue attribution,
  natural-language report draft, Text-to-SQL over 5 read-only views; `ai.enabled=false`
  default returns HTTP 503 + business code 503; offline deterministic `FakeAiAssistant` for
  CI; `OpenAiCompatibleAssistant` adapter for real models; `ReadOnlySqlGuard` (single
  SELECT, whitelisted views, forced `LIMIT 200`, 3s timeout); Text-to-SQL ADMIN-only; audit
  stores only SHA-256 question digest and suggestion summary.
- **CI gates**: commitlint on commit messages, gitleaks on changed commits, backend tests,
  frontend build.
- **One-command verification**: `scripts/verify.sh` starts the compose stack and drives the
  full order lifecycle (login → plan → dispatch → start → report → QC → history → telemetry
  history → metrics).

### Changed

- Device online state derived from `last_seen_at` + threshold rather than the `status` field.
- Work order start requires kit check; report writes history; status transitions are
  optimistic-locked.
- Dispatch requires explicit published process; process version is written on the work
  order.
- Audit logging now queryable by operator/path/date.
- `docker-compose.yml` host ports made overridable via `*_PORT` env vars
  (defaults unchanged: 3306/6379/1883/8080/8081).

### Fixed

- MQTT reconnect no longer drops subscriptions on reconnect and no longer gives up after a
  fixed retry count.
- Concurrent work-order start no longer double-transitions; one request wins with business
  code 409 on conflict.
- Report quantity can no longer be negative or exceed the remaining order quantity.
- Kit check no longer drifts when BOM is revised after the work order is dispatched.
- `sys_user.workshop_scope` for non-admins reset to NULL in V13 (previously backfilled to a
  single workshop without authority); scope must be assigned explicitly.

### Known limitations

- AI uses the offline `fake` provider by default; the `external` OpenAI-compatible adapter
  is wired but has not been validated against a live model endpoint.
- Login rate limiter is a single-instance local implementation; multi-instance deployments
  must replace it with a shared store (no Redis dependency added).
- AI only produces suggestions — it never mutates work-order status, report quantity or
  device state; natural-language reporting produces a draft that requires human confirmation.
- Telemetry is stored in MySQL; no dedicated time-series backend.
