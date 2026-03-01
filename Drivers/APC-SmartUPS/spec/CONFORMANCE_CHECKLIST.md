# APC SmartUPS Clean-Room Conformance Checklist

Use this checklist to validate any fresh implementation against the behavior contract in `MODULE_SPEC.md` and vectors in `TEST_VECTORS.json`.

## A) Interface and Environment
- [ ] Implementation language/runtime is Hubitat-compatible Groovy.
- [ ] All required commands are exposed.
- [ ] Required preferences are supported with expected ranges/defaults.
- [ ] Required attributes are emitted with compatible naming and types.

## B) Queue/In-Flight Arbiter
- [ ] Exactly one command can be in-flight at any time.
- [ ] Pending commands are FIFO.
- [ ] Reconnoiter dedupe prevents multiple queued/in-flight recon sessions.
- [ ] Queue pump reschedules while active session is in progress.

## C) Session Lifecycle Correctness
- [ ] Each session has unique token.
- [ ] Session marks `Pending` at start.
- [ ] Session processing runs at most once per token.
- [ ] Finalization runs at most once per token.
- [ ] Finalization always clears in-flight and pumps queue.

## D) Result Semantics
- [ ] E000/E001 produce `Success`.
- [ ] E1xx produce `Failure` with mapped explanation.
- [ ] Non-E-code telemetry lines never produce command failure.
- [ ] Missing E-code yields `No Response`.
- [ ] `Complete` fallback only occurs if result remains `Pending`.
- [ ] No duplicate terminal result events for one token.

## E) Race/Recovery Robustness
- [ ] Stale timer callbacks are ignored by token mismatch.
- [ ] Duplicate close/status callbacks do not replay processing.
- [ ] In-flight stale clear uses age thresholds (not immediate disconnect clear).
- [ ] Recovery path returns to clean `Disconnected` baseline.

## F) Parse/Normalization
- [ ] Inbound parser normalizes CR/LF/NUL correctly.
- [ ] Partial line carry across chunks works.
- [ ] Carry size is bounded.

## G) Overlap Scenario (Critical)
- [ ] Canonical overlap passes: alarm → refresh → alarm during refresh → alarm after refresh.
- [ ] Alarm queued during refresh is executed (not lost, not silently dropped).
- [ ] Post-run queue depth returns to zero.
- [ ] No ghost in-flight remains after completion.

## H) Operational Safety
- [ ] Control commands are blocked while UPS control disabled.
- [ ] Reconnoiter remains allowed when control disabled.
- [ ] Follow-up refresh delays for self-test/reboot/UPS on/off are honored.
- [ ] Low battery handling is edge-triggered and idempotent.

## I) Evidence Required for Sign-off
- [ ] Test harness output proving pass of all vectors in `TEST_VECTORS.json`.
- [ ] Event trace excerpt for canonical overlap scenario.
- [ ] Log excerpt showing no duplicate terminal events per token.
- [ ] Overnight stability sample showing no stale in-flight accumulation.
