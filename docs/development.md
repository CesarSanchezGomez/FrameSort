# Development

## Build

```bash
./gradlew build
```

Gradle 9.8 with a Java 25 toolchain; the foojay resolver downloads the JDK when it is missing. The jar is
`build/libs/FrameSort-<version>.jar`. Versions live in `gradle.properties`, with `paper-api` pinned to an exact
build. `./gradlew runServer` starts a Paper 26.2 test server in `run/`.

Tests use JUnit 6 and the Paper API types, but no server. Listeners and commands are checked by hand on a test
server.

## Layout

```
com.cesarcosmico.framesort
├── FrameSortPlugin   composition root: wires everything, one tick task, reload
├── model/            plain rules: priorities, ranking by priority, delivery, composting, frame positions, paging
├── service/          target index and resolver, delivery, sorters, pads, inspection, tracing, tags
├── config/           typed settings, pads.yml, commands.yml, strict YAML loading, validation, reload holder
├── item/             persistent data keys, item templates, the pad codec for chunks
├── text/             messages and the chat pager
├── command/          one class per command feature; CommandTree builds the roots from commands.yml
├── listener/         Bukkit listeners, one per feature
├── integration/      worldguard/: the only code that imports WorldGuard, loaded only when it is enabled
└── api/              TargetBindEvent, for other plugins
```

Packages depend on each other without cycles: `model` ← `item` ← `config` ← `text` ← `service` ← `command`,
`listener` and `integration`. `api` is used only by `service` and `integration`, and only `FrameSortPlugin` creates
the integration. Dependencies are wired by constructor in `FrameSortPlugin`; there are no static instances.

Interfaces exist only with several implementations or as closed sets:
- `DeliveryService.Source`: a sorter slot or a pad item entity;
- `CommandFeature`: one per command feature;
- `GiveCommand.ItemSource`: a sorter activator or a pad block;
- sealed `PadService.Placement`: what placing a pad block did;
- sealed `SorterService.Slot`: an inventory slot, or one inside a shulker box or bundle.

`commands.yml` only places features: `CommandTree` joins every enabled path into Brigadier literals, makes a literal
visible to anyone allowed to run something below it, and checks each feature's own permission on its command and
arguments.

## Data

Nothing is stored outside the world:

| Where | Key | Content |
|---|---|---|
| Item frame entity | `framesort:target` | Present while the frame is a target (manual registration). |
| Chunk | `framesort:pads` | The pads registered in the chunk, as `"x,y,z,type"`. |
| Item | `framesort:item-tag` | The tag a tagged item stands for, such as `minecraft:logs` (`/framesort tag apply <tag>`). |
| Sorter dispenser | `framesort:sorted` | How many items the sorter has sent (a `long`); it goes away with the block. |
| Item | `framesort:activator` | The sorter activator handed out by FrameSort. |
| Item | `framesort:pad-item` | The pad type of a special pad block. |

## How deliveries stay correct and cheap

- **Target index.** Target frames are indexed by chunk as entities enter and leave the world (which includes chunk
  loads and unloads). Finding targets only looks at the chunks within `max-distance`, never at every entity in
  the world.
- **Cache.** Matches are cached per source and item (amount ignored) and invalidated by a per-world epoch that
  changes with any target change. A cached frame is checked again before use.
- **One plan.** `TargetResolver.plan` ranks the targets into levels of preference, each split into containers,
  drop spots and lava. Deliveries and inspections (`/framesort where`, inspecting with an item in the off hand) both
  use it, so an inspection shows exactly what a delivery would do.
- **No duplication.** Containers are looked up the moment items go in, and the source is updated after each
  container (`model.Delivery`). A container that vanished in between is skipped. `DeliveryTest` covers the case
  where a vanished container used to duplicate items.
- **No hopper listener.** FrameSort never listens to or cancels `InventoryMoveItemEvent`, so hoppers behave
  exactly as in vanilla and Paper can skip that event.
- **Pads.** Pads are registered in their chunk, so only loaded pads are checked, every `sweep-interval` ticks.
- **Chunk borders.** A frame on a chunk border can hang on a block of the next chunk. That block is only read when
  its chunk is loaded, so FrameSort never loads chunks.
- **Tracing.** With nobody tracing, a delivery costs one empty-map check.

## Threading

Everything runs on the main thread: there is no I/O. A single repeating task ticks sorters and pads.

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
(`@MinecraftVersionDependent`) behind item templates, tagged items and shulker box and bundle contents. Check them
again whenever Paper or Minecraft changes.

## Manual checks

Listeners, commands and anything that needs a world are checked by hand on a test server (`./gradlew runServer`)
before each release:

- **Commands and permissions** (`commands.yml`):
  - a player with only one feature's permission sees the root command but can't run the help;
  - a feature with `enabled: false` disappears from every path;
  - a path of its own (`/fstrace`) works after a restart;
  - `/framesort inspect 1` from the console answers that it is for players.
- **Targets:** sneak + right-click marks and unmarks a frame without rotating its item; sneak + left-click shows the
  frame card and leaves the item in the frame.
- **Sorters:** a dispenser with the activator sends up to 64 items every `tick-rate`, from inside shulker boxes and
  bundles too; an item with nowhere to go doesn't stall it; breaking the dispenser ends the sorter without errors;
  with `disable-when-powered`, redstone stops it.
- **Pads:** a pad exists only once its top block sits on its base; pistons and explosions leave it in place;
  in `item` mode, breaking it gives the special item back.
- **Overflow:** with the preferred chest full, items go on to the next targets, down to the default target; an
  exact lava frame wins over a default-target chest.
- **`/framesort where`:** highlights where the held item would go from the nearest sorter or pad, level by level;
  from a sorter, a filled bundle says the sorter opens it; from a pad, it lists where the whole bundle goes.
- **WorldGuard:** a sorter never sends to a frame in a different set of regions, and its inspection does not list it.
- **Tracing:** a busy sorter draws one streak per target frame per turn, not one per item.
- **Leaving:** a player who quits while tracing or with highlights showing leaves nothing behind (no errors, no
  glowing copies).
- **No duplication:** break a target chest while a sorter is filling it and count the items before and after.
