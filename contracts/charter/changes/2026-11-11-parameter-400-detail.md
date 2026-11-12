### Changed

- A 400 for a malformed request parameter stays a `Problem` without a code and now carries a `detail` that names where
  the parameter is (query, path or header), its name, the value it carried and what it accepts (issue #411): for
  example `The query parameter kind is "bogus", which is not one of tactic, directive, ... or mission_step_contract.`,
  `The query parameter pageSize is "0", which is less than 1.` and `The query parameter pageCursor is "***", which is
  not a cursor this list gave.` The value is quoted with its quotes, backslashes and control characters escaped and is
  cut after 64 code points. The pack writes (`packId`, `If-Match`, `If-None-Match`), the relationship removal (`from`,
  `to`) and the activation writes' precondition headers answer the same shape; two headers that exclude each other
  are named together. The wording is the service's own on every route: the activation routes and
  `getEffectiveAgentProfile` answered a validator's message before (`The request's depth must be less than or equal to
  1.`), and every other route a bare 400. The `Problem` schema's `detail` description states the rule; a client
  branches on the status, never on the detail. Scenarios: `artifacts.feature` pins the detail of an unknown, an
  unserved and a malformed kind filter, a bad cursor and a page size out of range or not a number;
  `effective-reads.feature` of the kind and state filters, a malformed cursor and a cursor of another set;
  `agent-profile-reads.feature`, `effective-agent-profile.feature` and `effective-artifact.feature` of a depth outside
  0 to 1; `activation-register-guards.feature` of a malformed `If-Match` and activation id;
  `registration/whole-pack-registration.feature` of a `packId` outside the grammar and both validators sent;
  `pack-removal.feature` of a malformed and a missing end; `mission-types.feature` of a mission type id too long or
  not a slug; `errors.feature` says a 4xx Problem under the API carries a detail only where the service writes one.
  Additive: a member the closed `Problem` already declared appears where it was absent; no operation changes.
