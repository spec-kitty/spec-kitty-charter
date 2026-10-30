# Data model: Container profile file and secret import

No new domain entity or record component. The mission adds configuration sources and one key read by Spring's config
import.

## Configuration keys under the container profile

| Key | Source | Secret | Default (profile file) | Bound by |
|-----|--------|--------|------------------------|----------|
| `charter.container.secrets-dir` | env `CHARTER_CONTAINER_SECRETS_DIR`, command line | no | `/run/secrets` | the config import placeholder (not a record component) |
| `charter.worktree-token` | file `<secrets-dir>/charter.worktree-token`, env `CHARTER_WORKTREE_TOKEN` | **yes** | none | `GuardWiring` → `WorktreeToken` |
| `charter.container.allowed-hosts` | file `<secrets-dir>/charter.container.allowed-hosts`, env `CHARTER_CONTAINER_ALLOWEDHOSTS` | no | none (required) | `ContainerProperties` |
| `charter.config-root` | env `CHARTER_CONFIGROOT`, `--configRootLocation` | no | none in the profile (#234) | `StorageProperties`, `ContainerStartupCheck` |
| `server.address` | profile file, env `SERVER_ADDRESS` | no | `0.0.0.0` | Spring Boot |
| `server.port` | profile file, env `SERVER_PORT` | no | `8080` | Spring Boot; `ContainerStartupCheck` refuses 0 |

## Rules

- **Required import:** the secrets directory must exist; it may be empty.
- **File names:** a secret file is named in dot form, after its property. A file named in env-var style binds nothing.
- **Trailing newline:** a file's trailing newline is dropped.
- **Precedence:** env vars rank above imported files, so for a key given both ways the env var wins.
- **No secret committed:** no committed profile file, compose file or env template holds a value for
  `charter.worktree-token`. Every value in `.env.example` is empty.
