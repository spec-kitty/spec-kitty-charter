# Quickstart: verify this mission locally

## Render and check the architecture diagrams (pinned jar, C-003)

Graphviz is not installed on the workstation, so render inside a container with the repository's pinned jar:

```bash
jar=$(python3 -c "import json;print(json.load(open('scripts/docs/plantuml_pins.json'))['plantuml_jar_url'])")
sha=$(python3 -c "import json;print(json.load(open('scripts/docs/plantuml_pins.json'))['plantuml_jar_sha256'])")
curl -fsSL -o /tmp/plantuml.jar "$jar" && echo "$sha  /tmp/plantuml.jar" | sha256sum -c
docker run --rm -v "$PWD":/w -v /tmp/plantuml.jar:/plantuml.jar -w /w/docs/architecture/diagrams ubuntu:24.04 \
  bash -c 'apt-get update -qq && apt-get install -y -qq graphviz openjdk-21-jre-headless >/dev/null && java -Djava.awt.headless=true -jar /plantuml.jar -tsvg <changed>.puml'
PLANTUML_JAR=/tmp/plantuml.jar python3 scripts/docs/check_docs.py plantuml   # or run it inside the same container
```

Mirror the `PlantUML diagrams are current` job in `.github/workflows/docs-cleanliness.yml` exactly (Graphviz package, Java version, flags). A new diagram's `@startuml` name must equal its file stem.

## Docs gates

```bash
python3 -m scripts.docs.check_docs all
npx --yes markdownlint-cli2@<version from docs-cleanliness.yml> "<changed files>"
npx --yes cspell@<version from docs-cleanliness.yml> "<changed files>"
```

## Site build (CI is authoritative)

GitHub Packages needs a `read:packages` token, so the site build, validate, built-link, OKF and schematic-diagram checks run in the `Docs site` workflow on the PR. Locally, with a token (bundle the contract first):

```bash
./gradlew :contracts:bundleContract && cd site && npm ci && npm run validate && DK_DIAGRAM_BUILD_RENDER=build DK_PLANTUML_SERVER_URL=http://localhost:8091/svg/ npm run build \
  && npm run check:built-links && npm run check:okf
```

Run the render service with the pinned image digest from research.md R-04, on port 8091.
