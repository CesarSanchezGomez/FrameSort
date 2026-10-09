# Configuration

Every file has a `config-version`. When FrameSort's defaults change, the console lists missing and unknown keys;
your files are never rewritten. Invalid values are reported with their path and replaced by the default.
`/framesort reload` applies `config.yml`, `pads.yml` and the language file and tells you how many values fell back
to their defaults. A file that is not valid YAML stops the reload and the previous configuration stays active.
`commands.yml` needs a restart.

Text values are written in single quotes; an apostrophe inside one is written twice (`'can''t'`). Text with
several lines (help, lore, the page layout) is a YAML list, one line per entry. Item lore starts and ends with an
empty line (`''`) and keeps each line short.

## config.yml

### sorter

| Key | Default | Meaning |
|---|---|---|
| `activator.material` | `ENDER_EYE` | Item that turns a dispenser into a sorter when placed in a frame on it. |
| `activator.require-marked` | `false` | `true`: only the activator from `/framesort give sorter` works, not any item of that material. |
| `activator.name`, `lore`, `glint`, `item-model` | | Look of the activator FrameSort hands out (MiniMessage). |
| `frame-types` | `[ITEM_FRAME, GLOW_ITEM_FRAME]` | Frames that can hold the activator. |
| `custom-name` | `<gradient:#F7B733:#FC4A1A>Item Sorter</gradient>` | Name shown in the sorter's inventory. `''` = none. |
| `tick-rate` | `20` | Ticks between two sends of the same sorter. |
| `disable-when-powered` | `false` | A powered sorter stops sending. |
| `hide-frame` | `true` | Hides the activator's frame while the sorter works. |
| `show-activity` | `true` | Rotates the activator every time the sorter sends something. |

A sorter never dispenses like a plain dispenser. It sends one random stack per turn; when the stack is a shulker box
or bundle with contents, it sends a stack from inside instead.

### targets

| Key | Default | Meaning |
|---|---|---|
| `registration` | `manual` | `manual`: only frames marked by sneak + right-clicking them with an empty hand. `automatic`: every frame of `frame-types`. |
| `positions` | `[FRONT]` | Where a target frame may hang on its block: `TOP`, `BOTTOM`, `FRONT`, `BACK`, `LEFT`, `RIGHT` (`SIDES` = `LEFT` + `RIGHT`). `FRONT`, `BACK`, `LEFT` and `RIGHT` follow the way the block faces (chests, barrels, furnaces…); left and right are as seen standing in front of it. On blocks without a horizontal facing (hoppers, upright barrels, composters) a side face counts as any of the four. |
| `frame-types` | `[ITEM_FRAME, GLOW_ITEM_FRAME]` | Frames that can be targets. |
| `filter-glint` | `true` | Tag filters get the enchantment glint, so they stand out in inventories and frames. |
| `filter-name` | `'<gradient:#F7B733:#FC4A1A>#<tag></gradient>'` | Name a tag filter gets (MiniMessage); `<tag>` is the tag, without `minecraft:` for vanilla tags. |

Only the front counts by default, so decorative frames on the top or sides of a chest are never used. Add positions
only where frames are meant to be targets, especially with `automatic`.

### delivery

| Key | Default | Meaning |
|---|---|---|
| `max-distance` | `64` | Targets are searched within this many blocks of the sorter or pad. |
| `insert-into-containers` | `true` | `true`: items go into the container behind the frame. `false`: items are always dropped in front of the frame. |
| `default-target-item` | `CARROT_ON_A_STICK` | Frames holding it take whatever matches no other frame. `''` = off. |

How a destination is chosen:

1. Every target in range that accepts the item gets a priority: exact item, then tag, then material. A match
   through a shulker box or bundle in the frame ranks just below the same direct match, and the default target
   comes last.
2. Only the best priority is used, separately for frames with a container and frames without one.
3. The item goes into the containers in random order until it is all in.
4. Whatever is left is dropped at one random frame without a container.
5. If those frames are all on lava cauldrons, the rest is destroyed. If there is nowhere to go, the item stays.

**Tag filters.** `/framesort tag apply <tag>` (or the [Filter] button in `/framesort tag show <tag>`) turns the item
in your hand into a filter: the tag is stored inside the item and its name is set from `targets.filter-name`. In a frame, a filter accepts everything in its tag and nothing else,
not even its own material; inside a shulker box or bundle in the frame it adds its tag to what the frame accepts.
Renaming the item in an anvil never changes its tag, and a plain renamed item is never a filter.
`/framesort tag remove` turns it back into a normal item.

**Lava cauldrons are trash cans.** A target frame on a lava cauldron holds the item to destroy and is chosen like
any other frame (exact item, tag, material); with `registration: manual` it must be marked too. If a container
also takes that item, the lava only gets what doesn't fit; if the lava frame is the only match, everything that
arrives is destroyed. With the default target item (a carrot on a stick) in the frame, it destroys whatever has
no other target. An empty frame does nothing.

### inspect

| Key | Default | Meaning |
|---|---|---|
| `highlight-seconds` | `5` | How long targets glow for the player who inspected them. Nobody else sees the glow. |
| `colors.container`, `colors.dropped`, `colors.lava` | `#55FF55`, `#FFFF55`, `#FF5555` | Glow and trace colours (`#RRGGBB`) for targets that put items into a container, drop them, or destroy them in lava. |
| `page-size` | `8` | Entries per page in lists. |
| `trace-radius` | `32` | `/framesort trace` shows deliveries from sorters and pads within this radius. |
| `trace-default-seconds`, `trace-max-seconds` | `60`, `600` | Tracing duration. |

## pads.yml

| Key | Default | Meaning |
|---|---|---|
| `creation` | `anyone` | `anyone`: any player who places the top block on a complete column. `permission`: only players with `framesort.pad.create`. `item`: only by placing the pad's special item (from `/framesort give pad`) as the top block. |
| `sweep-interval` | `10` | Ticks between checks for items resting on pads. Items that arrive by water or fall onto a pad are picked up then. |
| `types.<id>.structure` | | Blocks from top to bottom. Items rest on the first one. |
| `types.<id>.item` | | `name`, `lore` and `glint` of the special top block (its material is the first block of the structure). |

A pad is created when its top block is placed on the rest of the column, so build it bottom up. It counts as long
as the column stays complete; breaking the top block removes it. In `item` mode, breaking it gives the special item
back. The top block of a registered pad can't be pushed by pistons or blown up.

Items resting up to one block above the top block count, so carpets or slabs on the pad are fine.

## Language

`language` in `config.yml` picks `lang/<language>.yml` (`en_US`, `es_ES`). A key missing from your file comes from
the bundled copy of that language, then from the bundled `en_US`; a key missing everywhere shows as the key and is
reported once in the console. Messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html); `<prefix>` is
the `prefix` key.

### Help

`command.help.header` opens the help (a list: one line per entry). Then comes one `command.help.<id>` line for
each command the player may run, where `<usage>` is the first path of that command in `commands.yml`. If you move a
command, the help follows it; any path you wrote by hand in the text does not.

### Pages

Lists (`tag`, `tags` and the inspection lists) are built from the `pager` keys:

| Key | Content |
|---|---|
| `pager.layout` | The whole page, one line per entry. Placeholders: `<entries>`, `<title>`, `<total>`, `<page>`, `<pages>`, `<previous>`, `<next>`. |
| `pager.empty` | Shown instead of the entries when the list is empty. |
| `pager.previous.enabled`, `pager.next.enabled` | The buttons; `<page>` is the page they open. Clicking is added by FrameSort, the hover is part of the text. |
| `pager.previous.disabled`, `pager.next.disabled` | Shown when there is no page in that direction. |

### Frame card

What a frame accepts is shown as one message built from `frame.layout`, framed like a page: `<title>` is
`frame.title` and `<lines>` are the `frame.status`, `frame.into`, `frame.accepts` and `frame.sources` lines that apply.
