# Clean-Room Handoff Instructions

Provide ONLY the files in this `spec/` folder to the implementation agent:
- `MODULE_SPEC.md`
- `TEST_VECTORS.json`
- `CONFORMANCE_CHECKLIST.md`

## Rules for the implementation agent
1. Do not inspect existing APC driver implementation.
2. Implement solely from the contract and vectors.
3. Build a mocked Hubitat harness to execute vectors deterministically.
4. Produce:
   - implementation source
   - test harness source
   - machine-readable test report (pass/fail per vector)
   - short variance report for any unsupported assumptions

## Verification protocol
1. Run all vectors from `TEST_VECTORS.json`.
2. Mark checklist items in `CONFORMANCE_CHECKLIST.md`.
3. Reject implementation if critical overlap scenario fails.
4. If all pass, compare behavior against live UPS logs in one staged trial.

## Notes
- This process is meant to break context-carrying bugs from legacy code paths.
- If test vectors reveal ambiguous behavior, update `MODULE_SPEC.md` first, then rerun.
