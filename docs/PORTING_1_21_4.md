# CivCraft Leaf 1.21.4 Porting Plan

This document is the working technical specification for moving the legacy Bukkit 1.8 CivCraft fork to Leaf 1.21.4.

## Scope

The legacy plugin cannot be safely recompiled against 1.21.4 as-is: it depends on removed Bukkit APIs, numeric item IDs, old NMS assumptions, TagAPI/HeroChat-era integrations, custom mobs, custom lore/NBT items, and synchronous schematic/block operations. The port therefore starts as a new `civcraft-leaf` module while the old source remains available for behaviour reference.

## External integrations chosen for the port

| Area | 1.21.4 integration | Decision |
| --- | --- | --- |
| Server core/API | Leaf 1.21.4 | Target Leaf runtime. Compile against Paper-compatible API where Leaf exposes inherited Paper compatibility, and isolate any future Leaf-only calls behind adapters. |
| Custom mobs | MythicMobs | Replace `CustomMobs` and old mob spawner logic with MythicMobs mob IDs and spawn adapters. |
| Weapons, armor, custom materials | MMOItems | Replace custom lore/NBT item system with MMOItems templates and CivCraft-specific MMOItems stats. |
| Map | Dynmap | Keep marker layers for town borders, culture borders, structures, trade goods, wonders, war state. |
| Schematics/buildings | FAWE preferred, WorldEdit fallback | Replace `.def` template paste/undo with schematic adapter and async-safe paste queue. |
| Economy | ExcellentEconomy | Prefer direct ExcellentEconomy service/API. If a stable public API is unavailable, use its Vault bridge only as a temporary compatibility layer. |
| Placeholders | PlaceholderAPI | Add `%civcraft_*%` placeholders for residents, camps, towns, civilizations, war and victory info. |
| Packets | PacketEvents, optional | Keep disabled until a feature requires packet interception; do not use packets for normal game state. |
| Future quests/dialogues | Typewriter | Expose CivCraft domain events through an internal event bus so Typewriter can subscribe later. |

## Implemented in this step

- Added a Maven parent and a new `civcraft-leaf` module.
- Added Leaf 1.21.4 plugin metadata with soft dependencies for MythicMobs, MMOItems, Dynmap, ExcellentEconomy, PlaceholderAPI, FAWE/WorldEdit, PacketEvents and Typewriter.
- Added default local SQLite bootstrap so a fresh install does not require external database setup.
- Added configuration sections for gameplay values, tick budgets, anti-dupe settings, storage and integrations.
- Added an integration registry that reports which required optional plugins are installed.
- Added a PlaceholderAPI expansion skeleton with reserved placeholder keys.

## Migration phases

### Phase 1: Domain model and storage

1. Recreate the core domain model with immutable IDs:
   - resident;
   - camp;
   - town;
   - civilization;
   - plot/chunk claim;
   - culture chunk;
   - structure;
   - wonder;
   - trade good;
   - relation;
   - war session;
   - random event;
   - unit training task.
2. Store every object in local SQLite by default.
3. Add MySQL/PostgreSQL as optional storage backends after schema stabilizes.
4. Use repository classes and transactions for all money/item/build operations.
5. Add idempotency keys for transfers, structure paste jobs and reward grants to prevent dupes after crash/retry.

### Phase 2: Config migration

1. Convert legacy `civcraft/data/*.yml` into modern configs with namespaced keys.
2. Replace numeric item IDs with Bukkit `Material` names or MMOItems type/id pairs.
3. Move every gameplay-affecting value into config:
   - costs;
   - upkeep;
   - HP;
   - ranges;
   - timers;
   - drop chances;
   - reward amounts;
   - victory thresholds;
   - tax limits;
   - tower fire rates;
   - anti-dupe limits.
4. Move every player-facing message into `messages.yml` and support MiniMessage formatting.

### Phase 3: Items via MMOItems

1. Replace `LoreCraftableMaterial`, legacy custom item IDs and direct lore parsing.
2. Represent CivCraft items as MMOItems templates:
   - units;
   - structure build items;
   - camp founder item;
   - catalysts;
   - trade good tokens;
   - arena rewards;
   - weapons/armor.
3. Add CivCraft MMOItems stats:
   - `CIVCRAFT_SOULBOUND`;
   - `CIVCRAFT_OWNER_CIV`;
   - `CIVCRAFT_OWNER_TOWN`;
   - `CIVCRAFT_UNIT_TYPE`;
   - `CIVCRAFT_STRUCTURE_ID`;
   - `CIVCRAFT_NO_TRADE`;
   - `CIVCRAFT_CATALYST_LEVEL`.
4. Validate item identity through MMOItems API, not display name or raw lore.

### Phase 4: Mobs via MythicMobs

1. Replace legacy `CustomMobs` dependency and mob spawner populator.
2. Add config mapping from CivCraft mob roles to MythicMobs IDs.
3. Spawn mobs through MythicMobs API and track spawned entity UUIDs in storage.
4. Convert random events and mob grinder logic to use MythicMobs spawn adapters.

### Phase 5: Structures via FAWE/WorldEdit

1. Convert template `.def` files to WorldEdit schematics.
2. Keep the structure definition metadata in YAML:
   - cost;
   - upkeep;
   - hammer cost;
   - schematic path;
   - paste offset;
   - bounding box;
   - required tech;
   - required structure;
   - components.
3. Queue paste operations and apply per-tick block budgets.
4. Record undo snapshots before paste.
5. Validate floating structures, bounding overlap, protected blocks and chunk ownership before charging money.
6. Charge money and items only after validation succeeds, and persist a pending build transaction before mutating the world.

### Phase 6: Economy via ExcellentEconomy

1. Define a CivCraft economy adapter:
   - `getBalance(account)`;
   - `deposit(account, amount, reason, idempotencyKey)`;
   - `withdraw(account, amount, reason, idempotencyKey)`;
   - `transfer(from, to, amount, reason, idempotencyKey)`.
2. Support accounts:
   - resident;
   - town;
   - civilization;
   - server sink/source.
3. Reject negative and NaN amounts.
4. Store every transaction in CivCraft storage before/with external economy mutation.
5. Use ExcellentEconomy direct API if documented/stable; otherwise require its Vault bridge and clearly mark the bridge as compatibility mode.

### Phase 7: Dynmap

1. Replace the separate legacy dynmap plugin with an internal optional Dynmap adapter.
2. Create marker sets:
   - `civcraft.towns`;
   - `civcraft.culture`;
   - `civcraft.structures`;
   - `civcraft.wonders`;
   - `civcraft.trade_goods`;
   - `civcraft.war`.
3. Incrementally update changed objects only; do not redraw the whole map every few seconds.
4. Batch marker updates by config budget.

### Phase 8: PlaceholderAPI

Reserved placeholders should include at least:

- `%civcraft_player_civ%`
- `%civcraft_player_town%`
- `%civcraft_player_camp%`
- `%civcraft_player_coins%`
- `%civcraft_player_rank%`
- `%civcraft_civ_name%`
- `%civcraft_civ_leader%`
- `%civcraft_civ_government%`
- `%civcraft_civ_towns%`
- `%civcraft_civ_score%`
- `%civcraft_civ_research%`
- `%civcraft_civ_research_percent%`
- `%civcraft_town_name%`
- `%civcraft_town_mayor%`
- `%civcraft_town_level%`
- `%civcraft_town_residents%`
- `%civcraft_town_happiness%`
- `%civcraft_town_culture_level%`
- `%civcraft_camp_name%`
- `%civcraft_camp_owner%`
- `%civcraft_camp_hitpoints%`
- `%civcraft_camp_firepoints%`
- `%civcraft_war_active%`
- `%civcraft_war_time_left%`

### Phase 9: Typewriter future support

Do not hard-depend on Typewriter. CivCraft should expose domain events first:

- `CivCreatedEvent`
- `TownFoundedEvent`
- `CampFoundedEvent`
- `StructureCompletedEvent`
- `WonderCompletedEvent`
- `TechnologyUnlockedEvent`
- `WarStartedEvent`
- `WarEndedEvent`
- `TownCapturedEvent`
- `CivilizationDefeatedEvent`
- `VictoryCountdownStartedEvent`

A Typewriter adapter can later translate these events into entries/objectives/dialogue triggers without changing core gameplay code.

## Open technical questions

1. ExcellentEconomy direct developer API must be confirmed. Public pages emphasize Vault and PlaceholderAPI support; if direct API is not stable, the first production port should use the Vault bridge while we keep an ExcellentEconomy-specific adapter boundary.
2. MMOItems 7 is documented as API-breaking in Phoenix docs. We should pin MMOItems API version per server build and isolate all MMOItems calls in one module.
3. MythicMobs artifact versions vary by release. The adapter should be compiled only after selecting the exact MythicMobs server jar/API version.
4. PacketEvents is not needed yet unless we implement packet-level nametags, fake blocks, client-side previews or anti-cheat visualizations.
5. Legacy `.def` templates need a converter or manual rebuild into modern schematic format.


## Leaf runtime decision

Leaf is the production target. The module is named `civcraft-leaf`, and runtime config defaults to `server.target-core: Leaf` plus `server.require-leaf-runtime: true`.

For the first implementation phase the code compiles against Paper API because Leaf is a Paper fork and advertises full Paper plugin compatibility. This keeps the build stable for 1.21.4 while we avoid accidental dependency on a Leaf API artifact that may not exist for the exact selected 1.21.4 build. If we later need Leaf-only APIs, they must be added behind a small adapter module after pinning the exact Leaf API Maven coordinates for the server build.

Current scheduler strategy is `bukkit-main-thread`. If the selected Leaf build adds region/threading APIs that should be used for structure paste queues or chunk work, the scheduler must be replaced through an adapter rather than directly in gameplay services.

## Playable plugin-owned mechanics baseline

This repository now contains a runnable Leaf plugin module rather than a document-only scaffold. The first playable baseline intentionally keeps all core CivCraft ownership in the plugin:

- resident profiles and coin balances are stored by CivCraft in local SQLite;
- camps are created by CivCraft commands and persisted by CivCraft tables;
- civilizations are created by CivCraft commands and persisted by CivCraft tables;
- towns are created by CivCraft commands, validated by CivCraft distance rules, and persisted by CivCraft tables;
- PlaceholderAPI values read CivCraft-owned state;
- Leaf is treated as the server runtime, not as the gameplay owner.

Leaf should provide the server API/runtime only. MythicMobs, MMOItems, Dynmap, ExcellentEconomy, FAWE/WorldEdit, PlaceholderAPI, PacketEvents and Typewriter must remain adapters around CivCraft-owned domain services. If an external plugin disappears, only the adapter-backed feature should degrade; ownership of residents, camps, towns, civilizations, wars, structures and victory state stays in CivCraft storage.

Current runnable commands:

- `/resident` or `/res` shows/creates the resident profile.
- `/camp create <name>` creates a camp and charges `camp.cost`.
- `/civ create <name>` creates a civilization and assigns the leader.
- `/town create <name>` creates a town for the player's civilization and enforces `town.min-town-distance`.
- `/civcraftmodern integrations` reports runtime/integration status.
- `/civcraftmodern reload` reloads config and refreshes integration state.
