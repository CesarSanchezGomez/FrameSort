# Development

## Layout

```
com.cesarcosmico.framesort
├── FrameSortPlugin   composition root: wires everything, one tick task, reload
├── FrameSortLoader   downloads HikariCP and the MariaDB driver on first start
├── model/            plain rules: priorities, ranking, delivery, composting, paging, item references, trust
├── service/          target index and resolver, automark, delivery, sorters, pads, inspection, tracing, tags, trust
├── storage/          the database and the trust table
├── config/           typed settings, pads.yml, tags.yml, commands.yml, strict loading, validation, reload holder
├── item/             persistent data keys, item templates, the overlay on items FrameSort doesn't build, codecs
├── text/             messages and the chat pager
├── command/          one class per command feature; CommandTree builds the roots from commands.yml
├── listener/         Bukkit listeners, one per feature
├── integration/      worldguard/, craftengine/: the only code that imports those plugins, each loaded only when enabled
└── api/              TargetBindEvent, for other plugins
```

Packages depend on each other without cycles: `model` ← `item` ← `config` ← `text` ← `service` ← `command`,
`listener` and `integration`, with `storage` between `config` and `service` (it uses only `model` and `config`).
`api` is used only by `service` and `integration`, and only `FrameSortPlugin` creates
the integrations. Dependencies are wired by constructor in `FrameSortPlugin`; there are no static instances.

Interfaces exist only with several implementations, as closed sets, or to keep an optional plugin out:
- `CustomItemResolver`: a custom item as `tags.yml` writes it (`craftengine:<namespace>:<id>`), its name, the item
  built from a reference, whether a placed block is CraftEngine's and which one, and the item that places a block, or
  `NONE` without CraftEngine, so no other class loads CraftEngine's classes. All but `id` and `isCustomBlock` go
  through types outside CraftEngine's stable `api` package (`BukkitItemDefinition`, `ImmutableBlockState`,
  `BlockDefinition`): check them on every CraftEngine update;
- `DeliveryService.Source`: a sorter slot or a pad item entity;
- `CommandFeature`: one per command feature;
- `GiveCommand.ItemSource`: a sorter activator or a pad block;
- sealed `model.ItemReference`: an item or block named in the config, a vanilla material or a CraftEngine reference;
- sealed `PadService.Placement`: what placing a pad block did;
- sealed `PadService.Breaking`: what breaking a pad's top block did;
- sealed `SorterService.Slot`: an inventory slot, or one inside a shulker box or bundle.

`commands.yml` only places features: `CommandTree` joins every enabled path into Brigadier literals, makes a literal
visible to anyone allowed to run something below it, and checks each feature's own permission on its command and
arguments.

## Data

What belongs to a block or an entity stays in the world, so breaking it, pasting it or copying the world can't leave
it out of step:

| Where | Key | Content |
|---|---|---|
| Item frame entity | `framesort:target` | Present from marking until the frame is unmarked or another item is put in. An item can leave a frame with no event that confirms it, so the mark ends when the next one goes in; an empty frame is never a target, so a mark left on one does nothing. |
| Item frame entity | `framesort:owner` | The UUID, as text, of whoever marked the target or put a sorter's activator in. Removed when the mark ends. |
| Chunk | `framesort:pads` | The pads registered in the chunk, as `"x,y,z,type"` or `"x,y,z,type,owner"`; one saved before owners existed has none. A type id can't contain `,`. |
| Item | `framesort:item-tag` | The tag a tagged item stands for, such as `minecraft:logs` (`/framesort tag apply <tag>`). |
| Item | `framesort:item-tag-original` | The name, lore and glint a tagged item had before its tag, as item bytes, so `/framesort tag remove` gives them back. |
| Sorter dispenser | `framesort:sorted` | How many items the sorter has sent (a `long`); it goes away with the block. |
| Item | `framesort:activator` | The sorter activator handed out by FrameSort. |
| Item | `framesort:pad-item` | The pad type of a special pad block. |
| Player | `framesort:automark` | Present while the player has `/framesort automark` on. Saved with the player's data on this server, so each server of a network keeps its own, and read only while the player is online. |

Who trusts whom is read while the player who gave it is offline, so it lives in the database (`database.yml`):
one table, `framesort_trust`, with a row per trust (`owner`, `trusted`, `created_at`). UUIDs are 16 bytes, most
significant half first, so one UUID has one form; `created_at` is epoch milliseconds, which MySQL's `TIMESTAMP`
could not hold past 2038. The primary key is the pair (`WITHOUT ROWID` in SQLite). Migrations are
`db/<sqlite|mysql>/V<n>.sql`, applied in order, with the applied versions in `framesort_schema_version`.

The UUID columns are binary (`BLOB` in SQLite, `BINARY(16)` in MySQL and MariaDB), so database tools show them as
blobs. In game, `/framesort trusted` lists the names. To read the table by hand:

```sql
-- SQLite: 32 hex digits per UUID, without dashes
SELECT hex(owner), hex(trusted), datetime(created_at / 1000, 'unixepoch') FROM framesort_trust;
-- MySQL 8.0 or later
SELECT BIN_TO_UUID(owner), BIN_TO_UUID(trusted), FROM_UNIXTIME(created_at / 1000) FROM framesort_trust;
-- MariaDB: 32 hex digits per UUID, without dashes
SELECT HEX(owner), HEX(trusted), FROM_UNIXTIME(created_at / 1000) FROM framesort_trust;
```

## How deliveries stay correct and cheap

- **Target index.** Target frames are indexed by chunk as entities enter and leave the world (which includes chunk
  loads and unloads). Finding targets only looks at the chunks within `max-distance`, never at every entity in
  the world.
- **Cache.** Matches are cached per source and item (amount ignored) and invalidated by a per-world epoch that
  changes with any target change. A cached frame is checked again before use, and so are the loop guards (a frame
  on another sorter, items that would land on a pad): sorters and the blocks below change without touching a frame.
- **One plan.** `TargetResolver.plan` ranks the targets into levels of preference, each split into containers,
  drop spots and lava, nearest to the source first. Deliveries and inspections (`/framesort where`, inspecting with
  an item in the off hand) both use it, so an inspection shows exactly what a delivery would do, in its order.
- **No duplication.** Containers are looked up the moment items go in, and the source is updated after each
  container (`model.Delivery`). A container that vanished in between is skipped. `DeliveryTest` covers the case
  where a vanished container used to duplicate items.
- **No hopper listener.** FrameSort never listens to or cancels `InventoryMoveItemEvent`, so hoppers behave
  exactly as in vanilla and Paper can skip that event.
- **Pads.** Pads are registered in their chunk, so only loaded pads are checked, every `sweep-interval` ticks.
- **Chunk borders.** A frame on a chunk border can hang on a block of the next chunk. That block is only read when
  its chunk is loaded, so FrameSort never loads chunks.
- **Tracing.** With nobody tracing, a delivery costs one empty-map check.
- **Owners.** `TargetResolver.eligible` lets a source send to a frame only if they have the same owner, or the
  source's owner trusts the frame's (`model.TrustList.allows`); no owner counts as one more. The source's owner is part
  of the cache key, so a pad rebuilt on the same block by someone else never reuses the old matches. A change of
  trust raises every world's epoch (`TargetIndex.changedEverywhere`).
- **Trust in memory.** Sorters work while their owners are offline, so the whole trust list is loaded at enable and
  kept as an immutable snapshot: a delivery never touches the database.

## Threading

Everything that touches the world runs on the main thread, and a single repeating task ticks sorters and pads.

The only I/O is the trust table, on the database's single thread (`storage.Database`):
- at enable, the whole table is read with a timeout before anything else runs; if it fails, FrameSort disables
  itself instead of starting with nobody trusted;
- `/framesort trust` and `untrust` write first, and the database thread swaps the snapshot (`TrustService`) in the
  same task. Being the only thread that changes it, it applies writes and re-reads in the database's order, and a
  failed write never leaves a trust in memory. The cached matches are then cleared on the main thread through the
  global scheduler, which runs even if the player who typed the command has left; their message uses theirs;
- with MySQL or MariaDB, a timer re-reads the whole table every minute, for trust given on another server. A failed
  read keeps the snapshot and is logged once until a read works again;
- `onDisable` cancels that timer and the tick task, then closes the database, which waits for queued writes;
  nothing is scheduled any more once the plugin is disabling.

An inspection's page buttons (`InspectService`) are Adventure click callbacks, not a command: Paper runs them on the
main thread, and they last 12 hours and any number of clicks. Each keeps the source's position and a copy of the
inspected item, never live objects, and on a click checks again that the source is there in a loaded chunk and that
the player may still see it, then lists the targets anew.

Players' names come from the server's profile cache (`PlayerProfile#completeFromCache`), never from
`OfflinePlayer#getName`, which for an offline player reads their file in `playerdata/` on every call.

Highlights are temporary item displays that copy each frame's item, glowing in an exact colour
(`Display#setGlowColorOverride`). The display's transformation repeats what the vanilla item frame renderer does
(`ItemFrameRenderer#submit` in 26.2: offset, facing, rotation, half scale, minus the half turn the item display
renderer adds), so the copy lands exactly on the real item; `HighlightServiceTest` pins it. They are hidden from
everyone but the inspecting player (`setVisibleByDefault(false)` + `Player#showEntity`), never saved, and removed
after `highlight-seconds`, when the player leaves or when the plugin disables. Making the frame itself glow would
show it to every player, in a colour that depends on scoreboard teams, and Paper has no per-player glow without
packets.

Tracing ends on a timer per player, so the "tracing ended" message arrives on time even when nothing is delivered.

## Paper API to recheck

[`api-status.txt`](../api-status.txt) lists the Paper API FrameSort uses that may change: the registry tag API
(`@ApiStatus.Experimental`), through which `TagCatalog` reads item and block tags, and the data components
(`@MinecraftVersionDependent`) behind item templates, the overlay on tagged and CraftEngine items, and shulker box
and bundle contents. Check them again whenever Paper or Minecraft changes.

The activator's `components` reach Minecraft as `/give` syntax through `ItemFactory#createItemStack`, which checks
them on load and reload with the server's registries (main thread). Component names belong to Minecraft: one that a
new version renames or removes becomes a warning on load, not an error.
