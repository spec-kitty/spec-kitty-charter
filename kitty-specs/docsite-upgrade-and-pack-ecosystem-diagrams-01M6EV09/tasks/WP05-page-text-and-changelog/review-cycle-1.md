---
affected_files: []
cycle_number: 1
mission_slug: docsite-upgrade-and-pack-ecosystem-diagrams-01M6EV09
reproduction_command:
reviewed_at: '2026-11-02T21:27:05Z'
reviewer_agent: claude:opus-5.5:reviewer-renata
wp_id: WP05
---

# WP05 review: changes requested

## Blocking

**Issue 1 [HIGH] docs/architecture/descriptor-schemas.md:43-47 makes a false claim about today and presents an open question as decided.**

The new text reads: "The diagrams show the decided design, so they do not draw today's details. Today's schemas differ in these ways: `pack_version` is required at every level ... (DIV-004); `pack_level` is optional; edge endpoints are URNs only (DIV-007); and the org fragment has no header fields (DIV-008)."

None of these four items is a difference between today's schemas and the redrawn diagrams:
- DIV-004, DIV-007 and DIV-008 record where the *service schemas* diverge from *core* (see docs/conformance/divergence-register.md:164-220). The old paragraph was correct because the old diagrams were drawn from core. The new diagrams were drawn from this repo's schemas plus the ADRs, so they agree with the service on all three.
- The diagrams draw every one of these the same way today's schemas do: `pack_version : string [every level]` (pack-write-model.puml:21, descriptor-governance.puml:16); `pack_level` optional (`- pack_level`, pack-write-model.puml:25, descriptor-governance.puml:20); `Edge.source/target : Urn` (pack-write-model.puml:64-65); `OrgFragment` "no header fields" (pack-write-model.puml:47).
- Listing "`pack_level` is optional" as a difference implies that the decided design makes it required. That is ADR-0048 OQ7, which every diagram marks `[pending ADR-0048 OQ7: required?]`.

Fix: delete the "Today's schemas differ in these ways ..." sentence and its list. If you keep a pointer to the register, make it accurate. For example: "The service's schemas already differ from core in places (DIV-004, DIV-007, DIV-008), and the diagrams follow the service there. Whether `pack_level` becomes required is still open (ADR-0048 OQ7). See the Divergence register." Do not imply any decided change to `pack_level`.

## Non-blocking (fix while you are in the file)

- [LOW] descriptor-schemas.md:132 (caption of the new lifecycle diagram) names only ADR-0050. The diagram's title cites ADR-0050 and ADR-0051, and it draws the event registry. Name both ADRs.
- [LOW] descriptor-schemas.md:79-80 lists "a not-activatable event registry" among the decided kinds. The diagram marks that `[pending ADR-0051 OQ1]`. Add "(activation pending ADR-0051 OQ1)", or drop the qualifier.
- [LOW] descriptor-schemas.md:64-66 still says the `mission-type` schema "is not drawn yet". The Mission type section now embeds a decided-design diagram. Say that today's `mission-type` schema is not drawn, and that its decided design is drawn in Mission type.
- [LOW] descriptor-schemas.md:220 (regeneration command) renders only `descriptor-*.puml pack-write-model.puml`. This mission also changed `class-diagram.puml` and `c4-components.puml`, and the CI job checks every diagram. Use `*.puml`, or name every file.
- [LOW] The "Yellow «pending» classes and `[pending ADR-00xx OQn]` marks ..." sentence is repeated verbatim in six notes, and several of those lines run past the wrapping width of the surrounding text. Consider saying it once in the intro and wrapping the lines.

## Verified OK

- Every R-06 page-text location is addressed: the source lines, the three "Decided, not built yet" notes, the regeneration section, the descriptors.md status note and the five pointers, and the :31-33 intro.
- The ADRs cited in each note match its diagram's title.
- Gate kind, `term:` and `end` are not presented as decided.
- The regeneration section matches plantuml_pins.json (1.2026.6), Java 21, Graphviz and the "PlantUML diagrams are current" job. It also states the build-time render service correctly (jetty-v1.2026.9, pinned by digest).
- The architectural-patterns-applied.md wording matches class-diagram.puml:401. There is no "#150" text.
- The changelog follows the house style. Its description is 125 characters. These claims are verified: 23 schemas, /reference/api/main/, DocKitty 0.5.0 to 0.6.0, five redrawn diagrams.
- check_docs.py all, markdownlint-cli2 0.18.1 and cspell 9.2.0 are clean.
- Scope is limited to the four owned files. C-001 holds.
