# Better Weathering Offline Progress

## Scope and authority

This repository owns its mod-specific behavior and authoring inputs. Read [local instructions](AGENTS.md)
and the [shared documentation/policy index](../../better-content-modpack/docs/README.md).


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
