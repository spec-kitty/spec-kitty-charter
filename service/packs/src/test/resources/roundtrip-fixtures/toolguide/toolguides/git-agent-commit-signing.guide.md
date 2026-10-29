# Git Agent Commit Signing

## Overview

An agent signs every commit it makes, so a reviewer can tell agent work from human work.
The wording follows the [commit-message styleguide](../styleguides/commit-messages.styleguide.yaml).

## When to use

Whenever an agent commits on a branch a human will review.

## Usage

```bash
# sign with the agent's key
git commit -S -m "feat: add the thing"
```

## Examples

### A signed merge

```bash
git merge --no-ff -S feature
```

## Cautions

- Never sign with a human's key.

## References

- https://git-scm.com/book/en/v2/Git-Tools-Signing-Your-Work
