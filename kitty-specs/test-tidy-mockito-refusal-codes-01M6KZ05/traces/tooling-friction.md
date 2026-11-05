# Tracer: tooling-friction

One entry per finding: `YYYY-MM-DD · actor · <text>`.

---

2026-11-04 · claude · [2026-11-04] Session setup: the auto-mode classifier refused add_repo of spec-kitty/spec-kitty and the CLI install from a core clone (code from external). The operator authorised a plain read-only git clone and the uv editable install in chat; both then ran. Suggested fix: install the CLI in the environment setup script (docs/development/cloud-session-setup.md already recommends it).

2026-11-04 · claude · [2026-11-04] Maven Central answered HTTP 429 on the first syncPacks; the documented Central mirror init script (cloud-session-setup.md) fixed it.

2026-11-04 · claude · [2026-11-04] REVIEW_NOT_INDEPENDENT on WP01 (same as #368's friction entry 1): the implementer's move to for_review used --agent claude, recording a bare 'claude::' and overwriting the --profile java-jenny claim; the reviewer's approval as claude::reviewer-renata was refused. Worked around per the operator's #368 ruling: back to planned with an identity-only note, re-claim, resubmit b4a9b2c4 unchanged as claude::java-jenny, approve as claude::reviewer-renata. Moving back to planned also reset the done subtasks (re-marked). Suggested fix as in #368: a lifecycle move inherits the claim's identity.

2026-11-04 · claude · [2026-11-04] The approve move was first refused by the issue-matrix gate: every cited issue (including context-only citations of sibling-mission issues) needed a verdict; the orchestrator recorded them (#373 fixed, #369 in-mission, #239/#320/#367/#370/#374 not-applicable).

2026-11-04 · claude · [2026-11-04] Metadata regen: a warm-cache --write-verification-metadata from empty <components> dropped 25 components and 20 BOM .module files a cold cache needs (e.g. kotlinx-coroutines-bom 1.8.0); the committed file merges warm and cold-cache regen runs. '-p build-logic' runs write a separate build-logic/gradle/verification-metadata.xml (discarded), so CI's -p build-logic runs are never dependency-verified.

2026-11-04 · claude · [2026-11-04] spec-kitty doctor charter-packs reports 8 ACTIVATION_UNMIGRATED legacy-surface warnings and the WP prompt header reports governance unavailable on a dangling directive:RED_MAIN_RELEASE_DISCIPLINE reference (pre-existing, out of scope).

2026-11-04 · claude · [2026-11-04] REVIEW_NOT_INDEPENDENT recurred on WP02 although the implementer claimed with --profile java-jenny and moved to for_review with --agent claude::java-jenny: the gate still recorded 'claude::'. Only a re-claim after a move back to planned recorded the profile. Same #368 workaround applied (resubmit e6080c7d unchanged).

2026-11-05 · claude · [2026-11-05] Correction to the 2026-11-04 metadata entry: no CI workflow runs '-p build-logic'; verifyAll covers build-logic as an included build against the root metadata, so only local -p build-logic runs are unverified (they write a stray build-logic/gradle/verification-metadata.xml). The regen removed 1 artifact file inside a kept component (commons-codec 1.17.1 jar), not 9; corrected in the changelog (b9a1f425).
