# Quickstart: register, remove, and read what is in force

```bash
./gradlew :deployable:bootRun --args='--configRootLocation=/tmp/charter-root'   # loopback; ADR-0018 D9 token
T="Authorization: Bearer $CHARTER_WORKTREE_TOKEN"
B=localhost:8080/api/v1
```

## Register a pack (PR A)

```bash
# A project pack authored as a directory: pack.yaml (pack_level: project), directives/, …
tar -czf demo.tgz -C ./fixtures demo-pack        # one leading directory is stripped
curl -sS -i -H "$T" -X PUT -H 'Content-Type: application/gzip' -H 'If-None-Match: *' \
  --data-binary @demo.tgz $B/packs/demo                # 202, Location: /api/v1/pack-registrations/<id>; 412 if demo exists
curl -sS -H "$T" $B/pack-registrations/<id>            # pending → running → succeeded {result: PackRegistration, notices}
                                                       # or refused {problem}; poll until it ends (A5)

# The same archive again: succeeded, outcome "unchanged". A pack_version ever stored with other content: refused 409 pack_version_conflict.
# A new version without directive B: succeeded "replaced", and B is gone.
(cd fixtures/demo-pack && zip -qr ../../demo.zip .)
curl -sS -i -H "$T" -X PUT -H 'Content-Type: application/zip' -H 'If-Match: "<revision>"' \
  --data-binary @demo.zip $B/packs/demo                                   # 202; stale If-Match → 412 at once
curl -sS -H "$T" $B/packs/demo/artifacts/directive/B -i                  # 404 artifact_not_found

# Refused in the status: a built-in pack.yaml → 409; ../ or a symlink in the archive → 422; a lock it breaks in any
# closure it joins → 422 naming the packs. Answered at once: over 32 MiB → 413; queue full → 503 Retry-After
curl -sS -i -H "$T" -X PUT -H 'Content-Type: text/plain' --data-binary @demo.tgz $B/packs/demo   # 415
```

## Register by source (PR A)

The sources are configured in `application.yaml` or with `--charter.pack-sources.*`:

```yaml
charter:
  pack-sources:
    https: { allowed-hosts: [ packs.example.org ] }
    git: { allowed-hosts: [ github.com ] }          # needs git >= 2.50.1 on PATH; empty by default
    filesystem: { root: /srv/charter-sources }
```

```bash
curl -sS -i -H "$T" -H 'Content-Type: application/json' -X POST $B/packs \
  -d '{"kind":"git","packId":"acme","url":"https://github.com/acme/acme-packs","ref":"refs/tags/v1.2.0"}'  # 202; status result.source.commit
curl -sS -i -H "$T" -H 'Content-Type: application/json' -X POST $B/packs \
  -d '{"kind":"https_archive","packId":"demo","url":"https://packs.example.org/demo-1.2.0.tgz","sha256":"<hex>"}'
curl -sS -i -H "$T" -H 'Content-Type: application/json' -X POST $B/packs \
  -d '{"kind":"filesystem","packId":"demo","path":"demo-pack"}'
# A host not on the allowlist, a ref like "--upload-pack=x", or a private address → refused 422 pack_source_refused
# Registration disabled, or git missing / older than 2.50.1 → 503 pack_source_unavailable
```

## Remove (PR A)

```bash
curl -sS -i -H "$T" -H 'If-Match: "<revision>"' -X DELETE $B/packs/demo/artifacts/tactic/T            # 204
curl -sS -i -H "$T" -H 'If-Match: "<revision>"' -X DELETE \
  "$B/packs/demo/relationships?from=directive:D&to=tactic:T"                                           # 204
# A tactic another pack references as demo::tactic:T (or names in a preset) → 409 artifact_referenced, naming the
# referring packs and artifacts
```

## Read what is in force (PR B, PR C)

```bash
A=$(curl -sS -H "$T" -H 'Content-Type: application/json' -X POST $B/activations \
  -d '{"schemaVersion":2,"selected":{"directive":["D"],"tactic":["T"],"mission_type":["software-dev"]}}' \
  | jq -r .id)
curl -sS -H "$T" "$B/activations/$A/artifacts?state=active" | jq '.items[] | {artifactId, reason, packId, level}'
curl -sS -H "$T" $B/activations/$A/skipped | jq .report        # staleIds, denyNotApplied, overridden, …
curl -sS -H "$T" $B/activations/$A/graph | jq .layerOverrides   # PR C
curl -sS -H "$T" "$B/activations/$A/mission-types/software-dev/actions/implement/context?depth=1"   # PR C
E=$(curl -sS -D - -o /dev/null -H "$T" $B/activations/$A/skipped | awk -F': ' 'tolower($1)=="etag"{print $2}' | tr -d '\r')
curl -sS -i -H "$T" -H "If-None-Match: $E" $B/activations/$A/skipped                      # 304
```

## Tests

```bash
./gradlew :packs:check :activations:check                       # module scope while a WP is in flight
./gradlew :packs:test --tests '*Archive*' --tests '*GitSource*'  # the crafted-archive corpus and the git runner
./gradlew :deployable:test --tests '*CharterContractSuite*'     # the @contract scenarios
./gradlew :deployable:test --tests '*HexagonalArchitectureTest*' :packs:test --tests '*NetworkCodeBoundaryTest*'
./gradlew :activations:timingBudgets                            # NFR-003 wall time, report-only (the gate is EffectiveReadOperationBudgetTest in check)
./gradlew :packs:gitHostTest                                     # the real-git tests; needs git >= 2.50.1 (CI job git-source)
./gradlew verifyAll                                             # once per PR, at closeout
```
