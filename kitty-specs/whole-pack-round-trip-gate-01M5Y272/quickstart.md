# Quickstart: Whole-pack round-trip gate

```bash
# The open-packs checkout at the pin (see docs/development/cloud-session-setup.md)
git -C ../spec-kitty-open-packs checkout "$(grep openPacksRef gradle.properties | cut -d= -f2)"

./gradlew :packs:wholePackRoundTrip          # the gate (not part of verifyAll)
cat service/packs/build/reports/whole-pack/report.md
```

## When your mission turns a kind green

1. Implement the kind's read/write and extend the round-trip adapter.
2. Run the gate. It fails with `BASELINED_MODELLED_KIND <kind>: a modelled kind is never expected red as a whole; remove it from …expected-red.yaml …`.
3. Remove the entry (and any per-file entries that now pass), run again, commit both.

## When a modelled file goes red

The gate fails with `UNEXPECTED RED: <path> (<kind>) at <difference path>`. Fix the code, or — if the pack itself
is inconsistent — fix it upstream and add a `files:` entry with the upstream issue until the pin moves.
