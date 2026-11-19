# Quickstart: Serve tensions

1. A pack writes a tension in a graph document, smaller URN first:

   ```yaml
   edges:
     - source: directive:DIRECTIVE_024
       target: directive:DIRECTIVE_025
       relation: tension
       reason: scope against speed
     - source: directive:DIRECTIVE_025
       target: directive:DIRECTIVE_024
       relation: requires
     - source: tactic:RECONCILE_CHANGE_SCOPE
       target: directive:DIRECTIVE_024
       relation: reconciles_tension
     - source: tactic:RECONCILE_CHANGE_SCOPE
       target: directive:DIRECTIVE_025
       relation: reconciles_tension
   ```

2. Read an activation's tensions: `GET /api/v1/activations/{id}/tensions`. A tension with an empty `reconciledBy`
   is also a finding `unreconciled_tension`, with `resolutions: [reconcile, deactivate_one_side]` and links to exclude
   each side.
3. `GET /api/v1/activations/{id}/graph` answers the same tension as an edge `tension` with `reconciledBy`.
4. `GET /api/v1/activations/{id}/mission-types/{mt}/actions/{a}/context` answers `tensions` for the pairs whose two
   sides the context lists in force.
5. Register one: `PUT /api/v1/packs/{packId}/relationships` with `{"from": ..., "to": ..., "type": "tension"}`;
   remove only it: `DELETE ...?from=..&to=..&relation=tension`.
