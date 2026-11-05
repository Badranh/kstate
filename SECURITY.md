# Security policy

Kstate opens a read/write channel into a running debug app and can publish it through a tunnel,
so we take reports seriously.

## Reporting a vulnerability

Please report privately through GitHub's **Report a vulnerability** button on the repository's
Security tab (private vulnerability reporting). Do not open a public issue.

Include the affected version or commit, the steps to reproduce, and the impact you observed.
We aim to acknowledge within 3 working days.

## Scope

The threat model is in [docs/SPEC.md](docs/SPEC.md) under "Security model". The SDK is debug-only
by design; a report that needs the SDK shipped in a release build should explain how the
release-build guard was bypassed.
