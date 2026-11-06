# Data model: Container guard, actuator and single-replica ruling

No persisted data changes. Two in-memory value types shape behaviour.

## GuardPolicy (packs, `infra/api`)

| Field | Meaning | Rule |
|---|---|---|
| `hosts` | The host names a request may give | Non-empty. Stored lower case (`Locale.ROOT`). Compared to the server name and every `Host` value with the port removed. |
| `hostHeaderRequired` | Whether a request without `Host` is refused | `false` for `loopback()`, which keeps today's behaviour. `true` for `named(...)`. |
| `tokenExemptGets` | Raw request URIs a `GET` may reach without the token | Empty for `loopback()`. Compared with `getRequestURI()` exactly. Each path starts with `/`. |

Invariants:

- The policy never names a profile. The deployable chooses the factory.
- An exempt path skips only the token check, never the host or browser check.

## ContainerProperties.allowedHosts (deployable, `charter.container.allowed-hosts`)

| Form | Accepted example | Refused examples |
|---|---|---|
| DNS name (LDH labels, no trailing dot) | `charter.internal`, `localhost` | `*.example.com`, `charter.internal.`, `bücher.de`, `host:8080`, `http://host`, `a@b` |
| IPv4 literal | `10.0.0.5`, `127.0.0.1` | `0.0.0.0` |
| Bracketed IPv6 literal | `[::1]`, `[2001:db8::1]` | `[::]`, `::1` (unbracketed), `[fe80::1%eth0]` |

A refused entry fails the start. The message names `charter.container.allowed-hosts`.
