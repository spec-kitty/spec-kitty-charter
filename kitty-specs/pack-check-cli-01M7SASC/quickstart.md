# Quickstart: pack-check

```bash
# Download and verify (after service-v0.1.0-rc.2 is published)
gh release download service-v0.1.0-rc.2 --repo spec-kitty/spec-kitty-charter \
  --pattern 'pack-check-*.jar' --pattern SHA256SUMS
sha256sum --check --ignore-missing SHA256SUMS
gh attestation verify pack-check-0.1.0-rc.2.jar --repo spec-kitty/spec-kitty-charter \
  --signer-workflow spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml \
  --source-ref refs/tags/service-v0.1.0-rc.2

# One pack, at a level (a pack that depends on others is checked together with them, as below)
java -jar pack-check-0.1.0-rc.2.jar --pack ./packs/my-org:org

# A set, as a deployment lays it out (<root>/packs/<name>/)
java -jar pack-check-0.1.0-rc.2.jar --config-root ./charter-root

# A set, with the round trip
java -jar pack-check-0.1.0-rc.2.jar --pack ./built-in:built-in --pack ./my-org:org --round-trip
echo $?   # 0 valid, 1 violation, 2 usage or I/O failure
```

From a checkout: `./gradlew :pack-check:packCheckJar`, then `java -jar service/pack-check/build/libs/pack-check-<version>.jar …`.
