# Gate evidence: the migrated tree loads and writes back

This file is the load-then-write check (IC-07) for the migrated commit `12b0e553f1c1450c32020fc3f91c56cd9d7c75e7`
(open-packs, on `5df92a5d`).

**Setup.** A scratch worktree of the charter branch with the staged WP06 changes. The re-pin edits came from
`repin.py <charter checkout> <migrated checkout> <sha>`, which is beside this file. It makes four edits:
- sets `openPacksRef`;
- re-vendors `packs/qa`, keeping its local `PROVENANCE.md` and `README.md`;
- turns the seven mission exclusions of `contracts/packs/file-kinds.yaml` into the `retired` section;
- removes the two #194 entries of `expected-red.yaml`.

WP06 also updates the tests pinned to the old layout and adds `MissionTypeCorpusTest`.

**Command.** `./gradlew -PopenPacksDir=<migrated checkout> :packs:openPacksRoundTrip :packs:wholePackRoundTrip`, then
`verifyAll`.

**What each check proves.**
- `MissionTypeCorpusTest` (`:packs:openPacksRoundTrip`) proves the byte-for-byte write-back. For each of built-in,
  cave-kitty and qa:
  - it reads every mission type into the domain with no violation of its mission tier;
  - it writes every mission-type file and step companion back through the production writer
    (`toEntity`, `toDso`, `FilesystemPackStore.write`);
  - it compares the bytes: 49, 9 and 8 files, 66 in all, every one byte-identical. This includes the 28 prompts that
    keep frontmatter.

  It also pins the counts (6 types, 40 steps, 6 on demand, 43 prompts, 17 guidelines, 28 detailed descriptions) and
  checks that no pack holds a retired file.
- `:packs:wholePackRoundTrip`: 7 tests, 0 failures. It covers the built-in pack only. It loads the pack into the
  domain and writes it back, comparing YAML by meaning (ADR-0036) and Markdown by model, so it is not a byte check.
- `:packs:openPacksRoundTrip`: all green once WP06's updates to the old-layout tests are in. Before them, the three
  failures were the built-in classified-file count (386 → 316: 74 retired YAML files out, 4 mission types in) and
  `MarkdownCorpusTest`'s glob (72 → 85 covered files).
- `verifyAll`: green. One run hit a timing failure in `HttpsGuideFetcherLoopbackTest` while other builds were running;
  it passes on its own and in the next full run.

**Open-packs' own CI scripts**, run on the migrated checkout with spec-kitty 4.0.0rc8:
- `check_vocabulary.py`: 0 findings.
- `tests/vocabulary` and `tests/release`: OK.
- `gen_pack_reference.py --check`: no drift.
- `validate-packs.sh` and `check-built-in.sh`: the same error lines as on the pin, compared with `diff`. Both already
  fail on the pin with this core version.
