---
affected_files: []
cycle_number: 1
mission_slug: container-guard-and-actuator-01M6P99A
reproduction_command:
reviewed_at: '2026-11-05T17:17:25Z'
reviewer_agent: reviewer-renata
wp_id: WP04
---

# WP04 review (reviewer-renata subagent): REJECT

- [MEDIUM] ExposureStartupCheck: spring.mvc.log-request-details refuses only the literal "true"; Spring binds 1/on/yes
  as true, so SPRING_MVC_LOG_REQUEST_DETAILS=1 passes and logs the Authorization header. Fix: judge the bound boolean;
  tests for 1, on, yes, TRUE.
- [LOW] Health groups unchecked: management.endpoint.health.group.<g>.show-details/show-components must stay never.
- [LOW] server.address resolved by DNS; accept localhost and IP literals only.
- [NIT] ProfileDefaultsTest: doesNotContain(TOKEN) proves nothing where no token exists.
