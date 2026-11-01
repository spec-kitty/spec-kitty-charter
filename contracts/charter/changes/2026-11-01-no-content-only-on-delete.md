### Changed

- Changed (lint): the new rule `no-content-only-on-delete` lets only a `delete` operation declare a `204` (issue
  #295). A removal is the one success without the resource (ADR-0020 D9); `no-content-has-no-body` already held a
  `204` to having no body, but let it sit on any method. The contract's one `204`, `deleteActivation`'s, is on a
  `delete`, so the contract does not change. Core's seam: none.
