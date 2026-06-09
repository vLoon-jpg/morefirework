# More Firework

A Minecraft Fabric mod that turns fireworks into a deep combat system. Craft ore-ingot rockets, chain elemental effects, and unleash devastating combos.

![Minecraft 1.21](https://img.shields.io/badge/Minecraft-1.21-brightgreen) ![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-yellow) ![License MIT](https://img.shields.io/badge/License-MIT-blue)

---

## Features

| Ore | Rocket Type | What It Does |
|---|---|---|
| 💎 **Diamond** | Stab | Marks armor — stabbed armor gives **zero protection** and zero Thorns |
| ⛏️ **Iron** | Bleed | Spreads bleed stacks — 0.5 hearts per stack every 2 seconds. Decays over time |
| 🟡 **Gold** | Shotgun | Shatters on impact into 8 shrapnel pieces in a 60° cone. Devastating up close |
| 🟢 **Emerald** | Tracker | Applies Heat Signature — makes enemy glow and **enables Seeker rockets to home** |
| 🟣 **Amethyst** | Fracture | Stack fractures on armor → redirects HP damage to durability → **Crystalized** stun finisher |
| 🔴 **Redstone** | Addon | Craft onto ANY rocket to make it a **Seeker** — homes on Emerald-marked targets |

### Signature Combo: Emerald → Gold Seeker
1. Tag with Emerald rocket (glow + heat signature)
2. Fire Gold + Redstone Seeker
3. Rocket charges slow, launches sluggish... then locks on and accelerates past Elytra speed
4. Impact → shotgun shrapnel burst

### Crystalized (all 4 armor pieces fractured)
- 5-second stun (head movement still allowed)
- 50% armor reduction for 10 seconds
- Permanent 15% max durability loss
- 1-minute fracture immunity after

### Counterplay
- **Milk bucket** — does NOT work on mod effects
- **Vanilla Brush** — right-click cleanses Bleed + Fracture only (tracking effects stay)
- **Shields** — block nothing except partial Gold shrapnel (50% reduction, eats durability)

---

## Installation

### Prerequisites
- Minecraft Java Edition 1.21
- [Fabric Loader](https://fabricmc.net/use/) 0.15.11+
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.100.3+

### Steps
1. Download `morefirework-0.1.0.jar` from [Releases](../../releases)
2. Drop into `%appdata%\.minecraft\mods\`
3. Also drop Fabric API jar into `mods\`
4. Launch with Fabric Loader 1.21 profile

---

## Development

### Build from source

```bash
git clone https://github.com/vLoon-jpg/morefirework.git
cd morefirework

# Requires JDK 21
export JAVA_HOME="/path/to/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

gradle --no-daemon build
# Output: build/libs/morefirework-0.1.0.jar
```

### Tech stack
- **Minecraft** 1.21
- **Mod loader** Fabric + Fabric API
- **Mappings** Yarn 1.21+build.9
- **Build** Fabric Loom 1.9 + Gradle
- **Language** Java 21

### Project structure
```
src/main/java/com/morefirework/
├── MoreFirework.java          # Mod init
├── component/
│   ├── FireworkEffectComponent.java  # Per-entity effect data
│   └── ModComponents.java            # Static component registry
├── effect/
│   ├── GoldShotgun.java       # Cone AOE shrapnel
│   ├── ModEffects.java        # Tick system + damage pipeline
│   └── SeekerBehavior.java    # Homing + speed curve
├── item/
│   ├── ModItems.java          # Item registration
│   └── OreFireworkItem.java   # NBT-backed firework items
└── mixin/
    ├── FireworkRocketEntityMixin.java  # Entity hit + seeker tick
    ├── LivingEntityMixin.java          # Damage redirect
    ├── PlayerEntityMixin.java          # Stun block
    ├── PlayerDataMixin.java            # NBT persistence
    └── ClientPlayerEntityMixin.java    # Client stun
```

---

## License

MIT — see [LICENSE](LICENSE)

---

Built by [vLoon-jpg](https://github.com/vLoon-jpg) with Tailor
