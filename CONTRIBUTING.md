# Contributing to Kstate

Thanks for helping. Read [CLAUDE.md](CLAUDE.md) for the hard rules and [docs/SPEC.md](docs/SPEC.md)
for the design; both apply to human contributors too.

## How we work

- **Test first.** Every change starts with a failing test, then the simplest code that passes,
  then a refactor. Bug fixes start with a test that reproduces the bug.
- **Clean code and SOLID.** Small classes with one job, narrow interfaces, dependencies injected.
- **One concern per commit**, written as a [Conventional Commit](https://www.conventionalcommits.org)
  (`feat(sdk): ...`, `fix(host): ...`, `test(protocol): ...`).
- **Public API changes are deliberate.** Run `./gradlew apiDump`, commit the `.api` files, and add
  an entry to [docs/DECISIONS.md](docs/DECISIONS.md).

## Before you open a pull request

```bash
./gradlew check apiCheck
```

It must pass. It runs the unit tests (JVM, Android host, iOS simulator, wasm/Node), detekt and ktlint.
Run `./gradlew ktlintFormat` to fix formatting.
