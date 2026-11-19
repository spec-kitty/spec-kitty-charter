# Release runbook: `service-v0.1.0-rc.2` (FR-012, ruling D4)

**Mission:** `pack-check-cli-01M7SASC` (#478). Written 2026-11-19 by WP08.

These are the steps after PR #608 merges into `main`. They follow `docs/guides/service-build.md`, "Release a service
version", steps 3 to 5, and the workflows `.github/workflows/service-native.yml` (name `service-native`) and
`.github/workflows/service-release.yml` (name `service-release`). Steps 1 and 2 of the guide are settled by ruling D4:

- **Version:** `0.1.0-rc.2`, a pre-release.
- **Contract:** a pre-release may bundle an unreleased contract.

The precedent is `service-v0.1.0-rc.1`: commit `b886d9886` ("release(service): service 0.1.0-rc.1") set the
version, and `bbf7f4385` reopened `0.1.0-SNAPSHOT`.

> **The operator pushes tags.** A session never pushes a tag, and the session's proxy refuses one. A session hands the
> tag and the commit to the orchestrator (step 3), and the operator pushes it.

In a cloud session, GitHub's GraphQL API is refused, so a `gh` command that uses it (`gh pr create` among them) fails
there. Where a step gives a REST form (`gh api …`), use it in a session.

The values below were checked at closeout against the merged `scripts/release/service-release-assets.sh` and
`service-release.yml`.

## 1. The version PR

From an up-to-date `main`, on a new branch:

1. In `gradle.properties`, set `version=0.1.0-rc.2` (it is `0.1.0-SNAPSHOT`). Change nothing else.
2. Check the gate locally:

   ```bash
   git fetch --tags origin
   ./gradlew checkServiceReleaseTag -PreleaseTag=service-v0.1.0-rc.2
   ```

   It names the cause of a refusal, or writes `build/service-release/gate.properties`. Expect `prerelease=true` there.
   While `info.version` of `contracts/charter/openapi.yaml` is `0.2.0-SNAPSHOT`, expect the contract note
   `It implements the charter contract 0.2.0-SNAPSHOT, which is not released yet.`
3. Commit, signed, in the precedent's form: `release(service): service 0.1.0-rc.2`.
4. Open the PR against `main`. The PR body states the following:
   - **The release:** this is the release candidate that adds the `pack-check` jar to the `service-v*` release
     (#478, ruling D4).
   - **The contract:** the bundled contract is unreleased (`0.2.0-SNAPSHOT`, `info.version` at this commit). A
     pre-release may bundle it (`service-build.md` step 2; the gate's pre-release rule in `ServiceReleaseGate`).
   - **The tag:** `service-v0.1.0-rc.2`, at this PR's merge commit on `main`, pushed by the operator once
     `service-native` is green at that commit.
   - `Refs #478`.

   In a session:

   ```bash
   gh api repos/spec-kitty/spec-kitty-charter/pulls -f title='release(service): service 0.1.0-rc.2' \
     -f head='<branch>' -f base=main -f body='<body>'
   ```

5. Record the PR URL.

## 2. Native build at the merge commit

After the version PR merges, note its merge commit on `main` (`<merge-sha>`). `CI gate / gate` does not prove the
native build (ADR-0060). Dispatch `service-native.yml` on `main`. The `cache-disabled` input builds without the Gradle
cache, "as a release does":

```bash
gh workflow run service-native.yml --repo spec-kitty/spec-kitty-charter --ref main -f cache-disabled=true
# or, by REST:
gh api repos/spec-kitty/spec-kitty-charter/actions/workflows/service-native.yml/dispatches \
  -f ref=main -f 'inputs[cache-disabled]=true'
gh run list --repo spec-kitty/spec-kitty-charter --workflow service-native.yml --branch main --limit 1 \
  --json databaseId,headSha,status,conclusion,url
```

- A dispatch runs at the tip of `main`. Check that the run's `headSha` is `<merge-sha>`. If `main` moved before the
  dispatch, the run proves another commit: tell the orchestrator, and do not ask for the tag on `<merge-sha>` on that
  run's evidence.
- Watch it to the end: `gh run watch <run-id> --repo spec-kitty/spec-kitty-charter`. Its jobs are `contract-bundle`
  and `native (linux-x64)`, `native (linux-arm64)`, `native (macos-arm64)` and `native (windows-x64)`.
- Record the run URL.

## 3. Hand the tag to the orchestrator

When that run is green, tell the orchestrator:

- **Tag:** `service-v0.1.0-rc.2`
- **Commit:** `<merge-sha>`, the version PR's merge commit on `main`
- **Native run:** the URL from step 2
- **The command, for the operator:**

  ```bash
  git tag service-v0.1.0-rc.2 <merge-sha> && git push origin service-v0.1.0-rc.2
  ```

The operator pushes it. The session does not run this command. A tag ruleset on `service-v*` limits who can push one.

## 4. The release

Pushing the tag runs `service-release.yml`. Its jobs are:

- `gate`: the tag's form, the commit on `main`, and `checkServiceReleaseTag`;
- `jvm`: `service.yml`, with `verifyAll` and the jar;
- `native`: `service-native.yml` again, with the cache off;
- `assemble`: `scripts/release/service-release-assets.sh`;
- `publish`: checks the sums, attests, and runs `gh release create`.

Watch it and record the run URL:

```bash
gh run list --repo spec-kitty/spec-kitty-charter --workflow service-release.yml --limit 1 \
  --json databaseId,headBranch,headSha,status,conclusion,url
gh run watch <run-id> --repo spec-kitty/spec-kitty-charter
```

Then check the release. It is a pre-release, never marked latest, titled `Charter service 0.1.0-rc.2`:

```bash
gh api repos/spec-kitty/spec-kitty-charter/releases/tags/service-v0.1.0-rc.2 \
  --jq '{url: .html_url, prerelease, count: (.assets | length), names: [.assets[].name]}'
```

- **13 files**: `SHA256SUMS`, and six assets with one `.sha256` each:
  - `spec-kitty-charter-service-0.1.0-rc.2-linux-x64`;
  - `spec-kitty-charter-service-0.1.0-rc.2-linux-arm64`;
  - `spec-kitty-charter-service-0.1.0-rc.2-macos-arm64`;
  - `spec-kitty-charter-service-0.1.0-rc.2-windows-x64.exe`;
  - `spec-kitty-charter-service-0.1.0-rc.2.jar`;
  - `pack-check-0.1.0-rc.2.jar`.
- **`SHA256SUMS`:** 6 lines, one per asset.
- **Attestations:** one build-provenance attestation per asset.

Download it into an empty directory and verify it:

```bash
mkdir rc2 && cd rc2
gh release download service-v0.1.0-rc.2 --repo spec-kitty/spec-kitty-charter
wc -l SHA256SUMS
sha256sum --check --ignore-missing SHA256SUMS
for asset in $(awk '{print $2}' SHA256SUMS); do
  gh attestation verify "$asset" --repo spec-kitty/spec-kitty-charter \
    --signer-workflow spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml \
    --source-ref refs/tags/service-v0.1.0-rc.2
done
```

If `gh release download` fails in a session, fetch each asset's `browser_download_url` from the `gh api` call above.
`wc -l` must print 6, and every asset must pass both checks; `pack-check-0.1.0-rc.2.jar` is among them.

If the run fails after its assets were built, follow the guide: re-run only the failed jobs within 7 days. A partial
release from a failed upload stops the next run at "release exists": delete that release, not the tag, and re-run.

## 5. The snapshot PR

In a follow-up PR, reopen the development version. Set `version=0.1.0-SNAPSHOT` in `gradle.properties`, as
`bbf7f4385` did after `0.1.0-rc.1`. `0.1.0-SNAPSHOT` still sorts below `0.1.0`, so it stays the next development
version. The gate never publishes a `-SNAPSHOT`.

In the same PR, update the service release named in the status line of `docs/architecture/contract-and-dtos.md`
(line 25 on 2026-11-19), as `bbf7f4385` did for that file. The line now says "the service `0.1.0-rc.1` bundles the
second". Add `0.1.0-rc.2` with the contract it bundled. The release notes' contract note from step 4 states that
contract; while it is unreleased, the line says that the service `0.1.0-rc.2` bundles the unreleased `0.2.0-SNAPSHOT`.

- **Commit:** signed, `release: reopen service 0.1.0-SNAPSHOT after 0.1.0-rc.2`.
- **PR body:** `Refs #478`.

Record the PR URL.

## 6. Evidence on #478

Post one comment on #478 that lists:

- the version PR URL (step 1);
- the `service-native` run URL at `<merge-sha>` (step 2);
- the `service-release` run URL (step 4);
- the release URL, with the asset count (13) and the `SHA256SUMS` line count (6) (step 4);
- the snapshot PR URL (step 5).

Then #478 closes, with that comment as its evidence, and epic #549's line for the release is ticked (see `closeout.md`).
