# Op 01M8CJGX8YKJXSPMKN60C6NV7N: draft issue for spec-kitty-open-packs (#673)

A charter session has no write access to spec-kitty-open-packs, so this file holds the draft of an issue. The
orchestrator filed it as
[spec-kitty-open-packs#356](https://github.com/spec-kitty/spec-kitty-open-packs/issues/356)
(`.kittify/charter/charter.md`, "Orchestrating cloud sessions").

The operator ruled on 2026-11-26 that the migration is acceptable and does not block the charter change: "charter
leads, packs follow".

## Draft: migrate agent-profile references to the charter's reference shape (open-packs#356)

**Type:** Task. **Labels:** `follow-up`.

### What changed in the charter

Charter #673 changes how the charter reads and judges agent-profile references.

1. **The fields judged.** Registration, a composing read and `pack-doctor validate --full` now judge each of these as a
   graph edge's endpoint is judged:
   - a `directive-references` code;
   - a `tactic-references`, `toolguide-references` or `styleguide-references` id;
   - a `collaboration.operating-procedures` entry.

   The codes are `pack_reference_unqualified`, `pack_reference_pack_missing` and `pack_reference_artifact_missing`.
2. **The form of an entry.** Each entry names an artifact of its list's kind by the artifact's id:
   - bare when the artifact is in the profile's own pack;
   - `<pack>::<id>` when it is in another pack;
   - with the kind written out, optionally (`<pack>::directive:DIRECTIVE_001`).
3. **The directive code.** A directive code is the directive's id, never a part of it. The packs contract's
   `agent-profile.schema.json` refuses `003` and `foundation::003` (`artifact_schema_error` at
   `#/directive-references/<n>/code`). The id is `DIRECTIVE_003`, or `foundation::DIRECTIVE_003` across packs.
4. **An enhancement keeps a reference by what it names.** An enhancer in another pack that restates its base's
   references writes them qualified with the base's pack, `built-in::DIRECTIVE_001`, not bare. A bare reference in the
   enhancer names the enhancer's own pack, so it drops the base's (`profile_enhancement_drops_field`).

### Effect on open-packs

**At the pinned commit `1692a3b1`.** The charter's pack-doctor, built from the #673 branch and run with
`validate --config-root . --full`, reports 97 `artifact_schema_error` violations at
`agent_profiles/*#/directive-references/<n>/code`. That is every short directive code. About 80 of them are
qualified, such as `foundation::003`.

The other violations it reports at the pin predate #673 and are not part of this issue:
- `anti_patterns` keys;
- `glossary_packs` nodes;
- `in_tension_with`;
- `sentinel`.

`CLOSURE` and `REFERENCES` are skipped there, so the reference faults that remain after the code migration are not yet
known.

**At `origin/HEAD` (`f9a55751`).** The same holds: 101 short codes, 80 of them qualified.

### Ask

1. **Rewrite every directive code as the directive's id.** `"010"` becomes `DIRECTIVE_010`, and `foundation::003`
   becomes `foundation::DIRECTIVE_003`.
2. **Qualify every cross-pack reference in all five lists.** A bare reference that only another pack of the closure
   holds, such as a profile citing a `foundation` artifact bare, is `pack_reference_unqualified`; write it
   `foundation::<id>`. REFERENCES was skipped in the probe at the pin, so the count is not known.
3. **Qualify each restated reference in a cross-pack enhancer.** An enhancer that enhances a profile of another pack
   writes each reference it restates qualified with that pack.
4. **Retire the Python rules.** With a pinned charter release that carries #673, run pack-doctor `--full`. Then
   retire the three Python profile-reference rules (open-packs #343, #348). The signal is that
   `RealPackDoctor.test_pack_doctor_does_not_yet_judge_agent_profile_references` fails. Mind two differences when
   retiring them; the Python rule is stricter in both:
   - The charter does not refuse a bare reference that no pack holds, as for an edge.
   - The charter does not refuse a reference qualified with the profile's own pack.
