# Contributing to Pushdozer

Thank you for your interest in contributing!

## Getting Started

### Requirements

- JDK 21
- Git

Gradle will download Minecraft, Fabric, and other dependencies automatically.

### Setup

```bash
git clone https://github.com/Theopote/pushdozer.git
cd pushdozer
./gradlew build
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

### Run in Development

```bash
./gradlew runClient    # client
./gradlew runServer    # dedicated server
```

## Pull Requests

1. Fork the repository and create a feature branch from `main`
2. Make focused changes — one feature or fix per PR
3. Ensure `./gradlew build` passes (includes unit tests and GameTests)
4. Update documentation if you change user-facing behavior
5. Open a PR with a clear description of what and why

### Code Style

- Match existing naming and package structure
- Prefer minimal, focused diffs over large refactors
- Use SLF4J (`LoggerFactory.getLogger("pushdozer")`) instead of `System.out/err`
- Comments in English or Chinese are both acceptable; keep them concise

### Tests

| Type | Location | Run via |
|------|----------|---------|
| Unit tests | `src/test/java` | `./gradlew test` |
| GameTests | `src/gametest/java` | `./gradlew runGameTest` |
| Full check | — | `./gradlew build` |

## Reporting Issues

Use [GitHub Issues](https://github.com/Theopote/pushdozer/issues) and include:

- Minecraft version, Fabric Loader version, Pushdozer version
- Steps to reproduce
- Expected vs actual behavior
- Relevant log snippets

## Documentation

User-facing documentation lives in the separate [Pushdozer-Introduction](https://github.com/Theopote/Pushdozer-Introduction) repository. Update both repos if your change affects gameplay or configuration.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
