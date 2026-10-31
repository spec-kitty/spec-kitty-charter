# WP05 review 2: response

All six issues are fixed. The migrated tree is unchanged: the harness, rerun after every change, reproduces it
exactly, apart from the hand-seeded `tree_digest`. So the patch and the commit `12b0e553` stand.

## A: guidelines with a detailed description (fixed)

`MissionStep` refuses guidelines that carry a detailed description. The refusal is a `ConstraintBreachException`,
"A mission step's guidelines carry no detailed description; only its prompt does." `MissionValuesTest` covers it.

## B: the role fallback and the repeats in ADR-0044 (fixed)

- The ADR now says the fallback applies to two `retrospect` steps (documentation, research), and that software-dev's
  `retrospect` index belongs to no step and is not carried. Answer 5 of `review-response-1.md` is corrected the same
  way.
- An action index that names more than one profile now stops the harness and fails the verifier. The ADR states this
  rule.
- Where the runtime and `step.yaml` both name a profile, both tools check that they are equal.
- `manifest_version`, `step_transition` and `prompt_template_dir` moved out of the repeats in the ADR and in the
  consolidation draft. They are now drops ("runtime mechanics with no place"). The repeats now name `loop_type` and
  `terminal_step` only.

## C: the gate evidence (fixed)

- `gate-evidence.md` now states the whole-pack gate's limits: built-in only, YAML compared by meaning and Markdown by
  model.
- The byte-for-byte proof is a test that will be committed with WP06. `MissionTypeCorpusTest.everyMissionFileWritesBackByteForByte`
  covers built-in, cave-kitty and qa. It reads each pack, deletes its mission-type files and companions, writes them
  back through `toEntity`, `toDso` and `FilesystemPackStore.write`, and compares bytes. 66 of 66 files are
  byte-identical, the 28 prompts with frontmatter included. It is staged and green against `12b0e553`, and lands with
  the re-pin.
- `repin.py` is committed beside the evidence, and the evidence lists its four edits.

## D: verifier gaps (fixed)

1. The runtime block has a KEY_TABLE entry: `loop_type` and `terminal_step` are repeats, and `step_transition` and
   `prompt_template_dir` are drops.
2. A step's runtime profile and its `step.yaml` profile are compared, in both tools.
3. Every `actions/*/index.yaml` is enumerated. An index with no step must be in `KNOWN_UNCARRIED`, which today holds
   only software-dev `retrospect`, and its keys are checked.
4. Every `template_path` must name a file. 13 are counted and asserted.
5. The 17 dropped titles, the 4 descriptions taken only from frontmatter and the 1 uncarried index are computed
   independently and asserted.
6. `step.yaml` is read from the step's directory, not its id. The harness had the same latent bug, and it is fixed
   there too.
7. `self_test.py` now plants 14 defect classes and detects 14 of 14. The new ones are a differing runtime key, a
   differing nested plan step, an unaccounted runtime-block key and a missing template.

## E: negative tests (fixed)

- `frontmatter.description` in the prompt schema now has `pattern: "\\S"`, as does its fixture copy.
- `anyOtherPromptFrontmatterIsRefused` is a parameterized test with seven cases: another key, a number, an empty
  string, a blank string, a list, no description, and an empty block.
- `theDescriptionBoundCountsCodePoints` shows that 160 astral characters pass the schema and 161 do not.

## F: small points (fixed)

1. A template file claims its id only after it has been read.
2. The PackFileSet javadoc names mission templates.
3. `plan.md` notes the refined ruling at both lines.
