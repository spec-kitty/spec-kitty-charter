# Data model: Serve tensions

## Kernel

- **`RelationType.TENSION`**: token `tension`, `Directionality.BIDIRECTIONAL`, not derived, endpoints
  `product(GUIDANCE, GUIDANCE)` (directive, tactic, styleguide, toolguide, paradigm, procedure).
- **`Relationship`** (unchanged fields: `from`, `fromPack?`, `to`, `toPack?`, `type`, `when?`, `rationale?`).
  - **Pair class**: `STRUCTURAL` for every type but `TENSION`; `TENSION` for it.
  - **Identity** (`equals`, `hashCode`): the unordered bare pair `{from, to}` and the pair class.
  - `samePair(other)`: the unordered pair only. `sameSlot(other)`: pair and class (= `equals`).
  - Factory `tension(KindRef<? extends Guidance>, KindRef<? extends Guidance>)`.
- **`DoctrineGraph`**: nodes and relationships; the tensions list is removed. A tension is a relationship.
- **Removed**: `Tension`, `EndpointTable.tension*`, `EndpointRule.bindTension`.

**Invariants**
- At most one structural relationship and at most one tension per unordered pair (per pack: refusal, FR-004; on a
  write: upsert on the slot, or 422 `tension_duplicated` against another document, FR-005; across packs: collapse,
  FR-006).
- Stored orientation (G1, extended): an owned side is the `source`; when the pack owns both sides, or neither,
  `source < target` on the bare URNs.
- The two sides of a tension have different bare URNs.

## Packs

- **Load codes**: `tension_duplicated` (two tensions on one unordered pair across the pack's graph documents; reported
  at the second edge, naming the first's file and index) and `tension_orientation` (a tension whose bare source is not
  the smaller between two owned sides, or not owned by the pack; reported at the edge) and `tension_self` (both sides
  the same bare URN). All `VIOLATION`, in `PackCodes.CONTENT`.
- **`TensionsInForce`** (application, new): from the merged links of an activation's closure and the in-force set.
  - `TensionInForce(source: Urn, target: Urn, when?, reason?, reconciledBy: SortedSet<Urn>)`, canonical, sorted by
    `(source, target)`.
  - Built: resolve each authored tension symmetrically (G2: absent when the authoring pack's own version of either
    side loses; a side it does not hold re-points to the winner); keep those whose two endpoints are in force;
    canonicalize; drop a self-pair; collapse on the pair, the first in merge order keeping its words; `reconciledBy` = each in-force `R` with merged `reconciles_tension` links
    `R → source` and `R → target`.
  - `deliveredTo(Set<Urn> listed)`: the subset whose two sides are in `listed`.
  - `unreconciled()`: those with an empty `reconciledBy`.

## Contract (wire)

- `Relation` gains `tension`.
- `EffectiveGraphEdge`: `source`, `relation`, `target`; and, for `relation: tension` only, `when?`, `reason?`,
  `reconciledBy` (required on a tension edge, an array of URNs, possibly empty; absent on others).
- `ContextTension`: `source`, `target`, `when?`, `reason?`, `reconciledBy` (array of URN).
- `EffectiveActionContext.tensions`: required array of `ContextTension`.
- `ActivationTensions`: `activationId`, `revision`, `governanceFingerprint`, `tensions` (array of `ContextTension`),
  `findings` (array of `TensionFinding`), `_links` (`self`).
- `TensionFinding`: `code` (enum `unreconciled_tension`), `source`, `target`, `resolutions` (array over
  `reconcile` | `deactivate_one_side`, `reconcile` always, `deactivate_one_side` only with an exclude link), `_links`
  (`$ref Links.yaml`: `effective-source`, `effective-target`, and `exclude-source`/`exclude-target` for a side that is
  neither locked nor required).
- `Activation._links.tensions`: `getActivationTensions`.
- `deletePackRelationship`: optional query parameter `relation` (a `Relation`).
- `TaxonomyRelation`/`RelationRow`: `in_tension_with` → `tension`; `heldAs: tension` names the pair class.
