# Contract changelog fragments

A contract change adds one file here instead of editing `../CHANGELOG.md` (ADR-0047). Every contract
pull request used to edit the same `## <version>-SNAPSHOT` section, so parallel pull requests conflicted
there. A fragment is the CHANGELOG line that the governance asks of every contract change, scenario
changes included. It lands in `CHANGELOG.md` when the version is released.

## Writing a fragment

- Name it `YYYY-MM-DD-<slug>.md`, dated the day you write it. The slug is lowercase kebab case.
- Write exactly one `###` block in it: the heading, then the text, in the CHANGELOG's own style.
  - **To add to an existing subsection** of the top version section (`### Breaking`, `### Added`,
    `### Changed`, `### Removed`, `### Provisional`, ...), use that heading. The text goes to the top of
    that subsection.
  - **Any other heading** becomes a subsection of its own, right after `### Breaking`. Use this for a
    change described by its own paragraph, such as `### One wording for a pack file that is not UTF-8
    (issue #273)`.
- A breaking change uses `### Breaking`, and says what core's seam has to change (ADR-0022). The
  `### Breaking` heading itself stays in `CHANGELOG.md`, so the release gate reads it as before.

```markdown
### Added

- `getOffering` answers `ETag` and honours `If-None-Match` (issue #321).
```

## Releasing

The release pull request runs `./gradlew :contracts:assembleContractChangelog`. It inserts every
fragment into the top version section, newest first, and deletes the fragment files. Review the
diff and commit it with the version bump (`contracts/lint/README.md`, "Releasing a contract
version"). `:contracts:checkReleaseTag` refuses a release tag while any fragment is left here.
