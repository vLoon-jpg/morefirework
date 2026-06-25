# Graph Report - morefirework  (2026-06-25)

## Corpus Check
- 48 files · ~10,642 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 228 nodes · 622 edges · 12 communities (11 shown, 1 thin omitted)
- Extraction: 82% EXTRACTED · 18% INFERRED · 0% AMBIGUOUS · INFERRED: 112 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `2abb657d`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]

## God Nodes (most connected - your core abstractions)
1. `FireworkEffectComponent` - 58 edges
2. `OreFireworkItem` - 12 edges
3. `SeekerData` - 10 edges
4. `OreFireworkRecipe` - 10 edges
5. `SeekerBehavior` - 8 edges
6. `ModComponents` - 7 edges
7. `ModEffects` - 7 edges
8. `FireworkRocketEntityMixin` - 7 edges
9. `CrossbowItemMixin` - 6 edges
10. `LivingEntityMixin` - 5 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Import Cycles
- None detected.

## Communities (12 total, 1 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.10
Nodes (5): FireworkEffectComponent, GoldShotgun, ModEffects, EquipmentSlot, LivingEntity

### Community 1 - "Community 1"
Cohesion: 0.10
Nodes (18): ActionResult, ClientModInitializer, CraftingRecipeCategory, CraftingRecipeInput, FireworkRocketItem, Integer, Item, OreFireworkItem (+10 more)

### Community 2 - "Community 2"
Cohesion: 0.10
Nodes (18): Boolean, CallbackInfoReturnable, DamageSource, Float, Inject, Iterable, List, ClientPlayerEntityMixin (+10 more)

### Community 3 - "Community 3"
Cohesion: 0.18
Nodes (7): SeekerBehavior, SeekerData, FireworkRocketEntity, FireworkRocketEntityMixin, Shadow, Vec3d, World

### Community 4 - "Community 4"
Cohesion: 0.19
Nodes (8): CallbackInfo, ModComponents, Entity, PlayerDataMixin, NbtCompound, ServerWorld, Set, UUID

### Community 5 - "Community 5"
Cohesion: 0.14
Nodes (13): Build from source, Counterplay, Crystalized (all 4 armor pieces fractured), Development, Features, Installation, License, More Firework (+5 more)

### Community 6 - "Community 6"
Cohesion: 0.22
Nodes (7): Identifier, ModItems, OreType(), ModInitializer, MoreFirework, String, T

### Community 7 - "Community 7"
Cohesion: 0.50
Nodes (3): Graph stats (last build), How to use it, More Firework Mod — Knowledge Graph

### Community 8 - "Community 8"
Cohesion: 0.39
Nodes (5): ChargedProjectilesComponent, CrossbowItemMixin, Overwrite, Predicate, ProjectileEntity

## Knowledge Gaps
- **11 isolated node(s):** `How to use it`, `Graph stats (last build)`, `Signature Combo: Emerald → Gold Seeker`, `Crystalized (all 4 armor pieces fractured)`, `Counterplay` (+6 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `FireworkEffectComponent` connect `Community 0` to `Community 2`, `Community 3`, `Community 4`?**
  _High betweenness centrality (0.224) - this node is a cross-community bridge._
- **What connects `How to use it`, `Graph stats (last build)`, `Signature Combo: Emerald → Gold Seeker` to the rest of the system?**
  _11 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.09863945578231292 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.09523809523809523 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.10465116279069768 - nodes in this community are weakly interconnected._
- **Should `Community 5` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._