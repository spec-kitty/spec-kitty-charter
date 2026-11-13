# Quickstart: verify slice F

Run every command from the repository root, after rebasing onto slice C. Run Gradle in the foreground, one run at a
time.

```bash
./gradlew :kernel:test :packs:test :missions:test --max-workers=1          # model, bridge, load rules
./gradlew :deployable:test --max-workers=1                                 # contract scenarios, parity, ArchUnit
./gradlew verifyAll --max-workers=1                                        # the whole gate, with the round trip
python3 scripts/docs/check_docs.py all
```

Check by hand, with the service running:

1. `GET /offering/kinds` answers `200`.
   - `kinds` has no `mission_step_contract`.
   - `relations` has no `impacts`, `replaces` or `delegates_to`.
   - Every relation lists its endpoint pairs.
   - A second request with `If-None-Match` set to the `ETag` answers `304`.
2. `PUT /packs/{p}/relationships` with `procedure:x -requires-> action:t/s` answers `422 validation_failed`, with the
   violation `relationship_endpoint_not_allowed` (#350).
3. `GET /packs/{p}/artifacts/mission_type/{t}/steps/{s}` returns `substeps` in the order they were authored, each
   delegation candidate linked to its artifact.
4. Any `…/mission_step_contract/…` path answers as an unknown kind or an unknown path.
5. Re-run the census against the applied open-packs drafts. It reports 0 refused edges. The census script and its
   CSV are in `research/`.
