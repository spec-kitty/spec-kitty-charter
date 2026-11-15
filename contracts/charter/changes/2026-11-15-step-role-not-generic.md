### Changed

- A mission step's `role` is never the reserved `generic` and is at most 64 characters (ADR-0068 decision 4, #446),
  in the pack schema `mission-type.schema.json` (`not: {const: generic}`, `maxLength: 64`, reported at
  `/steps/<n>/role`), in the step's domain rules, and in `MissionStep.role` of the contract. A step never asks for the
  role that marks a fallback profile, so the step-keyed selection never reads it.
