### Breaking

- An agent profile's references are judged as a graph edge's endpoints are (#673; operator rulings of 2026-11-26).
  - **What is judged:** each `directive-references` code, each `tactic-references`, `toolguide-references` and
    `styleguide-references` id, each `collaboration.operating-procedures` entry, and each `excluding` value under the
    key of one of those reference lists (`excluding: {directive-references: [ghost::DIRECTIVE_009]}`).
  - **How each is written:** each names an artifact of its list's kind by the artifact's id. It is bare when the
    artifact is in the profile's own pack and `<pack>::<id>` when it is in another pack, and the kind may be written
    out (`<pack>::directive:DIRECTIVE_001`). A directive code is the directive's id (`DIRECTIVE_001`, `QA_001`), never a
    part of it.
  - **The refusals:** a registration, a composing read and `pack-doctor validate --full` refuse a profile reference with
    the codes an edge's endpoint gets (ADR-0053 decision 7):
    - `pack_reference_unqualified` when it is bare and only another pack of the closure holds it;
    - `pack_reference_pack_missing` when it names a pack outside the writer's `depends_on` closure;
    - `pack_reference_artifact_missing` when the named pack of that closure does not hold it.
  - **Not a fault:** a bare reference that no pack holds, as for an edge's endpoint.
  - **An entry that is no reference:** the packs contract's `agent-profile.schema.json` refuses a short directive code
    (`001`) and an entry that writes another kind. Both are `artifact_schema_error` at
    `agent_profiles/<file>#/<list>/<n>/...`. The service also refuses a pack name past the pack-id cap, which the
    schema's pattern lets through, and an `excluding` value of a reference list that is no reference, which the schema
    does not pattern, as `artifact_schema_error` at the profile file.
  - **Removals:** a registration that removes an artifact another pack's profile names qualified is refused
    `pack_reference_artifact_missing`, and so is an artifact `DELETE`, through its closure impact check. A `409
    artifact_referenced` still names graph edges only: the references stay selection data (ADR-0064 decision 11). They
    mint no edge and are not followed by the governance walk.
  - **Packs that registered before:** a pack with such a fault registered before.
    - A new registration of it is refused.
    - A pack the service stored before is read without the schema. So a stored bare short code names no artifact and is
      no fault, but a stored qualified one (`foundation::003`) is `pack_reference_artifact_missing` on the reads that
      compose its closure, until the pack is registered again with the directive's id.
    - A stored profile whose entry writes another kind, or names a pack past the pack-id cap, breaks the profile's
      invariant: the reads that build the stored pack's artifacts leave that profile out, recording an
      `artifact_schema_error` at its file, until the pack is registered again.
  - **The effective profile read:** `getEffectiveAgentProfile` answers each reference as the answered profile's pack
    (`packId`) writes it, so it names the same artifact there.
    - A reference that an enhancer or an ancestor of another pack writes bare is qualified with that pack
      (`org-base::DIRECTIVE_038`).
    - One it qualifies with the answered profile's pack is bare (`DIRECTIVE_051`).
  - **Kept by what it names:** an enhancement keeps a reference of the profile it enhances by what the reference names,
    not by how it is written (`profile_enhancement_drops_field`, ADR-0078 decision 5). A bare `DIRECTIVE_001` of a
    built-in profile is kept by its org enhancer's `built-in::DIRECTIVE_001`, and dropped by a bare `DIRECTIVE_001`,
    which names the enhancer's own pack.
  - **Consumer impact:** a client of `getPackAgentProfile` or `getEffectiveAgentProfile` (core's generated client,
    doc-kitty) reads a reference in `directiveReferences`, `tacticReferences`, `toolguideReferences`,
    `styleguideReferences` or `collaboration.operatingProcedures` as `[<pack>::][<kind>:]<id>`. It resolves a bare one in
    the answered profile's pack (`packId`) and a qualified one in the pack it names.
  - **Core's seam:** core reads these codes and ids as references, not as short codes.
    - A directive code is the full id, for example `DIRECTIVE_001`, never `001`, and it may carry a pack qualifier or the
      kind.
    - On the effective read, a reference may be qualified where the stored profile wrote it bare.
    - No code is added and no schema of this contract changes shape (three descriptions say how a reference is written),
      so core regenerates nothing.
  - **Scenarios:** `registration/profile-references.feature`, and `profiles.feature` for the effective read.
