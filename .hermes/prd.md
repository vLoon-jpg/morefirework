# PRD — morefirework

## Done state
A Minecraft Fabric mod (1.21) that turns fireworks into a combat system with ore-ingot rockets and elemental effects — compiles and works in-game.

## Stack
Java 21, Gradle, Fabric Loom 1.9, Yarn mappings, Fabric API

## Acceptance criteria
- `gradle --no-daemon build` produces a working .jar
- All ore rocket types craftable and functional in-game
- Elemental effects (bleed, fracture, heat) apply correctly
- Seeker behavior works on moving targets
- Effects NOT cleansable by milk bucket (intentional)

## Out of scope
- Unit tests (verification = compiles + in-game test)
- Forge port
- Other Minecraft versions
