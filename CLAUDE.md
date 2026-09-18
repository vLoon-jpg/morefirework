# More Firework — Minecraft Fabric mod

Turns fireworks into a combat system: ore-ingot rockets with elemental effects (see README.md for full feature/design table).

## Build

Requires **JDK 21** (`JAVA_HOME` must point to it).

```
gradle --no-daemon build
```

Output: `build/libs/morefirework-<version>.jar`. Version comes from `gradle.properties` (`mod_version`).

- Minecraft 1.21, Fabric Loom 1.9, Yarn mappings `1.21+build.9`, Fabric API `0.100.3+1.21`.
- No unit tests — verification is "it compiles" plus in-game testing by the user.

## Code map

All code under `src/main/java/com/morefirework/`:

- `effect/ModEffects.java` — the tick system and damage pipeline; most gameplay logic flows through here.
- `component/FireworkEffectComponent.java` — per-entity effect data (bleed stacks, fractures, heat signature). The most connected class in the codebase.
- `item/OreFireworkItem.java` — NBT-backed firework items.
- `effect/SeekerBehavior.java` — homing logic and speed curve.
- `mixin/` — entity hit detection, damage redirect, stun, NBT persistence, client stun. Mixin targets are Yarn-mapped Minecraft classes; check mappings before renaming anything.

## Knowledge graph

A Graphify graph exists at `graphify-out/` (210 nodes). For architecture questions, prefer `graphify query "<question>"` over reading many files, and see `graphify-out/GRAPH_REPORT.md` for an overview. Run `graphify update .` after significant code changes.

## Conventions

- Effects must NOT be cleansable by milk bucket; the vanilla Brush cleanses Bleed + Fracture only. This is intentional design, not a bug.
- Ore→rocket mapping and combo rules are documented in README.md — keep README in sync when adding rocket types.
