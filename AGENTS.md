# Better Weathering Offline Progress

Forge 1.20.1 / Java 17; mod ID `better_weathering_offline_progress`.

## Local verification

- Deterministic: `./gradlew verifyFast --no-daemon`.
- Runtime changes: `./gradlew verifyFull --no-daemon`.
- Stage: `./gradlew stageRuntimeJar`, `build/libs/better-weathering-offline-progress-<version>.jar`.

## Shared authority

Read [workspace policy](../../better-content-modpack/docs/policies/workspace.md),
[testing](../../better-content-modpack/docs/testing.md) and
[disposal](../../better-content-modpack/docs/policies/generated-data.md).
Docs-only changes use the shared documentation check and `git diff --check`.
