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

## Legacy `.def` structure compatibility

The Leaf module now includes a legacy `.def` template loader and a throttled paste queue so existing CivCraft templates can be used before converting them to WorldEdit schematics.

Current behaviour:

- `.def` files are parsed in the original format: header `sizeX;sizeY;sizeZ`, followed by `x:y:z,legacyId:data` lines and optional sign text columns.
- Legacy numeric block IDs are mapped to modern 1.21 materials by CivCraft's `LegacyMaterialMapper`.
- Templates are pasted by CivCraft itself through a per-tick queue controlled by `global.tick-budgets.max-structure-blocks-per-tick`.
- Paste jobs are recorded in CivCraft SQLite table `structure_builds` and marked complete when the queue finishes.
- `/build list` lists configured legacy structures.
- `/build legacy structures <template> [direction]` pastes an old-format structure at the player's current block location.

Limitations to resolve in later mechanics phases:

- legacy block data orientation is not fully preserved for every block type yet;
- protected blocks, town ownership checks, cost charging, hammer progress, undo snapshots and structure components must be connected to the full structure domain model;
- old templates should still eventually be converted to schematics for FAWE/WorldEdit large-scale operations, but `.def` remains supported for compatibility.

## Structure definitions, charging and protected blocks

The next structure phase connects old template compatibility to CivCraft's old `structures.yml` definitions:

- `StructureDefinitionService` loads ids, display names, costs, upkeep, hammer cost, max HP and template names from `civcraft/data/structures.yml`.
- `/build list` now lists configured structures with costs.
- `/build structure <id> [direction]` resolves the structure definition, charges the resident from CivCraft's local economy, loads the old `.def` template and queues the paste.
- Placed non-air blocks are recorded in the CivCraft-owned `protected_blocks` table.
- `ProtectedBlockListener` prevents non-admin players from breaking protected CivCraft structure blocks.

This is still not the complete old building loop: hammers, build progress over time, town plot ownership, upkeep ticks, requirements and all structure components must be layered on top of this protected structure domain.

## Technology and beaker research mechanics

The next implemented mechanics slice ports the old CivCraft technology loop into plugin-owned state instead of delegating it to Leaf or an economy/core plugin.

Current behaviour:

- `TechDefinitionService` reads legacy technology definitions from `technologies.config`, defaulting to `civcraft/data/techs.yml`, and keeps the old fields `id`, `name`, `beaker_cost`, `cost`, `points`, `require_techs` and `era`.
- CivCraft persists completed technologies in `civ_technologies` and the single active civilization research task in `civ_research`.
- `/research list` lists available loaded technologies.
- `/research start <id>` validates that the player belongs to a civilization, checks required technologies, rejects duplicate/completed research, charges the resident locally and starts the active research in one SQLite transaction.
- A plugin-owned minute timer adds beakers from config values: `technologies.base-beakers-per-minute` plus `technologies.town-beakers-per-minute` for every town owned by the civilization.
- When enough beakers are accumulated, CivCraft atomically records the technology as completed and clears the active research row.
- PlaceholderAPI exposes `%civcraft_civ_research%`, `%civcraft_civ_research_progress%` and `%civcraft_civ_research_percent%` from CivCraft storage.

Technical notes and remaining work:

- This implementation intentionally uses CivCraft's local resident balance until the ExcellentEconomy adapter is implemented. The adapter should later wrap the same transaction boundary with idempotency keys.
- The old technology unlock effects are not all connected yet. Structure requirements, item unlocks, unit unlocks, diplomacy/government gates and scoreboard/victory points should query `civ_technologies` through a dedicated policy service.
- The current beaker rate is a safe config-based baseline. Legacy cottage/library/university/wonder bonuses still need to be mapped to structure components and town upkeep ticks.

## Town territory claims and chunk protection

The next town mechanics slice adds CivCraft-owned territory claims for cities. Leaf only provides block/chunk events; ownership decisions live in CivCraft storage and services.

Current behaviour:

- `town_claims` stores claimed chunks by `world`, `chunk_x`, `chunk_z`, owning `town_id`, owning `civ_id`, claimant UUID and timestamp.
- `/town claim` claims the player's current chunk for their town after validating same-world distance from the town center, max claim count and claim cost.
- `/town unclaim` removes the current chunk if it belongs to the player's town.
- `/town claims` displays the current town claim count against `town.max-claims`.
- Claim creation charges the resident and inserts the claim in one SQLite transaction, so failed claims do not lose money.
- `TownClaimProtectionListener` blocks place/break actions in claimed chunks for players outside the owning civilization, while `civcraft.admin` bypasses protection.
- PlaceholderAPI exposes `%civcraft_town_claims%`.

Technical notes and remaining work:

- This is a chunk-level baseline. The old plot/culture border system still needs fine-grained plot records, culture expansion, upkeep integration and Dynmap marker rendering.
- Claim limits are currently global config values. Later town halls, cottages, wonders and government effects should contribute modifiers through structure components.
- Player membership is still minimal in this port baseline; once resident join/invite mechanics are ported, claim build permission should use role/rank policy rather than only civilization id.

## ExcellentEconomy/Vault money bridge and banks

The money layer now has a dedicated `CivCraftEconomyService` instead of reading only the local `residents.coins` column. This is the intended production path for ExcellentEconomy on Leaf.

Current behaviour:

- Config defaults to `integrations.economy.provider: ExcellentEconomy`, `integrations.economy.currency: money` and `integrations.economy.use-vault-bridge-if-direct-api-unavailable: true`.
- When Vault and a Vault economy service are present, CivCraft uses that service for player balances, deposits and withdrawals. ExcellentEconomy must be configured with `Integration.Vault.Enabled: true` and `Integration.Vault.EconomyCurrency: money` so Vault points to the same currency.
- If Vault or the Vault economy service is missing, CivCraft falls back to the local SQLite `residents.coins` column so development servers still boot and gameplay commands still work.
- Player-facing costs for camps, civilizations, towns, town claims, structures and research now withdraw from the economy service first and refund if CivCraft persistence fails.
- `/town deposit <amount>` and `/town withdraw <amount>` move money between the player economy account and the CivCraft-owned town bank. Only the mayor can withdraw.
- `/civ deposit <amount>` and `/civ withdraw <amount>` move money between the player economy account and the CivCraft-owned civilization bank. Only the leader can withdraw.
- `%civcraft_player_coins%` and `/resident` now read the active economy service rather than blindly showing the local fallback balance.

Technical notes and remaining work:

- ExcellentEconomy documents Vault as the supported bridge for selecting a currency as the server economy currency, so this port uses Vault as the stable compile-time API while keeping the adapter boundary ready for a direct ExcellentEconomy API implementation.
- External economy operations cannot be part of the same SQLite transaction. The current implementation uses pre-check/withdraw plus refund-on-persistence-failure. Before production launch, every economy mutation should receive an idempotency key table so crash retries cannot duplicate refunds or charges.
