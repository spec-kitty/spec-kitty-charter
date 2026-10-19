# Quickstart: verify this mission

From the repository root, with Temurin 25 and a spec-kitty-open-packs checkout at the
commit pinned in `.github/workflows/service.yml`:

```bash
./gradlew verifyAll -PopenPacksDir=../spec-kitty-open-packs   # every gate, Javadoc included
./gradlew pitest                                             # the nightly mutation run, on demand
```

The charter's own contract checks run inside `verifyAll`; on their own:

```bash
./gradlew :contracts:lintContract :contracts:bundleContract :contracts:checkContractCompatibility
```

Docs checks:

```bash
for c in frontmatter related freshness links terms adr changelog orphans; do python scripts/docs/check_docs.py $c; done
npx --yes markdownlint-cli2@0.18.1
npx --yes cspell@9.2.0 --no-progress
```

Each new gate has a planted-violation proof recorded in its work package: plant, see it
fail, remove.
