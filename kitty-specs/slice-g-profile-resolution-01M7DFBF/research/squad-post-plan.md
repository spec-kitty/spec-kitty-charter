# Squad: post-plan (domain, contract, migration), 2026-11-14

This squad reviewed the plan at `78a437a23`. It found 2 blockers, 9 majors and 13 minors. All are folded: the plan,
the spec and the rulings D2 to D7 changed. B1, M4 and M5 were checked against the files before folding (the
`agent-profile.schema.json` `required` list, `profiles.feature:1-29`, `AgentProfile.yaml:7-11`).

| # | Finding | Disposition |
| --- | --- | --- |
| B1 | An enhancer is a full profile with its own id and the required `name`, `purpose` and `specialization`. P5 would refuse every enhancer, and an in-force enhancer would be a selection candidate | Folded. An enhancer's identity fields (`profile-id`, `name`, `purpose`, `role`, `alt-roles`, `performer`, `routing-priority`) are its own and never merged. Enhancers are excluded from selection candidates and from `lineage`. An enhancer's effective read answers its own body plus an `enhances` link. Ruling D5 is reworded |
| B2 | `project` depends on org-base, so an org-base pin removes the reviewer tie from every closure | Folded. The pin goes in `project`'s new `policy.yaml`; org-base gains a second reviewer, so the org-base context shows the tie |
| M1 | Picking the tier before the filters sends the fallback when an alt candidate fits; D2's rules 1 and 3 contradict each other; "descriptors" is undefined | Folded. Filter first (pin, performer, context), then pick the tier. D2 rewritten: only a specialization is filtered by context, and the descriptors are `languages` and `frameworks` only. A root fits any context |
| M2 | Under a strict depth order, the pinned profile is never tied. Depth is undefined with several parents or a cycle | Folded. Depth is the longest in-force `specializes_from` chain. A `specializes_from` cycle is refused at registration (`specialization_cycle`, new). ADR-0032 d9's tie-break is unreachable under the order, so ADR-0078 records that and US2.3 is removed. Ruling D2 |
| M3 | A pin to a human profile that does not fit narrows to nothing; a human fallback would serve every role | Folded. A pin must fit its role (no human exemption), and `performer: human` on a fallback profile is refused (`fallback_profile_not_agent`, new). Rulings D3 and D4 |
| M4 | ADR-0053 d22 rules on steps only; profiles keep their field-merge meaning, and the approved scenario treats an omission as a drop | Folded. D5 is re-posed with the field-merge reading recommended, and the approved scenario stands. `excluding` on an enhancer is a ruling item (D7) |
| M5 | Always answering `performer` breaks the read's "no default invented" rule | Folded. `performer` is omitted on the wire when the file is silent; selection reads absent as `agent`. Ruling D4 |
| M6 | Pin conflicts can meet only in an activation, never in one registration | Folded. `role_pin_conflict` is also raised at selection (422), as the fallback conflict is. Ruling D3 |
| M7 | WP01 and WP02 fail `ExamplePackCoverageTest` without their own examples; org-base has no policy file; the manifest hashes collide across lanes | Folded. Each WP carries its example. WP02 owns the new `project/policy.yaml`. WP01 and WP02 run in one lane, sequentially, so the manifests are restamped once per WP |
| M8 | ADR-0078 must amend ADR-0032 d5, ADR-0069 d4.5 and ADR-0053 d22; a fragment holds one `###` block; the path is `contracts/charter/changes/` | Folded into P6 and WP06: two fragments, one Breaking and one Added |
| M9 | `service/activations` is missing from the plan (the in-force set, `EffectiveReadService`, `EffectiveTag`); #106 edits the same files | Folded. The module is added, and the #106 overlap is named in the risks |
| minor | The `operations:` override is redundant | Folded: no override |
| minor | Why the conflict answers 422 at read but the drop answers 500 | Folded. ADR-0068 d7 fixes 422 for the fallback conflict, and the pin conflict follows it. The drop keeps the placeholder's 500, because it is a fault of stored content met on a read. ADR-0078 records the difference |
| minor | `generic` as a path pattern, not a refusal | Folded |
| minor | The ETag must cover the role and the context; `stale_input`; refusal order | Folded into P4 |
| minor | Effective-read description and `_links.artifact` text | Folded into WP05 |
| minor | The real overlaps with F are fixtures, manifests, the refusal registry and `EffectiveReadService` | Folded into P3 |
| minor | #106 renames `glossary_pack`, so the new `policy.yaml` carries no `kind_defaults` | Folded |
| minor | WP07: F's substep `delegates_to` agent_profile candidates against ADR-0032 d1; `{missionType}` | WP07 records the question; the path is fixed |
| minor | `no_profile_matched` needs no stand-alone pack (`operator` in the built-in context) | Folded |
| minor | An agent-performed `role: operator` profile is never selectable | Folded. It is refused (`operator_role_not_human`, new). Ruling D4 |
| minor | WP02 and WP04 both edit `RegistrationImpact` and `RefusalCode` | Folded. WP04 follows WP02 in the lane |
| minor | "The most derived pin wins" lets a project loosen an org pin (ADR-0012) | Posed to the operator as D3(ii) |
