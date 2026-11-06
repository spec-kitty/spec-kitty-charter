---
work_package_id: WP01
title: Complexity limits and the method-scoped register
dependencies: []
requirement_refs:
- FR-001
- FR-002
- NFR-001
- C-001
- C-003
- SC-001
- SC-002
planning_base_branch: ci-sonar-nightly-checkstyle-complexity
merge_target_branch: ci-sonar-nightly-checkstyle-complexity
branch_strategy: Planning artifacts for this mission were generated on ci-sonar-nightly-checkstyle-complexity. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into ci-sonar-nightly-checkstyle-complexity unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: config/checkstyle/
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- config/checkstyle/checkstyle.xml
- config/checkstyle/suppressions.xml
- contracts/src/main/java/ai/speckitty/charter/contracts/bundle/ComposedBundle.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ActivationConfigSchemaParityTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP01: Complexity limits and the method-scoped register

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make complexity a blocking, per-pull-request gate. Checkstyle, already run by `verifyAll` on main and
test sources of every hand-written module, gains four modules with the limits the operator ruled. The
methods that exceed a limit today are listed one by one, by XPath, in the existing single register, so
the build lands green and no new method can take an exception.

Implementation command:
`spec-kitty agent action implement WP01 --agent claude --mission ci-sonar-nightly-checkstyle-complexity-01M6HQ46`

## Branch Strategy

- Planning branch: `ci-sonar-nightly-checkstyle-complexity`.
- Final merge target: `ci-sonar-nightly-checkstyle-complexity`, which reaches `main` by pull request.
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by
  hand.

## Context

- **Spec:** FR-001, FR-002, SC-001, SC-002, C-001 (no new plugin or library), C-003 (one register).
- **Decisions:**
  - `01M6HQ9K8ZPB1W0PS5CNDEE9WR`: the limits.
  - `01M6JHQW6K8TH51XPHZK5VNJNB`: the register is method-scoped by XPath.
- **Research:** `research.md` R1 (the distribution) and R2 (the spike that proved the XPath entry and
  its negative control).
- **Environment:**
  - `JAVA_HOME=/usr/lib/jvm/temurin-25`.
  - Open-packs must be checked out at `openPacksRef`; see `docs/development/cloud-session-setup.md`.
  - If `~/.gradle/init.d` holds a broken mirror script, use `GRADLE_USER_HOME` with only the mirror's
    Groovy body.
- **Register convention** (header of `config/checkstyle/suppressions.xml`):
  - each entry carries `rule:`, `file:`, `reason:` and `sunset:` in the comment directly above it;
  - the header states `Size: N entries`.

  WP02 builds the check that enforces this, so follow it exactly.

### Subtask T001: Add the four modules and the XPath filter

**Purpose:** the limits (FR-001).

**Steps:**
1. In `config/checkstyle/checkstyle.xml`, inside `TreeWalker`, add:
   ```xml
   <module name="CyclomaticComplexity">
       <property name="max" value="15"/>
       <property name="switchBlockAsSingleDecisionPoint" value="true"/>
   </module>
   <module name="NPathComplexity">
       <property name="max" value="1000"/>
   </module>
   <module name="MethodLength">
       <property name="max" value="60"/>
       <property name="countEmpty" value="false"/>
   </module>
   <module name="NestedIfDepth">
       <property name="max" value="1"/>
   </module>
   <module name="SuppressionXpathFilter">
       <property name="file" value="${config_loc}/suppressions.xml"/>
       <property name="optional" value="false"/>
   </module>
   ```
   Keep the existing top-level `SuppressionFilter` on the same file. Both read it (C-003). Place the
   modules where the file's grouping and comments make sense, and add a one-line comment naming
   ADR-0056.
2. In `config/checkstyle/suppressions.xml`, switch the DOCTYPE to
   `-//Checkstyle//DTD SuppressionXpathFilter Experimental Configuration 1.2//EN` /
   `https://checkstyle.org/dtds/suppressions_1_2_xpath_experimental.dtd`. Keep the header comment and
   extend it: entries for the complexity rules are `suppress-xpath` elements naming one method each.

**Validation:** with the register still empty, `./gradlew checkstyleMain checkstyleTest --continue`
fails only on the methods listed under T002.

### Subtask T002: Measure every over-limit method

**Purpose:** know exactly what the register must hold.

**Steps:**
1. Run `./gradlew checkstyleMain checkstyleTest --continue` with the empty register.
2. Read every `build/reports/checkstyle/{main,test}.xml`. The contracts module writes under
   `build/contracts/reports/checkstyle/`. For each error from the four checks, record the file, the line
   and the method name, read from the source at the reported line.
3. Group the errors by method: one method may break several rules.

**Expected** (measured 2026-11-03 at `d2e1670`; your run is authoritative):
- **CyclomaticComplexity > 15:**
  - `AddressPolicy.java:42` (`isGlobalV4`)
  - `CheckReleaseGate.java:62`
  - `WholePackExpectation.java:255`
  - `GeneratedPackTree.java:512`
  - `DomainRoundTrip.java:128`
- **NPathComplexity > 1000:** the CyclomaticComplexity set plus `MarkdownScanner.java:287` and
  `ReleaseGate.java:204`, minus `GeneratedPackTree`.
- **MethodLength > 60:**
  - `WholePackStore.java:297` (main);
  - 8 test methods, among them `WholePackExpectation.java:255`, `PackFileSetExactnessTest.java:281`,
    `LifecycleTest.java:73`, `FileSystemPackInspectorTest.java:602`, `ReadOnlyPackSaveTest.java:70`
    and a validation-parity test.
- **NestedIfDepth > 1:**
  - `ComposedBundle.java:334, 389, 392`;
  - `ActivationConfigSchemaParityTest.java:194`.

  These are reported per nested `if`, so map them to their methods.

About 15 distinct methods in all. If you find more than 15, stop and report it: SC-002 caps the
register at 15.

### Subtask T003: Write the register

**Purpose:** land green with a shrink-only debt list (FR-002).

**Steps:**
1. Add one `suppress-xpath` entry per method:
   ```xml
   <!--
     rule:   CyclomaticComplexity, NPathComplexity
     file:   service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/AddressPolicy.java
     reason: <one honest sentence: why the method is shaped this way today>
     sunset: 2027-05-04
   -->
   <suppress-xpath checks="CyclomaticComplexity|NPathComplexity"
                   files="AddressPolicy\.java"
                   query="//CLASS_DEF//METHOD_DEF[./IDENT[@text='isGlobalV4']]"/>
   ```
   - `checks` lists exactly the rules this method breaks, no more.
   - `files` is the escaped file name; widen it to a path fragment only if the name is ambiguous.
   - Use `CTOR_DEF` for a constructor. If the method is overloaded, add a parameter predicate, or
     state in the reason that the entry covers every overload.
   - Sunset: six months out, 2027-05-04, unless a shorter date fits.
2. Update `Size: N entries`.
3. Order the entries by file path.

**Validation:** `./gradlew checkstyleMain checkstyleTest` passes.

### Subtask T004: Prove each limit bites

**Purpose:** non-vacuity (SC-001). A gate that never fires proves nothing.

**Steps:**
1. In a scratch copy outside the commit, plant one method per limit in an unlisted class (for example
   in the `kernel` tests):
   - a method of cyclomatic complexity 16;
   - one with more than 1000 paths;
   - one of 61 non-empty lines;
   - one with a nested `if` inside an `if`.
2. Run Checkstyle on that module. Confirm four errors, one per rule, each naming its method.
3. Remove the planted code. Confirm the module passes again.
4. Paste the four error lines, and the pass, into the WP's evidence: the review notes or the PR body.

### Subtask T017: Flatten the three nested-if methods (operator ruling 2026-11-04)

**Purpose:** the real measurement found 18 over-limit methods, against SC-002's cap of 15. The operator
ruled to refactor the three cheapest methods rather than raise the cap (decision recorded in
`decisions/`). They are the three NestedIfDepth cases:
- `ComposedBundle.checkReferences` and `ComposedBundle.reaches` in
  `contracts/src/main/java/ai/speckitty/charter/contracts/bundle/ComposedBundle.java`;
- `ActivationConfigSchemaParityTest.inline` in
  `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ActivationConfigSchemaParityTest.java`.

**Steps:**
1. Make each change behavior-preserving: a guard clause, an early `continue` or `return`, or an extracted
   private method. Change no signature that a caller outside the file uses, and no observable output.
2. Before and after, run the tests that cover each method: `:contracts:test` (`ComposedBundleTest` and the
   bundle tests) and `:packs:test --tests '*ActivationConfigSchemaParityTest'`. They must pass unchanged. Do
   not edit a test to make it pass.
3. Remove the three methods' register entries; the register then holds 15 entries, and `Size: 15 entries`.
4. Commit the refactor separately from the gate (its own `refactor(...)` commit), so the reviewer can
   check it on its own.

**Validation:** each file passes Checkstyle with no register entry, and `verifyAll` passes.

## Definition of Done

- The four modules and the XPath filter are in `checkstyle.xml`, and the register uses the XPath DTD.
- One entry per over-limit method, with rule, file, reason and sunset; Size is correct; at most 15
  entries, after T017 removes the three nested-if methods.
- `./gradlew verifyAll` passes.
- The planted-method evidence for all four rules is recorded.
- No new plugin or library, and no other file changed. T017's refactors are behavior-preserving, and
  their covering tests pass unchanged.

## Risks and reviewer guidance

- **The reviewer checks** that `checks` on each entry names exactly the rules that method breaks, and
  that no entry is per-file (one without a `query`).
- **An XPath that matches nothing** leaves the violation in place and the build fails: visible, not
  silent.
- **Time:** the measurement run must not add more than 30 s to `verifyAll` (NFR-001). Checkstyle
  already runs, and the modules are cheap.
