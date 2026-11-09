# Quickstart: the usability run

```bash
# from the repository root, with open-packs cloned beside it at the pin
JAVA_HOME=/usr/lib/jvm/temurin-25 scripts/usability/service-usability-run.sh \
  --open-packs ../spec-kitty-open-packs --profile java-jenny
```

The script performs these steps in order:

1. Builds `:deployable:bootJar`.
2. Starts the service on a free loopback port, with registration and the filesystem pack source on.
3. Copies `built-in` into `<config-root>/packs/`, and registers `internal`, `qa`, `spdd`, `event-driven` and
   `cave-kitty` with `POST /packs`.
4. Registers this repository's project pack.
5. Translates `.kittify/charter/charter.yaml`, plus `agent_profile: java-jenny`, into an activation document.
6. From `GET /api/v1/` on, follows only `_links` (filling templated variables the link declares):
   - root → `artifacts` (pack `built-in`, `kind=agent_profile`) → `java-jenny` → each governance entry;
   - root → `activations` (POST) → the activation → `artifacts` → the `java-jenny` item → `effective-artifact` →
     each governance entry → `artifact`.
7. Times 20 profile reads (p95 under 500 ms).

It exits `0` only if every hop answered `2xx`. The last line prints a summary of the hops followed, those that
failed, and the p95.

Pass `--keep` to leave the service running for manual exploration.
