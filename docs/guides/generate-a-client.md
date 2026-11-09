---
title: Generate your own client
description: Fetch the published charter contract, verify its SHA-256, and generate a Java or Python client with the pinned generator and the options that tolerate additive 0.x changes.
doc_status: draft
updated: 2026-11-05T00:00:00Z
type: Guide
kind: How-To
related:
  - api/service-contract
  - api/compatibility
  - guides/service-build
  - adr/0004-public-api-returns-generated-dtos
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - adr/0022-contract-versions-start-at-0-x
audience:
  - profile: contract-consumer
    guidance_text: Follow this to generate a client for the charter service in your language, and for what it must tolerate.
---

# Generate your own client

This page shows how to generate a client for the charter service from its published contract, in Java or in Python,
with the generator version and options this repository uses. The steps for other languages are the same except the
generator name; the [caveats](#caveats-for-any-language) hold for every language.

**The contract is not released yet.** Until contract 0.1.0 is tagged, the published contract is the snapshot of `main`
(`info.version` ends in `-SNAPSHOT`, for example `0.2.0-SNAPSHOT`), and it can change without notice
([#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45)). Generate against it to try the service; pin a
release once there is one ([compatibility](/api/compatibility/)).

Java consumers can also use the client this repository generates in its build (`client-java/`,
[Building the service](/guides/service-build/#the-java-client)). It is not published yet; a release zip follows contract
0.1.0 ([#220](https://github.com/spec-kitty/spec-kitty-charter/issues/220)). No Python client is published; generate
your own (Step 4).

## Before you start

- `curl`, `unzip`, and GNU `sha256sum` and `sha1sum`. On macOS, install them with `brew install coreutils`, or use
  `shasum -a 256 -c` and `shasum -a 1 -c` in their place.
- Java 21 or 25, to run the generator and to build the Java client.
- For the Java section: Python 3 with PyYAML, to prepare the contract, and Maven or Gradle to build.
- For the Python section: Python 3.9 or later.

## Step 1: Get the contract and verify it

Pick one source. Each gives you the single-file contract and a SHA-256 to check it against. The commands below call
the file `openapi.yaml`.

**From the docsite** (the snapshot of `main`, rendered at [the API reference](/reference/api/main/)):

```bash
curl -fsSLO https://spec-kitty.github.io/spec-kitty-charter/reference/api/main/openapi.yaml
curl -fsSLO https://spec-kitty.github.io/spec-kitty-charter/reference/api/main/openapi.yaml.sha256
sha256sum -c openapi.yaml.sha256
```

**From a running service** (optional; the docsite needs no token). `GET /api/v1/openapi.yaml` answers the contract the
service was built with. Like every operation it needs the service's worktree token, which whoever started the service
configured ([security](/api/service-contract/#security-and-freshness),
[run in a container](/guides/run-in-a-container/)). Its `ETag` is `"sha256:<digest>"`, the SHA-256 of the body:

```bash
curl -fsS -D headers.txt -H "Authorization: Bearer $CHARTER_TOKEN" -o openapi.yaml "$CHARTER_URL/api/v1/openapi.yaml"
digest=$(grep -i '^etag:' headers.txt | tr -d '\r' | sed 's/.*"sha256:\([0-9a-f]*\)".*/\1/')
echo "$digest  openapi.yaml" | sha256sum -c -
```

`$CHARTER_URL` is the service's address, for example `http://127.0.0.1:8080`, and `$CHARTER_TOKEN` its token.

**From a release** (from contract 0.1.0 on). Each release `contract-charter-v<version>` carries the contract as
`charter-openapi.yaml` with `charter-openapi.yaml.sha256`, at
`https://github.com/spec-kitty/spec-kitty-charter/releases/download/contract-charter-v<version>/`. Download both, check
with `sha256sum -c charter-openapi.yaml.sha256`, and use `charter-openapi.yaml` in place of `openapi.yaml` below.

`sha256sum -c` prints `openapi.yaml: OK`. On `FAILED`, download again; never generate from a file that does not match.
A checksum served beside its file proves the download is whole, not where it came from; a pinned release is the
reference to trust once there is one.

## Step 2: Get the generator at the pinned version

This repository generates with openapi-generator 7.26.0 (pinned in `gradle/libs.versions.toml`). Use the same version,
so your client reads the contract the way the service build does:

```bash
curl -fsSLO https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/7.26.0/openapi-generator-cli-7.26.0.jar
echo "$(curl -fsSL https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/7.26.0/openapi-generator-cli-7.26.0.jar.sha1)  openapi-generator-cli-7.26.0.jar" | sha1sum -c -
```

Maven Central publishes a SHA-1 for this jar, not a SHA-256.

## Step 3: Generate a Java client

The contract closes every response object (`additionalProperties: false`). The Java generator turns that into a check
that throws on any member the client does not know, so a member a later 0.x minor adds would break your client. Open
the closed objects in a copy of the contract first. Save this as `open_closed_objects.py`:

```python
"""Writes a copy of the charter contract in which no object is closed, for client generation only."""
import sys

import yaml


def open_objects(node):
    if isinstance(node, dict):
        if node.get("additionalProperties") is False:
            node["additionalProperties"] = True
        for value in node.values():
            open_objects(value)
    elif isinstance(node, list):
        for value in node:
            open_objects(value)


source, target = sys.argv[1], sys.argv[2]
with open(source, encoding="utf-8") as f:
    contract = yaml.safe_load(f)
open_objects(contract)
with open(target, "w", encoding="utf-8") as f:
    yaml.safe_dump(contract, f, sort_keys=False, allow_unicode=True)
```

Then generate and build:

```bash
python3 open_closed_objects.py openapi.yaml openapi.client.yaml
java -jar openapi-generator-cli-7.26.0.jar generate -g java --library okhttp-gson \
  -i openapi.client.yaml -o charter-client-java \
  --invoker-package com.example.charter --api-package com.example.charter.api \
  --model-package com.example.charter.model \
  --additional-properties=enumUnknownDefaultCase=true,disallowAdditionalPropertiesIfNotPresent=false,openApiNullable=false,dateLibrary=java8,hideGenerationTimestamp=true,useJakartaEe=true
cd charter-client-java && mvn -q -DskipTests -Dmaven.compiler.release=21 package
```

The generated project also has a `build.gradle` if you prefer Gradle. Call the service with the generated API classes:

```java
ApiClient client = Configuration.getDefaultApiClient().setBasePath(charterUrl + "/api/v1");
client.setBearerToken(token);
ServiceInfo info = new ServiceApi(client).getServiceInfo();
```

An error answer is an `ApiException` whose body is a Problem (see [the caveats](#caveats-for-any-language)); read it
with `Problem.fromJson(e.getResponseBody())`.

## Step 4: Generate a Python client

Python's generator needs no copy of the contract: `disallowAdditionalPropertiesIfNotPresent=false` turns off its
unknown-member check. It does need one template change, because pydantic refuses an enum value it does not know even
with `enumUnknownDefaultCase=true`. Take the generator's own enum template and add a `_missing_` method that answers
the unknown case:

```bash
mkdir -p templates
unzip -p openapi-generator-cli-7.26.0.jar python/model_enum.mustache > templates/model_enum.mustache
python3 - <<'EOF'
path = "templates/model_enum.mustache"
text = open(path, encoding="utf-8").read()
anchor = "        return cls(json.loads(json_str))\n"
missing = (
    "\n    @classmethod\n"
    "    def _missing_(cls, value):\n"
    '        """A value this client does not know, from a later 0.x minor of the contract."""\n'
    "        return cls.UNKNOWN_DEFAULT_OPEN_API\n"
)
assert text.count(anchor) == 1, "the enum template changed; check it before patching"
open(path, "w", encoding="utf-8").write(text.replace(anchor, anchor + missing))
EOF
```

Then generate and install:

```bash
java -jar openapi-generator-cli-7.26.0.jar generate -g python -t templates \
  -i openapi.yaml -o charter-client-python --package-name charter_client \
  --additional-properties=enumUnknownDefaultCase=true,disallowAdditionalPropertiesIfNotPresent=false,hideGenerationTimestamp=true
pip install ./charter-client-python
```

```python
import charter_client
from charter_client.api.service_api import ServiceApi

config = charter_client.Configuration(host=charter_url + "/api/v1", access_token=token)
with charter_client.ApiClient(config) as client:
    info = ServiceApi(client).get_service_info()
```

An error is a `charter_client.ApiException`; `Problem.from_json(e.body)` reads its Problem. Unknown members are
dropped, not kept.

## Caveats for any language

- **Tolerate enum values you do not know.** A 0.x minor may add a value to any enum
  ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)). Map an unknown value to an "unknown" case, as above; do
  not fail the decode. Refusal codes are an open string on `Problem.code` and need nothing.
- **Do not enforce closed objects on the client.** `additionalProperties: false` describes what the service sends
  today, not a limit on what a later minor may add. A client that rejects an unknown member breaks on an additive
  change.
- **Nullability is a type array.** The contract is OpenAPI 3.1, and a nullable member is written
  `type: ['string', 'null']`. Use a generator that reads 3.1; openapi-generator logs that its 3.1 support is in beta,
  and the output above was checked against this contract.
- **Errors are `application/problem+json`.** Every error answer is a Problem (RFC 9457) with a `title` and a `status`;
  a refusal adds a `code`. A body that is not a Problem (a proxy's page, a cut-off answer) can still reach you, so
  parse defensively.
- **Literal write paths sit beside templated reads.** `PUT /packs/{packId}/artifacts/directive/{artifactId}` sits
  beside `GET /packs/{packId}/artifacts/{kind}/{artifactId}`. A generated client calls each operation by its own path
  and is not affected; a validator or mock that matches the path before the method may refuse such a `GET`
  ([the service contract](/api/service-contract/)).
- **Every call needs the worktree token**, as a bearer token. A browser-like request (an `Origin` or `Sec-Fetch-*`
  header, or a `Host` whose name is not loopback) is refused with a `403`.
- **Regenerate when you raise your pin.** Within 0.x a minor may break; read the contract's `### Breaking` notes
  before you regenerate ([compatibility](/api/compatibility/)).

## The options, and why

| Option | Java | Python | Why |
|---|---|---|---|
| `enumUnknownDefaultCase=true` | yes | yes, with the template change | An enum value a later minor adds maps to `UNKNOWN_DEFAULT_OPEN_API` |
| Closed objects opened | the script | not needed | A member a later minor adds does not fail the decode |
| `disallowAdditionalPropertiesIfNotPresent=false` | yes | yes | An object that declares nothing is open too |
| `openApiNullable=false` | yes | n/a | Nullability comes from the 3.1 type arrays; no extra wrapper library |
| `dateLibrary=java8` | yes | n/a | `java.time` types; the contract has no date-time today |
| `hideGenerationTimestamp=true` | yes | yes | Two generations from one contract are identical |

This repository's own Java client applies the same options in its build, and opens the closed objects with a
normalizer instead of the script.

## See also

- [The service contract](/api/service-contract/) — every operation, its answers and its refusals
- [Compatibility](/api/compatibility/) — how the contract is versioned and what a consumer pins
- [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) — why a consumer generates its client
  from the released contract
