---
title: Set up a Claude Code cloud session
description: Prepare a Claude Code cloud environment for this repository, with Java 25, the domains to allow and the per-session steps before a Mission or an Op.
doc_status: draft
updated: 2026-10-27T00:00:00Z
type: Guide
kind: How-To
related:
  - guides/service-build
  - guides/contributing
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Follow this before you run a Mission or an Op in a cloud session, so the build, the docs checks and the contract suite run locally.
---

# Set up a Claude Code cloud session

This page is for contributors who run Missions or Ops on this repository in a Claude Code cloud
session. When you finish, the session can run what CI runs: the Gradle build on Java 25
(`./gradlew verifyAll`), the contract suite, and the docs checks.

A cloud session needs two things the default container does not give it:

1. **Java 25.** The build never downloads a toolchain
   (`org.gradle.java.installations.auto-download=false`, see
   [Building the service](/guides/service-build/)), and the default container ships only Java 21.
2. **Network access to the hosts the build reads.** The default network policy refuses the Java
   distributions and may refuse other hosts the build needs.

Both are settings of the cloud environment, outside the repository. Change them once; every new
session picks them up.

## Step 1: Allow the domains

1. Open the environment menu in the session title bar.
2. Choose **Edit**, then **Network access**.
3. Choose the **Limited** (or **Custom**) level, keep **Allow package managers** ticked, and add
   these domains under **Allowed domains**:

| Domain | Needed for |
| --- | --- |
| `api.adoptium.net` | Finding the current Temurin JDK 25 build and its checksum (CI uses Temurin 25) |
| `github.com` | The JDK download (Adoptium publishes its binaries as GitHub releases) and the pinned PlantUML jar |
| `objects.githubusercontent.com` | GitHub release downloads |
| `release-assets.githubusercontent.com` | GitHub release downloads |
| `services.gradle.org` | The Gradle wrapper distribution |
| `downloads.gradle.org` | The Gradle wrapper distribution (redirect target) |
| `plugins.gradle.org` | Gradle plugins (`gradlePluginPortal()`) |
| `plugins-artifacts.gradle.org` | Gradle plugin artifacts |
| `repo.maven.apache.org` | Maven Central (`mavenCentral()`) |
| `repo1.maven.org` | Maven Central |
| `spec-kitty.github.io` | The Spec Kitty org docsites (this repository's docsite and the others) |

The npm and PyPI registries the docs checks use (`npx cspell`, `npx markdownlint-cli2`, `pip`) are
covered by **Allow package managers**. The docsite build itself (`cd site && npm ci`) also needs a
token for `npm.pkg.github.com`; it runs in CI, and a cloud session does not need it.

The steps for network access are at
[cloud environments: network access](https://code.claude.com/docs/en/cloud-environments#network-access).

## Step 2: Add the environment setup script

In the same environment settings, choose **Setup script**, paste this and save:

```bash
#!/bin/bash
set -euo pipefail

# Java 25 (Temurin, as in CI). The checksum comes from the same API as the download, so it guards
# against a broken transfer, not against a compromised source.
JDK_DIR=/usr/lib/jvm/temurin-25
if [ ! -x "$JDK_DIR/bin/java" ]; then
  meta=$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse")
  link=$(printf '%s' "$meta" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["binary"]["package"]["link"])')
  sum=$(printf '%s' "$meta" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["binary"]["package"]["checksum"])')
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/jdk.tar.gz" "$link"
  echo "$sum  $tmp/jdk.tar.gz" | sha256sum -c -
  mkdir -p "$JDK_DIR"
  tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm -rf "$tmp"
fi
"$JDK_DIR/bin/java" -version
```

The script installs the latest Temurin 25 build where Gradle looks for installed JDKs on Linux
(`/usr/lib/jvm`), so the toolchain resolver finds it without downloading anything. It is safe to run
again: it does nothing when the JDK is already there.

This script has not yet been run end to end in a cloud environment: it was written while the
domains above were still refused. If it fails, see [Troubleshooting](#troubleshooting).

### Optional: environment variables

Under **Environment variables**, you can set:

```text
JAVA_HOME=/usr/lib/jvm/temurin-25
SPEC_KITTY_NO_NAG=1
SPEC_KITTY_NO_UPGRADE_CHECK=1
```

- `JAVA_HOME` makes Gradle itself run on Java 25. Gradle finds the toolchain in `/usr/lib/jvm`
  either way.
- `SPEC_KITTY_NO_NAG=1` and `SPEC_KITTY_NO_UPGRADE_CHECK=1` quiet the `spec-kitty` CLI's version
  banner and its PyPI check.

## Step 3: Prepare each session

From the repository root:

1. **Install the Spec Kitty CLI**, from a core checkout or from PyPI:

   ```bash
   uv tool install --force -e ../spec-kitty   # or: uv tool install --prerelease allow spec-kitty-cli
   ```

2. **Check out `spec-kitty-open-packs` at the pinned commit.** The build reads it from
   `../spec-kitty-open-packs` (or `-PopenPacksDir=<path>`) and refuses a checkout that is not clean
   or not at `openPacksRef` in `gradle.properties`; it prints the command that fixes it.
3. **Fetch the full history and tags.** The contract's breaking-change check compares against the
   last released contract tag: `git fetch --unshallow --tags origin` (or `git fetch --tags` when
   the clone is already complete).

## Step 4: Check the result

```bash
java -version                              # 25 when JAVA_HOME is set; else /usr/lib/jvm/temurin-25/bin/java -version
./gradlew verifyAll                        # the build and every check CI runs
python3 scripts/docs/check_docs.py all     # the docs checks
```

## Troubleshooting

- **`curl: (56) CONNECT tunnel failed, response 403`.** A domain in Step 1 is still refused. The
  error names the host; add it under **Allowed domains**.
- **`Cannot find a Java installation … matching {languageVersion=25}`.** The setup script did not
  install the JDK. Check its output in the environment build log, or run it by hand in the session.
  Until Java 25 is there, `./gradlew -Pcharter.javaVersion=21 …` builds and tests on the container's
  Java 21; CI always builds on 25.
- **Maven Central answers HTTP 429.** It rate-limits by address; wait and retry. Gradle's cache
  keeps what it already downloaded.
- **PlantUML diagrams render differently from CI.** Even with the pinned jar, the fonts in a cloud
  container differ from CI's, so a locally re-rendered SVG fails the "PlantUML diagrams are
  current" check. Re-render diagrams in CI's environment.

## See also

- [Building the service](/guides/service-build/)
- [Contributing](/guides/contributing/)
- [The docsite](/guides/docsite/)
