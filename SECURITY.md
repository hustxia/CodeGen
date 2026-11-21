# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| `main` (latest) | Yes |
| Released V9.x | Yes |
| V8.x and older | No |

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Email: `hustxiayuhong@126.com`

Subject format: `[CodeGen Security] <short summary>`

Include:

- affected component (`codegen-server`, `codegen-web`, generated artifact, template, docs site)
- reproduction steps or a minimal payload
- impact (what an attacker could do)
- your suggested fix, if any
- whether you want credit in the release notes

Please remove real passwords, tokens, database credentials and personal data from samples.

## Response target

- Acknowledge within 72 hours.
- Confirm or reject the report within 7 days.
- Ship a patch or mitigation within 14 days for confirmed issues on supported versions.
- Credit reporters in the release notes unless they prefer to stay anonymous.

## Scope

In scope:

- path traversal or arbitrary file write in generation / backup / rollback
- SQL injection in platform APIs or generated artifacts
- template injection that executes server-side code
- authentication / session handling in the platform or generated login
- secret leakage in logs, ledgers or generated config

Out of scope:

- issues requiring local administrator access to the developer machine
- social engineering
- vulnerabilities in third-party dependencies that are already tracked by upstream advisories (please still report them if CodeGen ships an unsafe default)

## Safe defaults in this project

- `src/main/resources/application-local.yml` is gitignored; do not commit real credentials.
- Generated artifacts are written to the configured `gen.output.*` paths; keep them outside web roots.
- The self-check classpath should point to dependency directories, not to production secrets.
