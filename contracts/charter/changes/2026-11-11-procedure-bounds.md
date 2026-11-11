### Changed

- The `Procedure` schema states the id and text bounds the pack schema puts on a procedure (#489). Its `id` carries
  the pack schema's pattern and `maxLength: 64`. Its `title`, `description`, `entryCondition`, `exitCondition` and
  `notes` have `minLength: 1`, and so does the `title` of a step and of an anti-pattern. Every procedure the service
  serves already meets them. Core's seam: nothing.
