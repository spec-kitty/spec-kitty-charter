# Post-plan adversarial review: adversary lens (debugger-debbie, squad-delegate mode)

Mission: `kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/`. This review is read-only. Decisions under `decisions/` are taken as binding.

## Profile and directives applied

- Profile `debugger-debbie`, run in **squad-delegate mode**. I answer from my own lens and dispatch no swarm. The verbs I use are falsify, diff and trace. I do not patch: each recommendation is a structural fix for an implementer to make.
- Charter context (action `plan`). Directives applied:
  - DIRECTIVE_001: boundaries. The fetcher stays a port with an adapter, and the companion rule has one owner.
  - DIRECTIVE_003: undocumented decisions are flagged.
  - DIRECTIVE_010: plan text vs. what Java 25 and the code can actually do.
  - DIRECTIVE_030 / QA_003: test seams that can really exercise each control.
  - DIRECTIVE_051: no new dependency is proposed. Every fix uses the JDK.
  - RECONCILE_CHANGE_SCOPE_TENSIONS: findings outside this mission are marked pre-existing.
- Evidence comes from code reads, a corpus scan of the pinned open-packs (`01edb4e`, the `openPacksRef` pin) and JDK probes. I ran the probes on the local JDK 21.0.12. The behaviours they rely on are the same in 25.

---

## 1. Guide fetch (NFR-004, D8, IC-05)

Probe results (`java T.java`, scratchpad `squad3/j/T.java`):

```
fd00::1 / fc00::1        -> loop=false site=false any=false ll=false   (ULA: no InetAddress predicate)
::127.0.0.1              -> Inet6Address 7f00:1 loop=false             (IPv4-compatible: not loopback)
::ffff:127.0.0.1         -> Inet4Address 127.0.0.1 loop=true           (IPv4-mapped: JDK folds, OK)
2130706433, 127.1        -> 127.0.0.1 loop=true                         (decimal/short literals resolve)
0177.0.0.1               -> 177.0.0.1                                   (JDK reads decimal, curl reads octal)
0.0.0.0                  -> any=true (loop=false)
64:ff9b::7f00:1          -> all predicates false                        (NAT64-embedded 127.0.0.1)
100.64.0.1               -> all predicates false                        (CGNAT)
https://allowed.com@evil.net/x -> host=evil.net user=allowed.com
https://exämple.com/, https://a_b.com/ -> host=null
https://ALLOWED.com./    -> host=ALLOWED.com.   (case and trailing dot)
HttpClient.newHttpClient().proxy() = Optional.empty, yet
default client fetch     -> IOException: Tunnel failed, got: 403       (default ProxySelector used: JAVA_TOOL_OPTIONS -Dhttps.proxyHost)
NO_PROXY fetch           -> ConnectException                            (direct egress blocked in this container)
header("Host", ...)      -> IllegalArgumentException: restricted header name: "Host"
```

- **[HIGH] plan.md:212 / research.md:339-340: "connect to the checked address" cannot be built as written with `java.net.http.HttpClient`.**
  - HttpClient has no per-client resolver or socket factory.
  - Rewriting the URI to the IP literal does not work either: `Host` is a restricted header (probe above), and TLS SNI and hostname verification would then check the IP instead of the name.
  - So the time-of-check vs. time-of-use gap behind DNS rebinding is open by design.
  - The JVM's positive InetAddress cache (`networkaddress.cache.ttl`, 30 s by default with no SecurityManager) narrows the window, because HttpClient resolves through the same cache. It does not close it: an attacker can repeat until the check and the connect fall on either side of a cache expiry.
  - The test seam in plan.md:213-214 ("local stub server through an injectable resolver") has the same flaw. An injectable resolver only feeds the adapter's pre-check, never HttpClient's connect. A test that passes that way proves nothing about the connect.
  - **Recommendation:** close the gap at resolution time. Use a JEP 418 `InetAddressResolverProvider` (JDK 18+, a `META-INF/services` entry, no dependency) that delegates to the built-in resolver. For hostnames on the fetch allowlist only, it refuses any result that is not global unicast. Every other lookup passes through unchanged.
  - Keep the explicit pre-check as well. It gives the typed refusal.
  - If the operator will not accept a JVM-wide provider, the plan must say plainly that the allowlist is the primary control and rebinding is a residual risk. Do not claim "pinned".
  - Test the address classifier as a pure function. Give the integration test a test-only resolver delegate and a test `SSLContext`; the adapter needs an injectable `SSLContext` anyway for an HTTPS stub. Never add a production property that permits loopback.

- **[HIGH] IC-05 does not mention proxies: by default the client tunnels through the JVM proxy, which makes the address checks irrelevant.**
  - `HttpClient.newBuilder()` without `.proxy(...)` uses `ProxySelector.getDefault()`, which reads `https.proxyHost`.
  - This container sets that property through `JAVA_TOOL_OPTIONS`. The probe tunnelled through `127.0.0.1:40751`.
  - Through a proxy, the proxy resolves and connects. The service-side DNS check validates an address that is never used, and `http.nonProxyHosts` changes what is reachable.
  - `HTTPS_PROXY` (the environment variable) is **not** read by the JDK; only the system properties are.
  - **Recommendation:** build with `.proxy(HttpClient.Builder.NO_PROXY)`, and also no `authenticator` and no `cookieHandler`, and test it.
  - Record the consequence (DIRECTIVE_003): deployments whose egress needs a proxy cannot use `guideUri`. In this container the probe got `ConnectException` with NO_PROXY. An operator who later wants proxy egress has a new decision to make (allowlist enforced by the proxy), so it should not be absorbed silently.

- **[HIGH] plan.md:49 / research.md:340: "10 s total" is not what `HttpRequest.timeout` gives.**
  - `HttpRequest.Builder.timeout` only bounds the wait for the response **headers**.
  - The body (slowloris drip), the redirect hops and the DNS lookup (blocking `InetAddress.getAllByName`) are all outside it.
  - `connectTimeout` only bounds the TCP and TLS connect.
  - **Recommendation:**
    - Set one deadline when fetching starts. Each hop gets `remaining = deadline - now` as its request timeout.
    - Run the DNS pre-check on a virtual thread joined with the remaining time.
    - Read the body with `sendAsync` and a counting `BodySubscriber`, plus `CompletableFuture.orTimeout(remaining)`. On timeout, cancel the future (JDK 16+ `cancel(true)` aborts the exchange).
    - Never read through a blocking `BodyHandlers.ofInputStream()` without a watchdog.
    - Test: a stub that sends headers at once and then 1 byte per second must be refused at about 10 s.

- **[MEDIUM] research.md:339: redirect handling is not stated precisely enough to be safe.**
  - `HttpClient.Redirect.NORMAL` (and `ALWAYS`) follow redirects inside the client, so no per-hop check runs. `NORMAL` refuses HTTPS to HTTP, but `ALWAYS` does not.
  - D8 says addresses are refused "on every redirect", but it does not say the **allowlist** is re-checked per hop. Only the contract description implies it (delta.yaml:16-17).
  - **Recommendation:** use `Redirect.NEVER` and follow by hand, at most 3 times. Resolve `Location` with `URI.resolve`. On every hop, re-run the whole check: scheme `https`, no userinfo, host allowlisted, all resolved addresses global.
  - Refuse a 3xx without `Location` and anything that is not 2xx or 3xx.
  - Write the per-hop allowlist into D8. The common case `github.com` to `raw.githubusercontent.com` then means both hosts must be allowlisted.

- **[MEDIUM] plan.md:211-212: the address classes listed are incomplete, and the JDK predicates mislead.**
  - Unique-local `fc00::/7` matches **no** `InetAddress` predicate; `isSiteLocalAddress` covers only the deprecated `fec0::/10`. A plain implementation of "refuse site-local" lets ULA through.
  - Classes the plan does not list:
    - `0.0.0.0/8` and `::` (unspecified; on Linux, connecting to 0.0.0.0 reaches the local host)
    - `100.64.0.0/10` CGNAT
    - multicast
    - `240.0.0.0/4` and broadcast
    - `192.0.0.0/24`, `198.18.0.0/15`
    - IPv4-compatible `::a.b.c.d` (probe: `::127.0.0.1` is not loopback)
    - addresses with an embedded IPv4: NAT64 `64:ff9b::/96`, 6to4 `2002::/16`, Teredo `2001::/32`
  - **Recommendation:** use an allow-only rule. Accept only global unicast. Decode any embedded IPv4 and classify it. Check **every** address `getAllByName` returns, and refuse the URI if any of them is not global, because HttpClient may connect to a different one than the check used.

- **[MEDIUM] research.md:337: how the host is matched against the allowlist is not specified.**
  - Probes:
    - `https://allowed.com@evil.net` has host `evil.net`, so prefix or string matching is wrong.
    - IDN and underscore hosts give `getHost() == null`.
    - `ALLOWED.com.` keeps its case and the trailing dot.
  - **Recommendation:**
    - Match the host exactly, after lowercasing and stripping one trailing dot. The allowlist entries are normalized the same way, with `IDN.toASCII` applied when the configuration is loaded.
    - Refuse a null host, userinfo and IP-literal hosts. Literal tricks (`2130706433`, `127.1`, the JDK reading `0177.0.0.1` as decimal where curl reads it as octal) then cannot reach the address check, and resolver-parser differentials go away.
    - Never use suffix matching. If subdomains are wanted, use an explicit `*.example.com` entry and check on a dot boundary.
    - Decide on non-443 ports. I recommend refusing them unless the allowlist entry names the port.

- **[LOW] Gzip bomb: mostly does not apply to this design.** The JDK HttpClient neither sends `Accept-Encoding` nor decompresses, so the 1 MiB cap counts wire bytes. An unsolicited `Content-Encoding: gzip` would leave compressed bytes that fail UTF-8 decoding. **Recommendation:** refuse any `Content-Encoding` other than `identity` with a typed refusal. Never add a decompressing subscriber. Refuse early when `Content-Length` exceeds the cap, but count the body regardless of the header.

- **[LOW] Content type and charset.**
  - Decode strictly with `CodingErrorAction.REPORT`, the same as `FilesystemPackStore.readText` (FilesystemPackStore.java:785-797).
  - Refuse a declared charset other than UTF-8.
  - Accept `text/markdown`, `text/plain` and `text/x-markdown`, and refuse `text/html`. An HTML page, such as GitHub's blob view instead of the raw file, would otherwise get as far as the guide parser and fail with a confusing "missing Overview".
  - A CRLF guide served from Windows is refused by the Markdown form (D6), which is correct with no tolerance logic. The refusal message should say "CR line endings", not "invalid guide".

- **[LOW] Lifecycle.** Use one shared `HttpClient` bean, closed on shutdown (`AutoCloseable` since 21). A client per request leaks selector threads.

---

## 2. Markdown form (D6, IC-01)

Corpus scan (`squad3/py/scan.py`, `dash.py`) over 155 `.md` files at the pin:

| Edge case | 24 toolguide guides | 47 mission-step files | others |
|---|---|---|---|
| CR / BOM / non-UTF-8 | 0 | 0 | 0 |
| `~~~` fences | 0 | 0 | 0 |
| opening fence longer than 3 (closing fence length differs) | 1 (MERMAID_DIAGRAMMING.md:212-217, ```` around ```mermaid … ```) | 0 | 0 |
| fence indented 1-3 / 4+ | 6 / 0 | 27 / 4 (specify/prompt.md:539,631,712,773, inside lists) | 5 / 0 |
| fence in blockquote | 0 | 1 (analyze/prompt.md:214 `> ```bash`) | 0 |
| heading-looking lines inside fences | 62 lines | 44 | 16 |
| closing `#`s (`## T ##`), empty heading, `#x` with no space, tab in heading | 0 | 0 | 0 |
| H1/H2 heading with trailing whitespace | 0 | 0 | 0 |
| indented heading (1-3 spaces) | 0 | 1 (tasks/prompt.md:127 `   ### Task Tracking Format`, in a list) | 2 (qa promise-page-template.md:17-18) |
| thematic break `---` (not frontmatter) | 26 lines in 5 guides (e.g. PYTHON_MUTATION_TOOLS.md:83,159,171), all after a blank line | many | many |
| setext-ambiguous (`---`/`===` right after a paragraph line) | 0 | 0 | 0 |
| HTML comment with a column-0 H1/H2 inside it | 0 | 0 | 0 |
| frontmatter | 0 | 28 | 14 |
| empty (0-byte) file | 0 | 2 (documentation/ and research/retrospect/prompt.md) | 0 |
| non-ASCII headings | 12 | 44 | 33 |
| reserved heading in a different case | 0 | n/a | n/a |

- **[MEDIUM] plan.md:38-39 / research.md:310: fence closing must follow CommonMark (same character, length at least the opener's, no info string).**
  - The corpus has one nested fence (MERMAID_DIAGRAMMING.md:212-217). A scanner that simply toggles on every ``` line accepts it by luck: the inner ```mermaid … ``` pair cancels out.
  - Such a scanner exposes a column-0 `## X` placed inside an inner ``` block of a ```` fence as an H2. Different files then produce different models.
  - Other cases: a `~~~` fence containing ``` lines; a closing line with an info string (not a closer); a ```` ```foo``` ```` line (backtick info strings cannot contain backticks, so it is not a fence).
  - **Recommendation:** make these exact unit fixtures in PR-A1, with a column-0 H2 inside each.

- **[MEDIUM] research.md:313: the "indented heading (1-3 spaces)" refusal is too broad.**
  - tasks/prompt.md:127 is an indented **H3** inside a list item, which is legitimate CommonMark.
  - D6 says the model will serve "later mission step prompts" and that all 72 files are already canonical. With the rule as written, that claim is false.
  - **Recommendation:** refuse indented headings only at levels 1-2 (`^ {1,3}#{1,2}(\s|$)`). H3 and deeper are content anyway. Correct the claim in D6.

- **[MEDIUM] data-model.md:414-416: nothing constrains the model's constructors, so `parse(write(d)) != d` is only found at write time.**
  - The migration harness and `Guide` both build models programmatically. Inputs that break the round trip:
    - A title with leading or trailing whitespace, a newline, a trailing `#` run, or an empty title.
    - Content with leading or trailing blank lines. "Blank lines between blocks are ignored", so they disappear on reparse.
    - Content holding a column-0 H1/H2 outside a fence, or an unbalanced fence.
    - A freeform section titled with a reserved heading. An exact `Overview` turns into a duplicate fixed block. A case variant such as `Known Limitations` silently becomes a freeform section.
  - The check on write catches these, but late and with an opaque message.
  - **Recommendation:**
    - Canonicalize content in the constructors (strip leading and trailing blank lines) and refuse the cases above. Run the scanner over section content and refuse any content in which it finds structure.
    - Decide on `## Title ##`. I recommend refusing it (corpus count 0). If the closing hashes are stripped instead, two different files compare equal, and the plan should say that this is intended.
    - Refuse reserved headings case-insensitively, and refuse NBSP lookalikes too, as ambiguous.

- **[LOW] data-model.md:409: the position of a freeform section after `cautions`, `known_limitations` or `references` cannot be represented.** The writer would silently move it in front of `cautions`. **Recommendation:** refuse it explicitly. Today the text only refuses fixed blocks that are out of order.

- **[LOW] research.md:313: the setext refusal must not catch thematic breaks.** 26 `---` lines in 5 guides each follow a blank line, so they are thematic breaks. A rule that refuses every `---` line would refuse 5 guides.
  - **Recommendation:** treat a line as setext only when it immediately follows a non-blank paragraph line (not a list item, heading or fence).
  - A guide companion has no frontmatter in its model, so a guide whose first line is `---` should be refused explicitly ("frontmatter not allowed in a guide"). Do not let a leading thematic break be read as frontmatter.

- **[LOW] Divergences from CommonMark that are consistent but should be written down.**
  - A column-0 `## ` inside an HTML block or comment counts as a heading for the scanner but not when rendered (corpus 0).
  - A ``` line inside a top-level indented code block (4 or more spaces, not in a list) opens a fence for the scanner (corpus: the only 4+-indented fences are in lists).
  - The fence in a blockquote (analyze/prompt.md:214) must be treated the same way on its opening and closing lines.
  - Recommendation: record these in the ADR-0040 amendment.

- **[LOW] Empty documents.** The two 0-byte prompts must parse to the empty model and must not be refused. The writer emits `""`.

- Concede:
  - Non-ASCII headings compare exactly, which is right. A lookalike in a reserved heading fails safe, since `overview` is then missing.
  - No CR, BOM, tabs or trailing whitespace exist in guides.
  - Fenced headings at any indentation are handled by the plan's own rule.
  - Frontmatter delimiters inside fences do not matter, because frontmatter is recognised only at line 1.

---

## 3. Toolguide and guide coupling

- **[HIGH] data-model.md:420-421 together with Companions.java:121-126: the `<id>.md` path that registration derives can claim a pack-level Markdown file.**
  - A new toolguide is created at the **pack root** (FilesystemPackStore.java:746-758, `newFile`), so its guide lands at `<pack>/<id>.md`.
  - The id `pack` (valid kebab) therefore gives `pack.md`, the file sibling #195 models with a `markdown` `files` rule.
  - `Companions.resolve` refuses only a Mapped classification whose `schema()` **is empty**, meaning payload rules. A `files` rule with a structure schema is Mapped with a schema, so it is **not refused**.
  - The same hole allows `guide_path: ../pack.md` in a toolguide inside `toolguides/` that arrives through a whole-pack write.
  - Excluded files (`*/assets/**/README.md`) are not refused either. That part is pre-existing.
  - **Recommendation:**
    - Refuse a companion path that **any** `files` or `exclusions` rule classifies, not only schema-less payloads. This keeps one file with one owner (ADR-0039 decision 3).
    - Derive the HTTP path into a namespace that cannot collide, for example `<id>.guide.md` or `<id>.toolguide.md`.
    - Coordinate with #195 before PR-A2.

- **[MEDIUM] IC-06 does not say whether guides are renamed to `<id>.md`.**
  - For 4 toolguides the existing guide name differs from `<id>.md` **only in case**: contextive (CONTEXTIVE.md), gherkin, sonar, qa-carrier-lint.
  - If the migration keeps the names and a PUT re-derives `<id>.md` (data-model.md:421), `planCompanions` refuses with "differs only in case" (FilesystemPackStore.java:607).
  - The other 18 differ by `_` vs `-` as well. They get a silent rename: the old file is removed and a new one written.
  - **Recommendation:** the migration writes every guide at the path the server derives, or the server keeps the existing `guide_path` when replacing. State which in IC-06 and data-model, and add a PUT scenario over a migrated toolguide.

- **[MEDIUM] RefusalHandler.java:111-125 and FilesystemPackStore.java:601-610, 756: conflicts a client can cause come back as 500 `pack_unreadable`.**
  - These are "is taken", "named by another artifact", "differs only in case" and "file name is taken". Now that registration writes a guide file, an ordinary client can hit them, for example with a stray `<id>.md` or a case collision.
  - **Recommendation:** in the registration path, map them to `registration_conflict` (409) or `validation_failed` (422), and cover them with a contract scenario. Otherwise every such client error is logged as a server fault.

- Concede, after verifying the code:
  - **ETag and fingerprint.** `PackFileSet.digested` adds each readable companion to the digest (PackFileSet.java:215-221), so a change to the guide alone moves the fingerprint. One caveat: a guide that stops decoding as UTF-8 drops out of the digest, and the load then reports it. That is pre-existing.
  - **Atomicity.** The registration commit is a stage-and-swap (`CommitStorePackWriteStore.commit` → `PackCommitStore`, CommitStorePackWriteStore.java:124-127). A YAML file without its guide cannot become visible. The direct `FilesystemPackStore.write` is not atomic, and its documentation (FilesystemPackStore.java:533-535) says it is not a production writer for kinds with companions.
  - **Orphans.** There is no DELETE (ADR-0026). A replace that changes the path removes the dropped companion (`removeDropped`, :667-681). The whole-pack write copies only modelled files.
  - **Collisions.** Claims are case-folded (`Companions.Inside.claim`) across all kinds with companions (`claimsOfOthers`, :625-640). Two toolguides that name the same guide are refused on read and on write.

---

## 4. Registration (contract delta, controller, service)

- **[MEDIUM] contracts/toolguide-registration.delta.yaml:6-8: `oneOf` would be the contract's first composition keyword (grep over contracts/charter: 0 `oneOf`/`anyOf`), and nothing enforces it at runtime.**
  - The generator runs with the custom `SingleModelPerSchemaNormalizer` and `useBeanValidation=true, performBeanValidation=false` (build-logic/.../charter.contract-api.gradle.kts:39-55).
  - The generated DTO carries only bean annotations, so `oneOf` is not checked at runtime.
  - How openapi-generator's `spring` generator emits an object with properties plus a `oneOf` of subschemas that only list `required` is unverified. It may produce composed or interface models.
  - **Recommendation:**
    - Spike `generateContract` first. If the output changes shape, express the rule as `not: {required: [guide, guideUri]}` together with the description and a scenario.
    - Either way, enforce exactly one in `ToolguideController`: exactly one of `guide` / `guideUri` is non-null.
    - Define the edge cases:
      - Explicit JSON `null` counts as absent. Note that JSON Schema `required` treats it as present, so the scenario and the server must agree.
      - `guide: ""` is a 422 from the guide parse (no Overview).
      - A blank `guideUri` gets 422 before any fetch.
      - Both present gets 422.
    - Note that the `@Pattern('^https://')` failure surfaces through bean validation (`MethodArgumentNotValidException`), not as a `CharterRefusal`. Check that its status matches the other refusals.

- **[MEDIUM] There is no size limit on an inline guide.** Tomcat's `maxPostSize` applies only to form posts, not JSON. The only ceiling is Jackson's default `StreamReadConstraints` maximum string length, which is tens of MB. A fetched guide is capped at 1 MiB, but an inline one can be about an order of magnitude larger. The service listens on loopback only (application.yaml `server.address: 127.0.0.1`), which lowers the severity. **Recommendation:** apply the same 1 MiB limit to `guide`, counted in UTF-8 bytes in code, and add a `maxLength` hint in the contract. Use the same refusal code as the fetch cap.

- **[MEDIUM] ToolguideRegistrationService.java:294: the fetch must not run under `writeStore.exclusively(pack, …)`.** That is a per-pack in-JVM lock. A 10 s fetch held inside it blocks every writer of the pack, so one slow allowlisted host stalls all registrations. **Recommendation:** fetch and parse in the controller or service before taking the lock; the precondition is still checked inside the lock. Add a test that a stalled fetch does not block a concurrent registration of another artifact.

- **[MEDIUM] data-model.md:398: "an H1 that differs from the title is refused" will reject most fetched guides.**
  - Remote content is not under the client's control. Upstream H1s rarely match the registered title exactly, for example `# Mermaid` vs. `Mermaid Diagramming`.
  - **Recommendation:** decide and record (DIRECTIVE_003) the input rule for HTTP. I recommend that the H1 is optional on input; if present, it must equal `title`, and either way it is written from `title`. This is an input mapping rule, not tolerance in the reader. Add scenarios for an inline guide without an H1, a fetched guide whose H1 differs, and a PUT that changes `title` while keeping an old H1.

- **[LOW] Idempotency.**
  - A PUT with an identical inline `guide` writes the same canonical bytes, so the digest and ETag should not change. Make that a test.
  - A PUT with `guideUri` is idempotent in intent only, because remote content can change between calls. Document this in the contract description.
  - A guide is stored canonically (`write(parse(x))`), so the stored bytes may differ from the bytes sent. Say so in the contract.

- **[LOW] plan.md:179: the plan says to "remove this mission's own guide exclusion" from `file-kinds.yaml`, but no toolguide or `.md` exclusion exists there at this checkout.** grep finds only `*/assets/**/README.md`. Reconcile with `expected-red.yaml` (`other:toolguides`) so the task does not chase a rule that does not exist.

---

## Verdict

**Conditional pass. Fix before tasks.**

The plan's structure holds up against the attacks in this lens:

- the port and adapter split;
- stage-and-swap atomicity;
- a fingerprint that covers companions;
- case-folded single ownership;
- a purpose-built scanner with exact refusals.

These items must be in the plan text before `/spec-kitty.tasks` decomposes IC-05 and IC-03:

1. **The three HIGH fetch gaps.** Address pinning cannot be done with HttpClient: use a JEP 418 resolver scoped to allowlisted hosts, or record the residual risk. Use `NO_PROXY`. Enforce a real total deadline.
2. **The HIGH companion hole.** `Companions.resolve` must refuse every classified file. Put the derived `<id>.md` in a namespace that cannot collide.
3. **The MEDIUM items in D6 and D8.** These are the fence closing rule, indented-heading refusal limited to H1/H2, constructor invariants, an allow-only address classifier, exact allowlist matching re-checked per redirect, the `oneOf` spike and enforcement in code, an inline size cap, fetching outside the lock, the H1 input rule, and the migration rename choice.

None of these reopens an operator decision. All of them can be fixed with the JDK alone (DIRECTIVE_051 holds).
