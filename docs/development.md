# Development

## Build

```bash
./gradlew build
```

Requires a JDK 25 or newer (the build targets Java 25 bytecode). The jar is
`build/libs/FrameSort-<version>.jar`. The Paper API version lives in `gradle.properties`.

Tests use JUnit 5 and the Paper API types, but no server. Listeners and commands are checked by hand on a test
server.

## Layout

```
com.cesarcosmico.framesort
├── FrameSortPlugin   composition root: wires everything, one tick task, reload
├── model/            plain rules: priorities, best-priority set, delivery, frame positions, paging
├── service/          target index and resolver, delivery, sorters, pads, inspection, tracing, tags
├── config/           typed settings, pads.yml, commands.yml, validation, reload holder
├── item/             persistent data keys, item templates, the pad codec for chunks
├── text/             messages and the chat pager
├── command/          Brigadier commands, one class per subcommand
├── listener/         Bukkit listeners, one per feature
└── api/              TargetBindEvent, for other plugins
```

Dependencies are wired by constructor in `FrameSortPlugin`; there are no static instances. `DeliveryService.Source`
(a sorter slot or a pad item entity) and `Subcommand` are the only interfaces.

## Data

Nothing is stored outside the world:

| Where | Key | Content |
|---|---|---|
| Item frame entity | `framesort:target` | The frame is a target (manual registration); the value is the UUID of the player who marked it. |
| Chunk | `framesort:pads` | The pads registered in the chunk, as `"x,y,z,type"`. |
| Item | `framesort:activator` | The sorter activator handed out by FrameSort. |
| Item | `framesort:pad-item` | The pad type of a special pad block. |

## How deliveries stay correct and cheap

- **Target index.** Target frames are indexed by chunk as entities enter and leave the world (which includes chunk
  loads and unloads). Finding targets only looks at the chunks within `max-distance`, never at every entity in
  the world.
- **Cache.** Matches are cached per source and item (amount ignored) and invalidated by a per-world epoch that
  changes with any target change. A cached frame is checked again before use.
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

Architecture conventions shared with the other CesarCosmico plugins are described in the workspace's
`ARCHITECTURE-STANDARD.md`.
