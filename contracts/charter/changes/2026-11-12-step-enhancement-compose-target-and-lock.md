### Breaking

- Two compose checks judge every step enhancement, a `step_enhancement` whose one `enhances` edge names an
  `action:<type>/<step>` (ADR-0066 decision 8, phase 2; FR-002; #438). A compose, a registration or a write that
  answered before is now refused (`step-enhancement-compose.feature`):
  - **The target held (C37).** The step must be held (it defines the mission type and declares the step, OD-9) by the
    pack the edge names: the enhancing pack for a bare target, or the pack of the qualifier's name, which must be the
    enhancing pack or one of its `depends_on` closure. A qualifier naming a pack outside the closure is
    `pack_reference_pack_missing`, and a named pack that does not hold the step is `pack_reference_artifact_missing`,
    where an `action:` reference was never found missing before. The reads (`getOffering` and every composing read)
    and the activation writes answer `500`; a registration (`getPackRegistration`) and the impact-checked writes
    (`putPackRelationship` and the per-artifact registrations) answer `422`, as for every composition code.
  - **The inherited lock (OD-2 (A)).** A step takes its mission type's mutability level, judged along the closure
    without the enhancing pack's own policy. An enhancement of a step whose mission type is `locked` is refused `422
    override_not_permitted` at registration and on the impact-checked writes, naming the enhancing pack and then the
    pack that locks the mission type, and `500 pack_unreadable` on the activation reads and writes whatever the
    activation selects, as a forbidden layer override is (C21). An `extendable` mission type takes the enhancement.

  No enum value is added. Core's seam must expect these refusals where the same request succeeded; a pack that
  enhances an unheld step, or a step of a mission type a pack of its closure locks, must be fixed before it composes.
