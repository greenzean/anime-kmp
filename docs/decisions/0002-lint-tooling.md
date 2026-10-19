# 0002: Lint and format tooling

## Status
Accepted (M0-3)

## Decision
- Formatting: Spotless 8.10.3 with ktlint 1.8.0 and the Compose rules ruleset 0.6.7.
- Static analysis: detekt `dev.detekt` 2.0.0-alpha.6 with the Compose detekt rules.
- Both applied to all subprojects from the root `build.gradle.kts`. CI and the optional pre-commit hook run `./gradlew spotlessCheck detekt`.

## Consequences
- detekt 2.x is a pre-release. It is the only line that supports Kotlin 2.4 and AGP 9 (1.23.8 predates both). Revisit when 2.0 is stable.
- detekt formatting rules are not used; Spotless owns formatting.
