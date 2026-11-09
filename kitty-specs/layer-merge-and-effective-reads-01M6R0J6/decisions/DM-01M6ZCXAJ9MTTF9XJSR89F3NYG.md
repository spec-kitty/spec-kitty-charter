# Decision Moment `01M6ZCXAJ9MTTF9XJSR89F3NYG`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `a1-union-levels-only`
- **Input key:** `a1-union-levels-only`
- **Status:** `resolved`
- **Created:** `2026-11-09T05:36:17.481736+00:00`
- **Resolved:** `2026-11-09T05:36:19.451897+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

Does ruling A1's union over every governing copy include a same-level parent copy that a child pack's preset replaces? main pins that the replaced parent copy requires nothing.

## Options

- union across levels only; within a level the replacing copy wins and the replaced copy requires nothing
- union includes replaced parent copies (Breaking)
- writes stay winners-only

## Final answer

union across levels only; within a level the replacing copy wins and the replaced copy requires nothing

## Rationale

Operator, 2026-11-09. A1's union (DM-01M6RA7FHRC5K8QPX7KN5FZ4S8) is over the copy in force at each level (built-in, org, project), not over every copy: within one level a child pack's preset replaces its parent's (the 2026-11-06 behaviour on main stands, its scenario unchanged). The reads, the write-side floor refusal and the registration floor notices all follow this one rule (01M6YKZRM7VF19A57E40D59N81 still applies, with this scope).

## Change log

- `2026-11-09T05:36:17.481736+00:00` - opened
- `2026-11-09T05:36:19.451897+00:00` - resolved (final_answer="union across levels only; within a level the replacing copy wins and the replaced copy requires nothing")
