---
affected_files: []
cycle_number: 1
mission_slug: service-release-native-01M693YF
reproduction_command:
reviewed_at: '2026-10-31T14:49:07Z'
reviewer_agent: reviewer-renata
wp_id: WP04
---

# WP04 review feedback (reviewer-renata) - REJECT

The smoke script itself is sound: it passes against the local jar (first answer 5.6 s, all 7 checks ok), fails on a
wrong expected version and on an artifact that exits at once, leaves no process behind, and every check proves what it
claims (N2 via GuideFetchConfiguration -> GlobalAddressResolverProvider.installRequiringGuard, an eager bean that throws
when an allowlist is set and the guard is not the JVM resolver). Pins, permissions, persist-credentials, artifact names,
cache-disabled wiring, concurrency and the gate's NATIVE entry are all correct. Two defects stop the native matrix from
going green on 2 of 4 targets (FR-010, SC-002), so the WP cannot meet its Definition of Done as written.

## Blocking 1: Windows checkout is CRLF, so windows-x64 fails before and at the smoke step

The repository has no `eol` rule in `.gitattributes`, and GitHub's Windows runners check out with
`core.autocrlf=true`. On `windows-2025` with `shell: bash` (Git Bash):

- `./gradlew` gets `#!/bin/sh\r` and fails with "bad interpreter" (service-native.yml:85);
- `scripts/release/smoke-service.sh` gets CRLF and bash fails on `$'\r'` (service-native.yml:108);
- `gradle.properties` and `openapi.yaml` give `0.1.0-SNAPSHOT\r` to the versions step (service-native.yml:90-91), so
  the `serviceVersion` check would fail even if the script ran.

The WP's Risks section names CRLF explicitly; nothing in the commit handles it. The reference pipeline
(`../spec-kitty-events/.gitattributes`) solves it with `* text=auto eol=lf` and `*.bat text eol=crlf`.

Fix, one of:
- (preferred, matches events) add those two lines to `.gitattributes`. That file is outside WP04's `owned_files`:
  record the ownership amendment or the coordination note in the move-task note;
- or, within owned files, add a step before the checkout in service-native.yml:
  `git config --global core.autocrlf false` and `git config --global core.eol lf`.
  This step needs `shell: bash` or pwsh, which works on every runner.

## Blocking 2: the contract-version `sed` is GNU-only, so macos-arm64 fails

`sed -n '/^  version:/{s/^  version: *//p;q}'` (service-native.yml:91, and the same line at service.yml:154) puts `}`
straight after `q`. BSD sed, which is `/usr/bin/sed` on macos-15, rejects it with "extra characters at the end of q
command". Under `set -euo pipefail` the step fails. The jar job runs on Ubuntu, so only the macOS native job is hit.
Fix: `'/^  version:/{s/^  version: *//p;q;}'`, or use awk:
`awk '/^  version:/{sub(/^  version: */,"");print;exit}'`. Keep both workflows identical.

## Non-blocking (fix if you are in the files)

- service-native.yml:12-20 and ci.yml:54 (native_re): `contracts/` is not a native path. The executable embeds
  `charter-openapi.yaml` and is built against the DTOs generated from the contract, so a contract-only change can
  break a native build (new DTO, missing hint) that shows up only at release. Consider adding `contracts/` to both,
  keeping them identical.
- smoke-service.sh:120 and the curl calls: the bearer token goes on curl's argv, so `ps` can see it on the runner.
  It is a throwaway random token, so the risk is low. `-H @file` with a 0600 header file would keep it off argv, as
  the WP context asks of the service.
- T014 asked for a README line next to the fixture. The tactic's `purpose` and the script header cover it, but there
  is no README.
- service.yml jar job: unlike release-rehearsal it has no `if: github.workflow != 'Docs site'`, so a docs-site call
  also builds and smokes the jar. This is harmless, but it is wasted minutes.
- service.yml rehearsal loop: under `set -e`, a failing rehearsal leaves the `::group::` open. This is cosmetic only;
  the job still fails fast.
- Existing `verify` checkouts (service.yml) still persist credentials. This predates the WP, and C-005 asks for
  `false` on every checkout; consider aligning it.
