# Better Weathering Offline Progress

## Native source fixture safety

`verifyFull` runs the four authored native GameTests in a fresh `build/gametest/<UUID>/`
ForgeGradle RunConfig. Preparation reads that actual configuration, not an early JavaExec
working directory, and creates only absent comment-only settings (native defaults).
Structures are copied into the new fixture; old worlds/logs and existing settings are
untouched. Actual startup/shutdown, exactly four completed/passed required tests and
zero ERROR/FATAL records are required. `verifyFast` exercises settings preservation and
negative diagnostic/population controls without Minecraft. Retain complete old builds,
libraries, fixtures and evidence before any independently authorized clean/reset.

## Playtest diagnostics

`bc.playtest.better_weathering_offline_progress.catchup` reports catch-up/pending-batch
outcomes, random-ticks-disabled/no-exposure skips, action candidates, state mismatch and
operations invoked. Invocations are not observed successful block mutations; this remains
UNKNOWN. Logging adds no RNG draws, state rescans or save-callback mutation. Native stop
clears the pending/deferred dimension queues and diagnostic aggregation.

The legacy `debugLogging` switch gates only the focused TRACE endpoint sample. New causal
DEBUG is independent of it. TRACE also requires logger TRACE and JVM properties
`bc.playtest.trace.better_weathering_offline_progress.dimension=namespace:path` plus
`bc.playtest.trace.better_weathering_offline_progress.pos=x,0,z` at the chunk's minimum
block coordinates. Invalid/absent focus gives no detail and one status; first match arms
60 seconds/1000 records once per process. Categories share120 details/minute, burst20,
256 keys and2048 chars. Native ticks summarize30-second windows without background work.
INFO startup supplies enabled/version/integration/own-source metadata or UNKNOWN.
Standalone stays native INFO; pack profile chooses scoped DEBUG/`bc.playtest.level`.
Common six-case and invoked-versus-mutated projection tests run with the source gate.

## Scope

A Better Content Forge 1.20.1 integration that gives Immersive Weathering a
bounded unloaded-time endpoint update.

The mod does not tick unloaded chunks and does not replay missed ticks. Each
dimension keeps four cumulative exposure counters (clear/rain and day/night).
A chunk stores its last counter snapshot. When that chunk returns, the sampler
subtracts the snapshots, converts the elapsed exposure into at-least-one-event
probabilities, scans existing block states once, and applies a shuffled set of
direct endpoint changes.

Normal loaded Immersive Weathering behavior remains unchanged. Unloaded
Activity remains installed for block entities and its other supported systems.
The sampler deliberately excludes historical entities, item drops, fluid
updates, lightning, player-triggered mechanics, and recursive growth from newly
created blocks.

## Configuration

`config/better_weathering_offline_progress-common.toml` controls the minimum unloaded
interval, probability density multiplier, and debug logging. A newly installed
world initializes chunk snapshots on first observation, so no time before the
mod was installed is applied retroactively.

## Native save lifecycle

Exposure snapshots are written into the current Forge chunk-save NBT using the
existing schema. The save callback updates its in-memory snapshot without marking
the chunk dirty again: Minecraft clears that flag before writing and native flush
repeats while chunks remain dirty. Marking every saved chunk dirty caused an
unbounded flush loop and watchdog termination. Normal load/tick/unload snapshot
changes still mark the chunk dirty, and saving never clears another mutation's
existing dirty flag. Pending/deferred unload snapshot rules remain unchanged.

## Validation

```sh
./gradlew verifyFast --no-daemon
./gradlew verifyFull --no-daemon
```
