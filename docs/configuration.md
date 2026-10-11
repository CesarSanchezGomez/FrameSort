# Configuration

`config.yml`, `pads.yml`, `tags.yml` and `commands.yml` have a `config-version`. When FrameSort's defaults change,
the console lists their missing and unknown keys; your files are never rewritten. Language files fill missing keys on
their own (see [Language](#language)). Invalid values are reported with their path and replaced by the default.
`/framesort reload` applies `config.yml`, `pads.yml`, `tags.yml` and the language file and tells you how many values
fell back to their defaults. A file that is not valid YAML stops the reload and the previous configuration stays active.
`commands.yml` needs a restart.

Text values are written in single quotes; an apostrophe inside one is written twice (`'can''t'`). Text with
several lines (help, lore, the page layout) is a YAML list, one line per entry. Item lore starts and ends with an
empty line (`''`) and keeps each line short.

## config.yml

### sorter

| Key | Default | Meaning |
|---|---|---|
| `activator.material` | `ENDER_EYE` | Item that turns a dispenser into a sorter when placed in a frame on it. A material that is not an item (`WATER`, `AIR`) falls back to the default. |
| `activator.require-marked` | `false` | `true`: only the activator from `/framesort give sorter` works, not any item of that material. |
| `activator.name`, `lore`, `glint`, `item-model` | | Look of the activator FrameSort hands out (MiniMessage). |
| `frame-types` | `[ITEM_FRAME, GLOW_ITEM_FRAME]` | Frames that can hold the activator. |
| `custom-name` | `<gradient:#F7B733:#FC4A1A>Item Sorter</gradient>` | Name shown in the sorter's inventory. `''` = none. A dispenser renamed in an anvil keeps its own name. |
| `tick-rate` | `20` | Ticks between two turns of the same sorter. |
| `disable-when-powered` | `false` | A powered sorter stops sending. |
| `hide-frame` | `true` | Hides the activator's frame while the sorter works. A reload applies a change to every sorter. |
| `show-activity` | `true` | Rotates the activator after every turn that sends something. |

Whoever creates or removes a sorter (placing or taking the activator, or breaking its frame or dispenser) is told
so. A sorter never dispenses like a plain dispenser. Each turn it sends up to 64 items from random stacks (only part of
the last one when it would go past 64), taking them from inside shulker boxes and bundles, so packed items go as fast
as loose ones. Items with nowhere to go stay
in the sorter without holding up the rest, and are not tried again in the same turn; a turn gives up after 64
tries. Once nothing inside a shulker box or bundle can go anywhere, the box or bundle itself is sent whole to the
targets that accept it.

### targets

| Key | Default | Meaning |
|---|---|---|
| `registration` | `manual` | `manual`: only frames marked by sneak + right-clicking them with an empty hand. `automatic`: every frame of `frame-types` on a container or composter; frames anywhere else (walls, lava cauldrons) are never targets, so marking is the only way to make a drop spot or a trash can. |
| `positions` | `[FRONT]` | Where a target frame may hang on its block: `TOP`, `BOTTOM`, `FRONT`, `BACK`, `LEFT`, `RIGHT` (`SIDES` = `LEFT` + `RIGHT`). `FRONT`, `BACK`, `LEFT` and `RIGHT` follow the way the block faces (chests, barrels, furnaces…); left and right are as seen standing in front of it. On blocks without a horizontal facing (hoppers, upright barrels, composters) a side face counts as any of the four. |
| `frame-types` | `[ITEM_FRAME, GLOW_ITEM_FRAME]` | Frames that can be targets. |
| `tagged-item.name` | `'<gradient:#F7B733:#FC4A1A>#<tag></gradient>'` | Name a tagged item gets (MiniMessage); `<tag>` is the tag, without `minecraft:` for vanilla tags. |
| `tagged-item.glint` | `true` | Tagged items get the enchantment glint, so they stand out in inventories and frames. |

Only the front counts by default, so decorative frames on the top or sides of a chest are never used. Add positions
only where frames are meant to be targets, especially with `automatic`.

### delivery

| Key | Default | Meaning |
|---|---|---|
| `max-distance` | `64` | Targets are searched within this many blocks of the sorter or pad. |
| `insert-into-containers` | `true` | `true`: items go into the container behind the frame, or are composted by a composter. `false`: items are always dropped in front of the frame. |
| `default-target-item` | `CARROT_ON_A_STICK` | Frames holding it (but not a CraftEngine custom item made from it) take whatever matches no other frame, and whatever the other targets have no room for. `''` = off. A material that is not an item falls back to the default. |

How a destination is chosen:

1. Every target in range that accepts the item gets a priority: exact item, then tag, then material. A match
   through a shulker box or bundle in the frame ranks just below the same direct match, and the default target
   comes last. Targets with the same priority form a level. A filled shulker box or bundle in the frame stands only
   for what it holds; an empty one takes any shulker box or bundle of its kind, filled or not. With CraftEngine
   installed, a custom item's material is its ID: the material step takes the same custom item, never a vanilla item
   or another custom item of the same base material. No game tag takes a custom item, only a [`tags.yml`](#tagsyml)
   tag that lists it.
2. Levels are used in order, best first. In each level, the item goes into the containers in random order until it
   is all in.
3. Whatever is left is dropped at one random frame of that level without a container. If that level's frames
   without a container are all on lava cauldrons, the rest is destroyed. Either way the delivery ends there.
4. Otherwise, what is left goes on to the next level. If no level has room, it stays.

**Tagged items.** `/framesort tag apply <tag>` (or the [Apply] button in `/framesort tag show <tag>`) applies a tag
to the item in your hand: the tag is stored inside the item, and its name and glint come from `targets.tagged-item`.
In a frame, a tagged item accepts everything in its tag, never itself or its own material; a tagged shulker box or
bundle with contents also accepts what it holds. Inside a shulker box or bundle in the frame, a tagged item adds its
tag to what the frame accepts. Renaming the item in an anvil never changes its
tag, and a plain renamed item never has one. `/framesort tag remove` takes the tag off.

**Composters compost.** A target frame on a composter takes only compostable items and composts them as a hopper
feeding it would: every item is used up, the level rises with that item's vanilla chance, and a full composter turns
ready a second later and takes nothing more until it is emptied. A hopper under it collects the bone meal, as in
vanilla. Items it doesn't take go on to the other targets as usual.

**Lava cauldrons are trash cans.** A target frame on a lava cauldron holds the item to destroy and is chosen like
any other frame (exact item, tag, material); it must be marked, because `registration: automatic` only uses frames
on containers. If a container with the same or a better priority also takes that item, the lava only gets what
doesn't fit; containers with a worse priority get nothing. With the default target item (a carrot on a stick) in the
frame, it destroys whatever has no other target or no room left in one. An empty frame does nothing.

### inspect

| Key | Default | Meaning |
|---|---|---|
| `highlight-seconds` | `5` | How long targets glow for the player who inspected them. Nobody else sees the glow. |
| `colors.container`, `colors.dropped`, `colors.lava` | `#55FF55`, `#FFFF55`, `#FF5555` | Glow and trace colours (`#RRGGBB`) for targets that put items into a container, drop them, or destroy them in lava. |
| `page-size` | `8` | Entries per page in lists. |
| `trace-radius` | `32` | `/framesort trace` shows deliveries from sorters and pads within this radius. |
| `trace-default-seconds`, `trace-max-seconds` | `60`, `600` | Tracing duration. A default above the maximum is lowered to it, with a warning. |

## pads.yml

| Key | Default | Meaning |
|---|---|---|
| `creation` | `anyone` | `anyone`: any player who places the top block on its base. `permission`: only players with `framesort.pad.create`. `item`: only by placing the pad's special item (from `/framesort give pad`) as the top block. |
| `sweep-interval` | `10` | Ticks between checks for items resting on pads. Items that arrive by water or fall onto a pad are picked up then. |
| `types.<id>.structure` | | Exactly two blocks: the top, where items rest, then its base. Players build pads by hand, so both must be blocks a player can place (not `STICK`, `WATER` or `AIR`); otherwise the type is skipped with a warning. |
| `types.<id>.item` | | `name`, `lore` and `glint` of the special top block (its material is the top block of the structure). |

A pad is created when its top block is placed on its base, so place the base first. It counts as long as both
blocks stay; breaking the top block removes it. If the top block goes any other way (WorldEdit, a command, gravity),
the pad is forgotten at the next sweep. Without its base a pad waits, and works again once the base is back. In
`item` mode, breaking it gives the special item back. The top block of a registered pad can't be pushed by pistons
or blown up.

Items resting up to one block above the top block count, so carpets or slabs on the pad are fine.

## tags.yml

Tags of your own, and replacements for the game's tags. They only change what FrameSort's frames accept, never the
game's tags, recipes or anything else. Each tag under `tags` is a list of members:

```yaml
tags:
  axes:
    - '#minecraft:axes'
    - 'craftengine:default:topaz_axe'
  'server:gems':
    - 'diamond'
    - 'emerald'
```

| Member | Meaning |
|---|---|
| `'diamond_axe'`, `'minecraft:diamond_axe'` | A vanilla item. |
| `'#minecraft:axes'` | Every item in that game tag, as the game has it, so it keeps up when Minecraft adds items. It never reads a tag from `tags.yml`, so tags can't loop. |
| `'craftengine:default:topaz_axe'` | A CraftEngine item: `craftengine:` and the item's ID, namespace included. Sneak + left-click a frame holding the item to see what to write. |

- **Names.** A name without a namespace is a game tag (`axes` is `minecraft:axes`), so it replaces that tag entirely
  inside FrameSort; add `'#minecraft:axes'` to extend it instead of copying it. Give new tags a namespace of your own
  (`'server:gems'`). Removing a replacement and reloading brings the game's tag back.
- **Matching.** A vanilla item goes to a tag by its material; a custom item only by its reference, never by the
  material it is made of.
- **Commands.** `tag list`, `tag search`, `tag show` and `tag apply` include these tags. `tag show` marks them as
  "tags.yml tag" and lists their custom items after the materials, by the name CraftEngine gives them: CraftEngine
  translates it for each player, as in their inventory (a name written with `<l10n:…>` needs CraftEngine's
  `network.intercept-packets.system-chat`, on by default). Hovering shows the reference, which is also shown instead
  of the name while CraftEngine doesn't know the item (not installed, a typo, or during `/ce reload`).
- **Warnings.** Each of these is reported with its path (`tags.yml > tags.<name>`) and ignored: a malformed name or
  member, an unknown material or one that is not an item, a `#tag` the game doesn't have, an item plugin FrameSort
  doesn't read (only `craftengine:` today), a CraftEngine item without its namespace (`craftengine:topaz_axe`; write
  `craftengine:default:topaz_axe`: two packs can use the same name, so `default` is never assumed), a tag named twice
  (`logs` and `minecraft:logs`: the first one counts) and a tag left without valid members. The rest of a CraftEngine
  ID is not checked, because CraftEngine may not have loaded its items yet.

## Language

`language` in `config.yml` picks `lang/<language>.yml` (`en_US`, `es_ES`). A key missing from your file comes from
the bundled copy of that language, then from the bundled `en_US`; a key missing everywhere shows as the key and is
reported once in the console. Messages use [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/);
`<prefix>` is the `prefix` key.

### Help

`command.help.header` opens the help (a list: one line per entry). Then comes one `command.help.<id>` line for
each command the player may run, where `<usage>` is the first path of that command in `commands.yml`. If you move a
command, the help follows it; any path you wrote by hand in the text does not.

### Pages

Lists (`tag list`, `tag search`, `tag show` and the inspection lists) are built from the `pager` keys:

| Key | Content |
|---|---|
| `pager.layout` | The whole page, one line per entry. Placeholders: `<entries>`, `<title>`, `<total>`, `<page>`, `<pages>`, `<previous>`, `<next>`. |
| `pager.empty` | Shown instead of the entries when the list is empty. |
| `pager.previous.enabled`, `pager.next.enabled` | The buttons; `<page>` is the page they open. Clicking is added by FrameSort, the hover is part of the text. |
| `pager.previous.disabled`, `pager.next.disabled` | Shown when there is no page in that direction. |

### Frame card

What a frame accepts is shown as one message built from `frame.layout`, framed like a page: `<title>` is
`frame.title` and `<lines>` are the `frame.status`, `frame.into`, `frame.accepts` and `frame.sources` lines that apply.
