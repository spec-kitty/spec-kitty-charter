# Decision Moment `01M710DQEMV7VPKDNQPWKPGD4F`

- **Mission:** `agent-profile-reads-01M70ZDM`
- **Origin flow:** `plan`
- **Slot key:** `plan.activations.body`
- **Input key:** `activation_body`
- **Status:** `resolved`
- **Created:** `2026-11-09T20:36:32.340049+00:00`
- **Resolved:** `2026-11-09T20:36:35.288730+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What does GET /activations/{id}/artifacts/{kind}/{artifactId} answer, given each body's JSON mapping lives in its own slice's inbound adapter?

## Options

_(none)_

## Final answer

The winner with links to the pack reads; the body may be embedded too. The shared surface between slices is the application (service) layer, not the REST layer; one application service may be wired to several endpoints.

## Rationale

_(none)_

## Change log

- `2026-11-09T20:36:32.340049+00:00` - opened
- `2026-11-09T20:36:35.288730+00:00` - resolved (final_answer="The winner with links to the pack reads; the body may be embedded too. The shared surface between slices is the application (service) layer, not the REST layer; one application service may be wired to several endpoints.")
