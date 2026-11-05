# Data Model: Whole-pack round trip closeout

No persisted data changes. The values affected:

- **StepDocument** (`:missions`): title, description, preamble, sections. New invariants at construction:
  title passes the heading rule (when non-empty); preamble passes the block rule; with neither title nor
  description, the preamble passes the opening rule. Breach: `ConstraintBreachException`.
- **StepSection** (`:missions`): title passes the heading rule; content passes the block rule.
- **Line rules** (`kernel.markdown`): pure functions from text to a problem description or none.
- **Pack root in validate mode**: the configured locator, read as its absolute normalized path; the `PackRoot` value
  reported back is unchanged.
