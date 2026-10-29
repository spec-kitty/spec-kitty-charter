# Data model: Container profile properties record

## ContainerProperties (`charter.container.*`)

| Component | Key | Type | Rule | Under `container` | Without the profile |
|-----------|-----|------|------|-------------------|---------------------|
| `allowedHosts` | `charter.container.allowed-hosts` | ordered list of host names | required, at least one entry, no blank entry | bound and validated; a breach fails the start naming the key | not bound, not validated |

- No component holds a secret (ADR-0041 decision 8).
- The guard does not consume the list yet; that is the container guard's change (ADR-0041 decision 6, a follow-up).
