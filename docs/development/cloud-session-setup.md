---
title: Set up a Claude Code cloud session
description: Prepare a Claude Code cloud environment for this repository — Java 25, allowed domains, per-session steps — and run several cloud sessions in parallel.
doc_status: draft
updated: 2026-11-01T00:00:00Z
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
| `maven-central.storage-download.googleapis.com` | Google's Maven Central mirror, used by the optional mirror below to avoid HTTP 429 |
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
npm install -g @colbymchenry/codegraph@1.6.2
codegraph install --yes --target claude --location global || true
command -v uv >/dev/null || curl -LsSf https://astral.sh/uv/install.sh | sh

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

The script installs the session tooling (CodeGraph for code navigation, and `uv` for the Spec Kitty
CLI), then the latest Temurin 25 build where Gradle looks for installed JDKs on Linux
(`/usr/lib/jvm`), so the toolchain resolver finds it without downloading anything. It is safe to run
again: it does nothing when the JDK is already there.

The script was run end to end in a cloud session on 2026-10-27 with the domains above allowed
(Temurin 25.0.5+8). If it fails, see [Troubleshooting](#troubleshooting).

### Optional: resolve Maven Central through a mirror

Maven Central rate-limits by address (HTTP 429), and the sessions of one environment share an
address. Every fresh checkout (a lane worktree included) downloads the build-logic, Spotless and
Checkstyle tooling again, so builds stall. Add this to the setup script to resolve Maven Central
through Google's mirror, which serves the same artifacts. The build's strict SHA-256 dependency
verification still checks every artifact. The init script lives in `~/.gradle/init.d/`, outside the
repository, and changes no repository file.

```bash
mkdir -p ~/.gradle/init.d
cat > ~/.gradle/init.d/central-mirror.gradle <<'EOF'
// Resolve Maven Central through Google's mirror (same artifacts) to avoid Central's per-address HTTP 429.
// The build's strict SHA-256 dependency verification still checks every artifact.
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
def centralHosts = ['repo.maven.apache.org', 'repo1.maven.org']
// Central's own URL becomes the mirror; the mirror also goes first, ahead of the plugin portal, which redirects its
// Central artifacts to Central server-side.
def useMirror = { RepositoryHandler repos ->
    repos.withType(MavenArtifactRepository).configureEach { r ->
        if (r.url?.host in centralHosts) { r.url = mirror }
    }
    if (repos.findByName('CentralMirror') == null) {
        def m = repos.maven { url = mirror; name = 'CentralMirror' }
        repos.remove(m)
        repos.addFirst(m)
    }
}
beforeSettings { settings ->
    useMirror(settings.pluginManagement.repositories)
    // Declaring a plugin repository drops the implicit portal, so it is added back after the mirror.
    settings.pluginManagement.repositories.gradlePluginPortal()
}
settingsEvaluated { settings ->
    useMirror(settings.pluginManagement.repositories)
    if (!settings.dependencyResolutionManagement.repositories.isEmpty()) {
        useMirror(settings.dependencyResolutionManagement.repositories)
    }
}
allprojects {
    afterEvaluate {
        if (!buildscript.repositories.isEmpty()) { useMirror(buildscript.repositories) }
        if (!repositories.isEmpty()) { useMirror(repositories) }
    }
}
EOF
# Warm the Gradle cache (build-logic, Spotless and Checkstyle tooling included) so worktrees start warm.
cd spec-kitty-charter && JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon -PopenPacksDir=../spec-kitty-open-packs \
  build spotlessCheck checkstyleMain checkstyleTest -x test || true
```

- The mirror goes **first** in every repository list. Rewriting Central's URL alone is not enough:
  `build-logic` resolves through `gradlePluginPortal()`, which redirects Central artifacts to
  `repo.maven.apache.org` on its own server, out of reach of a URL rewrite.
- The `pluginManagement` block must re-add `gradlePluginPortal()`: declaring any plugin repository
  drops the implicit one, and plugin resolution then fails.
- Allow `maven-central.storage-download.googleapis.com` (Step 1).
- Run in a #195 session on 2026-10-28: `./gradlew verifyAll` passed on Java 25 with no 429. It is
  not yet known whether the environment caches the setup script's result between sessions; if it
  does not, the warm-up runs once per session, before work starts.

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
   `../spec-kitty-open-packs` (or `-PopenPacksDir=<path>`: an absolute path, or one relative to the
   repository root) and stops on a checkout that is missing, not clean or not at `openPacksRef` in
   `gradle.properties`. It names the directory it looked in, and for an unclean or unpinned
   checkout it prints the command that fixes it.
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
- **Maven Central answers HTTP 429.** It rate-limits by address. Use the
  [Maven Central mirror](#optional-resolve-maven-central-through-a-mirror); otherwise wait and
  retry. Gradle's cache keeps what it already downloaded.
- **PlantUML diagrams render differently from CI.** Even with the pinned jar, the fonts in a cloud
  container differ from CI's, so a locally re-rendered SVG fails the "PlantUML diagrams are
  current" check. Re-render diagrams in CI's environment.

## Running several cloud sessions (cloud runners)

An orchestrating session can hand Missions to several child cloud sessions ("cloud runners") at
once. These lessons come from parallel runs on 2026-10-27 and 2026-10-28.

### What can go wrong

- **The permission classifier can refuse a setup step the operator approved.** In auto mode, a
  child session's classifier refused, even after the operator approved the step in that session's
  chat:
  - `add_repo` of `spec-kitty-open-packs` with `push` access;
  - `uv tool install --force -e ../spec-kitty` (installing the CLI from a core checkout), refused
    as code from an external source.
- **The refusals are inconsistent.** In the same environment, one sibling session installed and
  ran the CLI while another was blocked.
- **A blocked session does not recover.** It does not pick up later changes to the environment.
  Replace it with a new session; do not keep retrying in the old one.

### Mitigations

- **Install the tooling in the setup script, not per session.** The Spec Kitty CLI and CodeGraph
  go in the environment setup script (Step 2), so no child session has to install anything.
- **Clone sibling repositories read-only.** A plain `git clone` of a public repository is enough
  for `spec-kitty-open-packs` and core; do not ask for push access to them.
- **Route writes to sibling repositories through the orchestrator.** A child session that cannot
  write to a sibling repository (for example, to file an open-packs issue for a pack divergence,
  ADR-0036 decision 5):
  1. writes each issue as a draft in its Mission's research directory and lists the drafts in the
     PR hand-off;
  2. keys any known-failure entry with a placeholder, `open-packs#TBD-<slug>`.

  The orchestrating session, which has the sibling repository attached, files the issues and
  replaces the placeholders before the PR merges.
- **Run a missing review skill by hand.** If a skill the brief names (for example
  `adversarial-squad`) is not installed, run the squad as parallel subagents, one per lens, each
  with its profile loaded by `spec-kitty agent profile show <id>`.

### Setup-script addition for the CLI

Add this to the setup script after the `uv` line. It is **untested** as a setup script; the
PyPI install itself is the fallback the sessions above used.

```bash
export PATH="$HOME/.local/bin:$PATH"
if ! command -v spec-kitty >/dev/null; then
  # Option A: from a core checkout (untested in a setup script)
  #   git clone --depth 1 https://github.com/spec-kitty/spec-kitty.git /opt/spec-kitty
  #   uv tool install --force -e /opt/spec-kitty
  # Option B: the PyPI release
  uv tool install --prerelease allow spec-kitty-cli
fi
spec-kitty --version
```

### Brief checklist for orchestrators

- [ ] Tell the session to run `command -v spec-kitty` first; if it is missing, install the PyPI
      release (`uv tool install --prerelease allow spec-kitty-cli`), and if that fails, **report**
      rather than try other install routes.
- [ ] Name the sibling repositories to clone read-only; grant no push access to them.
- [ ] Say where draft issues go (the Mission's research directory) and the placeholder form
      `open-packs#TBD-<slug>`.
- [ ] Pick the ADR number when the ADR is written, not when the session is dispatched (see below).
- [ ] Name the fallback for any skill the brief relies on.

### Troubleshooting parallel runs

- **ADR numbers collide.** Parallel sessions pick the same next ADR number. Check `main` *and* the
  open PRs for the next free number at writing time.
- **Two PRs conflict on an index.** They should not: no PR edits `docs/adr/README.md` or
  `docs/changelog/README.md`, whose lists are generated
  ([ADR-0047](/adr/0047-generated-indexes-and-merge-queue/)). Drop the hub edit from the PR.
- **The docsite build fails on `related:`.** The CI site build is stricter than
  `scripts/docs/check_docs.py`: every `related:` entry must be the slug of a real page.
  `adr/README` is not one (PR #246 failed on it). Run the site build, or check that each slug maps
  to a file under `docs/`.
- **A sibling checkout is dirty with mode-only changes.** A local tool can make scripts
  executable, which the build reads as a dirty `spec-kitty-open-packs` checkout. Restore the modes
  (`git diff --summary` lists them; `git checkout -- <path>` restores them); do not commit them.
- **Maven Central answers HTTP 429.** Several sessions in one environment share an address. Use
  the [Maven Central mirror](#optional-resolve-maven-central-through-a-mirror) in the setup script.

## See also

- [Building the service](/guides/service-build/)
- [Contributing](/guides/contributing/)
- [The docsite](/guides/docsite/)
