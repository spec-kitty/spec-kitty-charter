# Execution Lanes

## Overview

Execution lanes are Spec Kitty's default implementation workspaces for missions with more than one execution lane. Each execution lane is a lane branch. Its work packages run in sequence in a lane worktree that `spec-kitty implement` provides, so agents can implement, test, review, and hand off changes without mutating another lane's checkout.

By default, with no explicit `--topology`, small missions with at most two work packages or one lane use `single_branch` and need no lane worktree. `finalize-tasks` chooses the topology from the mission size.

## Enter and work in a lane

- Let `spec-kitty implement` create or enter the lane worktree. Do not create or reuse a lane worktree by hand.
- Run the work packages assigned to a lane in sequence on that lane branch.
- Keep implementation, verification, review, and handoff inside the checkout provided for the current workflow action.
- Use targeted staging and explicit agent commit attribution before review.

## Status and integration

Mission status lives on the mission's resolved status surface, not in a lane checkout. Lane checkouts never change or serve that status surface.

Shared mainline state is reached only through explicit review, consolidation, and quality gates. Relevant tests and type checks must pass before handoff and integration.

## Cautions

- Do not treat an execution lane as a project branching paradigm. Use the selected branching procedure or paradigm separately.
- Do not edit mission status through a lane checkout.
- Do not create lane worktrees for small missions whose selected topology is `single_branch`.
