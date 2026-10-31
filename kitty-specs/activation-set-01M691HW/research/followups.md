# Follow-ups of the activation set (#125)

Found during the mission and left out of its scope. Each is filed by the orchestrator (sibling
writes and new issues go through it) and replaces its placeholder here.

1. **Build the activation writes** — `charter#TBD-activation-writes`. Draft:
   `draft-issue-build-activation-writes.md`. The nine write operations of ADR-0020 D3/D6, their
   storage port and use cases; the six reserved refusal codes become raised.
2. **A `null` list on a registration body** — `charter#TBD-null-registration-list`. With
   `containerDefaultToNull`, an explicit `"references": null` on a directive, paradigm,
   styleguide or toolguide registration is now treated as `[]` (`OptionalLists.orEmpty`); before,
   it reached `List.copyOf` and answered 500. The contract says `null` is never a value, so it
   should be `422 validation_failed`. (WP03 review, finding 8.)
3. **DELETE relations on activation links** — when the writes are built, `Link.method` widens
   from `GET|POST|PUT` to include `DELETE` (remove, unselect, re-activate). Additive. Agreed with
   #126 through the orchestrator.
4. **`x-refusal` drift** — `charter#TBD-x-refusal-drift`. `CharterRefusalCode.yaml` says
   `governance_policy_violation` and `drg_schema_error` are `raised: false`, but
   `PackMutabilityPolicy` and the registration services raise the first, and `WholePackStore`
   the second. Fix the map, then let `ReservedRefusalCodesTest` read every `raised: false` code
   from the contract instead of a list (WP04 review, finding 5). Older than #125.
5. **Bundle size** — the inlined bundle core vendors grew from about 2.5 MB to 5.2 MB with the
   activation set; snakeyaml's default 3 MiB limit and vacuum's 5 s rule timeout were raised
   where the build reads it. A `components`-preserving bundle would cut the duplication.
