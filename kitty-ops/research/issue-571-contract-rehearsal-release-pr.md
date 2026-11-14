# Op 01M7DF8W7Y8CRQ0Q6FEXD9WD1Z: the contract rehearsal fails on the release pull request (#571)

Profile: java-jenny. Kind: bugfix (test-first-bug-fixing, red first). Size: S. Spec Kitty CLI 4.0.0rc9 (the version `spec-kitty --version` reports; `AGENTS.md` names the project at rc8) at
`/opt/spec-kitty` `d7cedd7` (equal to its `origin/main`).

## Grounding

- **#571.** `scripts/release/rehearse-contract-release.sh` exits 1 when `info.version` of
  `contracts/charter/openapi.yaml` is not a `-SNAPSHOT`. `service.yml`'s `release-rehearsal` job runs every
  `scripts/release/rehearse-*.sh` on each pull request that touches `contracts/`, and the required `CI gate / gate`
  depends on it. Step 1 of "Releasing a contract version" (`contracts/lint/README.md`) sets `info.version` and the top
  CHANGELOG heading to the release in the release pull request, so that pull request goes red.
- **#45.** The first charter contract release (0.1.0 line, now 0.2.0-SNAPSHOT on `main`). No `contract-charter-v*`
  tag exists, so the flaw has not surfaced. This fix unblocks its release pull request; #45 stays open.
- **#196.** The `contract-charter-v*` tag ruleset. A repository setting, untouched here; no tag is pushed.
- **#568** (merged 2026-11-14, `ab83491`). The packs rehearsal reads its version carrier and, when it is already a
  release, skips the refusal steps and rehearses the release as it stands. Reused here: one branch on
  `info.version`, then tag, `contract-release-assets.sh`, and the four-asset check.

Expected: on a release pull request's commit, the rehearsal assembles and checks the release. Actual: it exits 1 at
the `-SNAPSHOT` guard before doing anything.

## Red first

A clone of `main` `b1d73b1` with step 1 applied (`:contracts:assembleContractChangelog`, 129 fragments, then
`info.version` and the top heading set to `0.2.0`), committed, then the unchanged script:

```
error: info.version is 0.2.0, not a -SNAPSHOT: rehearse on a commit before the release
EXIT=1
```

The regression check lives in the script itself: steps 2 and 3 of the `-SNAPSHOT` path now run the script on their
synthetic release commits, which takes the release pull request's branch. With the old guard put back, the rehearsal
fails at step 2 for the right reason:

```
error: info.version is 0.2.0, not a -SNAPSHOT: rehearse on a commit before the release
error: :contracts:checkReleaseTag did not refuse the unassembled fragment
EXIT=1
```

## Similar-surfaces sweep

- `rehearse-packs-contract-release.sh`: fixed by #568.
- `rehearse-service-release.sh`: sets the contract version itself; run on the release pull request's state above, it
  exits 0.
- No other script or workflow under `scripts/` or `.github/workflows/` assumes a `-SNAPSHOT` contract.
- Docs: only `contracts/lint/README.md` describes the rehearsal's behaviour; updated.

## Scope

The script, its README paragraph and a changelog entry. No contract, gate, workflow or Java change. Local builds need
the full history (`git fetch --unshallow`): `:contracts:checkContractCompatibility` refuses a shallow clone, as CI's
`fetch-depth: 0` already provides.

## Squad (pre-PR)

Two lenses, release engineering and shell/tests. No BLOCKER or MAJOR.

| Sev | Finding | Disposition |
|---|---|---|
| MEDIUM | If the synthetic release's version edit misses, the self-call recurses until the job times out | fixed: the nested run sets `REHEARSAL_OF_A_RELEASE`, and a `-SNAPSHOT` under it stops the run (verified: exits 1 in 20 s) |
| MEDIUM | `git tag` fails when the release tag already exists (a re-run on the tagged release commit); the packs rehearsal shares the line | fixed in both: `git tag -f` (the clone has no remote) |
| LOW | Step 2's nested output appeared only after it ended | fixed: `tee` |
| LOW | The nested run uses the working-tree script, not the commit's | kept: it rehearses the script under review; no effect in CI |
| LOW | Two nested clones add a configuration pass to the job | accepted: the run took 1 min 21 s locally, as before |
