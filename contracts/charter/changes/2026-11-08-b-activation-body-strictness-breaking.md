### Breaking

- The `422 activation_invalid` of a duplicate member (`duplicate_member`) and of a scalar of another type than the one declared (`unreadable_body`) on `createActivation` and `replaceActivation`, entered as `### Changed` in `2026-11-06-activation-body-strictness.md`, is breaking: a body that used to be accepted is refused. Read it as breaking; core's seam sends each member once and a JSON value of the declared type.
