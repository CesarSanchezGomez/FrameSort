# Configuration

`config.yml`, `pads.yml`, `tags.yml` and `commands.yml` have a `config-version`. When FrameSort's defaults change, the
console lists their missing and unknown keys; your files are never rewritten. Keys marked *optional* can be left out
without a warning: leaving one out means none of it, such as an activator with no lore. Language files fill missing keys
on their own (see [Language](#language)). Invalid values are reported with their path and replaced by the default.
`/framesort reload` applies `config.yml`, `pads.yml`, `tags.yml` and the language file and tells you how many values
fell back to their defaults. A file that is not valid YAML stops the reload and the previous configuration stays active.
`commands.yml` and `database.yml` need a restart.

Text values are written in single quotes; an apostrophe inside one is written twice (`'can''t'`). Text with
several lines (help, lore, the page layout) is a YAML list, one line per entry. Item lore starts and ends with an
empty line (`''`) and keeps each line short.

## Item look

The activator, the pad items and tagged items take these keys, all optional: left out, or set to `''`, `[]` or
`false`, the item has none of it.

| Key | Meaning |
|---|---|
| `name` | MiniMessage. Not italic unless you write `<i>`. On a tagged item it replaces the item's name. |
| `lore` | A list of MiniMessage lines, not italic unless you write `<i>`. On a tagged item they go below its own lines; on a CraftEngine item FrameSort hands out, they replace its lore. |
| `glint` | `true` adds the enchantment glint. `false` leaves the item's own glint alone: a nether star still shines. |
| `components` | Only a vanilla activator. Any vanilla item component by its vanilla name, written as YAML in the same shape as in `/give`. |

`name`, `lore` and `glint` win over the same thing in `components`. Examples:

```yaml
sorter:
  activator:
    material: PLAYER_HEAD
    components:
      item_model: 'mypack:sorter'   # a model from your resource pack
      profile:                      # a player head's texture
        properties:
          - {name: textures, value: 'eyJ0ZXh0dXJlcyI6...'}
      tooltip_display: {hidden_components: ['minecraft:enchantments']}
      custom_model_data: {floats: [1]}
```

More: `dyed_color: 0xFF0000` (leather armour), `potion_contents: {custom_color: 0x3366FF}` (a potion's colour),
`trim: {pattern: 'minecraft:host', material: 'minecraft:emerald'}` (an armour trim), and
`enchantment_glint_override: false` to take away an item's own glint.

- `components` is meant for the look, but any component works (food, a tool…): what it does to the activator is up to
  you. Whatever it looks like, any item of the activator's `material` still works as one unless `require-marked` is
  `true`.
- A component Minecraft rejects is reported on load and reload with its path and Minecraft's reason, and left out;
  the rest still apply. For example: `config.yml > sorter.activator.components.nope: Unknown item component
  'minecraft:nope', ignored`. A component that a later Minecraft version renames or removes is reported the same way.
- Not possible: removing a component the item type has (`!component`), typed arrays (`[I;...]`), keys with a dot, and
  a specific number type inside `custom_data` (numbers arrive as int, long or double).

**CraftEngine items and blocks.** With [CraftEngine](https://modrinth.com/plugin/craftengine) installed, wherever a
material goes you can write one of its items or blocks as `'craftengine:<namespace>:<id>'`, as in
[`tags.yml`](#tagsyml). CraftEngine builds the item; FrameSort lays `name`, `lore` and `glint` over it when it hands
it out: its name and its lore show instead of CraftEngine's, and the glint is added. Each one you leave out keeps
CraftEngine's, so with none of them the item looks as CraftEngine makes it. `components` don't apply: the model and
the rest are CraftEngine's (they are reported and ignored).

- With `require-marked: true` or `creation: item`, only the item from `/framesort give` works. FrameSort's mark
  can't be seen, so its look is what tells it apart from the same item from `/ce give`; the two don't stack.
- Better still, give the activator or the pad block an item of its own in CraftEngine, named and described for it,
  rather than reusing a general one.
- The ID is not checked on load (CraftEngine loads its items later); one it doesn't know never matches.
- Without CraftEngine installed, such a value is reported on load and treated like any wrong value: the default is
  used (`activator.material`, `default-target-item`), the pad type is skipped, or the tag member is ignored.

## config.yml

### sorter

| Key | Default | Meaning |
|---|---|---|
| `activator.material` | `HOPPER` | Item that turns a dispenser into a sorter when placed in a frame on it: a material, or a CraftEngine item (`'craftengine:<namespace>:<id>'`, see [CraftEngine items and blocks](#item-look)). A material that is not an item (`WATER`, `AIR`) falls back to the default. A CraftEngine item made from the activator's material is not the activator. |
| `activator.require-marked` | `false` | `true`: only the activator from `/framesort give sorter` works, not any item of that material. |
| `activator.name`, `lore`, `glint`, `components` | | *Optional.* Look of the activator FrameSort hands out (see [Item look](#item-look)); over a CraftEngine one, `name`, `lore` and `glint` only. Left out, or set to `''`, `[]` or `false`, the activator has none of it: with no name it shows the item's own name. |
| `frame-types` | `[ITEM_FRAME, GLOW_ITEM_FRAME]` | Frames that can hold the activator. |
| `custom-name` | `<gradient:#F7B733:#FC4A1A>Sorter</gradient>` | *Optional.* Title of a sorter's inventory. Left out or `''` = the dispenser's own name. The dispenser itself is never renamed: once it stops sorting it shows its own name again (from an anvil, or none), and a broken sorter drops it with that name. |
| `range` | `64` | A sorter's targets are searched within this many blocks of it (1 to 512). Each pad type has its own `range` in `pads.yml`. |
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
| `frame-types` | `[ITEM_FRAME, GLOW_ITEM_FRAME]` | Frames that can be targets. |
| `tagged-item.name`, `lore`, `glint` | `'<gradient:#F7B733:#FC4A1A>#<tag></gradient>'`, none, `true` | *Optional.* Look a tagged item gets (see [Item look](#item-look)); in `name` and `lore`, `<tag>` is the tag, without `minecraft:` for vanilla tags. Left out, or set to `''`, `[]` or `false`, the item has none of it: `tagged-item: {}` tags items without changing how they look. |

Only marked frames are targets: marked by hand, or by putting the item in with `/framesort automark` on (see
[docs/commands.md](commands.md)). A target frame may hang on any face of its block, whichever way the block faces:
the lid of an upright barrel, the side of a chest or the bottom of one in a ceiling.

### delivery

| Key | Default | Meaning |
|---|---|---|
| `insert-into-containers` | `true` | `true`: items go into the container behind the frame (CraftEngine's storage blocks and drawers too, see [CraftEngine containers](#delivery)), or are composted by a composter. `false`: frames on containers or composters drop the items in front of them instead; frames on lava cauldrons still destroy them. |
| `default-target-item` | `RECOVERY_COMPASS` | Frames holding it (a recovery compass inside the frame) take whatever matches no other frame, and whatever the other targets have no room for: a material (but not a CraftEngine item made from it) or a CraftEngine item (`'craftengine:<namespace>:<id>'`). `''` = off. A material that is not an item falls back to the default. |

How a destination is chosen:

1. Every target in range that accepts the item gets a priority: exact item, then tag, then material. A match
   through a shulker box or bundle in the frame ranks just below the same direct match, and the default target
   comes last. Targets with the same priority form a level. A filled shulker box or bundle in the frame stands only
   for what it holds; an empty one takes any shulker box or bundle of its kind, filled or not. With CraftEngine
   installed, a custom item's material is its ID: the material step takes the same custom item, never a vanilla item
   or another custom item of the same base material. No game tag takes a custom item, only a [`tags.yml`](#tagsyml)
   tag that lists it.
2. Levels are used in order, best first. In each level, the item goes into the containers nearest to the sorter or
   pad first (counting height too), until it is all in. Targets of players the owner trusts take their turn by
   distance like the owner's own.
3. Whatever is left is dropped at the nearest frame of that level without a container. If that level's frames
   without a container are all on lava cauldrons, the rest is destroyed. Either way the delivery ends there. If
   another plugin cancels the drop (an item limiter, for instance), items from a sorter stay in it, and items from a
   player's inventory stay in that inventory. A stacking plugin that merges items by cancelling their drop would then
   double them, so try a drop spot with yours.
4. Otherwise, what is left goes on to the next level. If no level has room, it stays.

**A sorter never sends to another sorter.** A target frame on a sorter only takes items from pads (its frame card says
so), so a pad can feed a sorter that opens the shulker boxes and bundles dropped on it, and no two sorters pass items
back and forth. Nor does a sorter or pad send into its own block, and none of them drops items where they would fall
onto a pad, which would pick them up and send them again; no pad pulls back what they drop either. Hoppers can
still lead items back into a sorter: build them so that items don't go round.

**Tagged items.** `/framesort tag apply <tag>` (or the [Apply] button in `/framesort tag show <tag>`) applies a tag
to the item in your hand: the tag is stored inside the item, which takes the look of `targets.tagged-item`.
In a frame, a tagged item accepts everything in its tag, never itself or its own material; a tagged shulker box or
bundle with contents also accepts what it holds. Inside a shulker box or bundle in the frame, a tagged item adds its
tag to what the frame accepts. Renaming the item in an anvil never changes its
tag, and a plain renamed item never has one. Applying a tag to a tagged item replaces the tag. `/framesort tag remove`
takes the tag off and gives the item back the name, lore and glint it had when it was tagged, so it stacks with
untagged ones again; a name given in an anvil after tagging is lost then.

**Composters compost.** A target frame on a composter takes only compostable items and composts them as a hopper
feeding it would: every item is used up, the level rises with that item's vanilla chance, and a full composter turns
ready a second later and takes nothing more until it is emptied. A hopper under it collects the bone meal, as in
vanilla. Items it doesn't take go on to the other targets as usual.

**CraftEngine containers.** With CraftEngine installed, a target frame on one of its storage blocks
(`simple_storage_block`, such as the safe of its default pack) or drawers (`drawer_block`) fills it as a hopper
feeding it would, whatever the block is called or looks like. A drawer only takes the item it already holds, or the
first one that comes while it is empty, as many as fit. A block whose `allow_input` is `false` takes nothing from
FrameSort: its frame drops the items in front of it, like a frame on a wall. A change to `allow_input` reaches a
block already placed once its chunk loads again. The frame card and inspection name the block as CraftEngine does,
or as the vanilla block it shows as while `/ce reload` runs or when no CraftEngine item places it. Storage furniture
is never filled: a frame can't hang on furniture, and CraftEngine keeps hoppers out of it.

**Lava cauldrons are trash cans.** A target frame on a lava cauldron holds the item to destroy and is chosen like
any other frame (exact item, tag, material). If a container with the same or a better priority also takes that
item, the lava only gets what doesn't fit; containers with a worse priority get nothing. With the default target
item (an item frame) in the frame, it destroys whatever has no other target or no room left in one. An empty
frame does nothing.

### ownership

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | `true`: a sorter or pad sends only to frames of its owner and of the players its owner trusts (`/framesort trust`), and only they inspect it or see its deliveries in `/framesort trace`. `false`: it sends to any frame and anyone inspects it, as without owners, and `/framesort trust`, `untrust` and `trusted` are hidden. A reload applies it, to the commands too. |

What a player sets up is theirs: a target frame belongs to whoever marked it, a sorter to whoever put the activator
in, a pad to whoever placed its top block. Owners are recorded even with `enabled: false`, so turning it on later
finds them. What has no owner (built before owners existed, or pasted without its data) only sends to frames that
have none either, and only admins inspect it; unmarking and marking a frame again, putting the activator back, or
breaking and placing a pad's top block again gives it an owner. More in
[docs/commands.md](commands.md#owners-and-trust).

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
| `sweep-interval` | `10` | Ticks between checks for items resting on pads, for loose items near them to pull, and for players crouching on pads that take inventories. Items that arrive by water or fall onto a pad are picked up then. |
| `types.<id>.structure` | | Exactly two blocks: the top, where items rest, then its base. The `<id>` can't contain `,`: each pad keeps it in its chunk between commas. Each one is a material or a CraftEngine block (`'craftengine:<namespace>:<id>'`). Players build pads by hand, so a material must be a block a player can place (not `STICK`, `WATER` or `AIR`); otherwise the type is skipped with a warning. |
| `types.<id>.range` | `64` | Targets of a pad of this type are searched within this many blocks of it (1 to 512). Left out, it is reported and `64` is used. |
| `types.<id>.pull-radius` | `4` | Blocks around a pad of this type whose loose items it pulls onto itself (0 to 16; `0` = off). See below. Left out, it is reported and `4` is used. |
| `types.<id>.takes-from-inventory` | `false` | `true`: a player crouching on a pad of this type sends a stack of their main inventory on every check. See below. Left out, it is reported and `false` is used. |
| `types.<id>.item` | | *Optional*, and so is each key in it: `name`, `lore` and `glint` of the special top block (see [Item look](#item-look)). Left out, or set to `''`, `[]` or `false`, the block has none of it. Its material is the top block of the structure and it looks like that block, so it takes no `components` (they are reported and ignored). With a CraftEngine top block the item is CraftEngine's, and `name`, `lore` and `glint` are laid over it (see [CraftEngine items and blocks](#item-look)). |

A key a type doesn't take, in it or in its `item`, is reported with its path as a typo and ignored.

A pad is created when its top block is placed on its base, so place the base first. It counts as long as both
blocks stay; breaking the top block removes it, and whoever broke it is told so. If the top block goes any other
way (WorldEdit, a command, gravity), the pad is forgotten at the next sweep. Without its base a pad waits, and works
again once the base is back. In `item` mode, breaking it gives the special item back. The top block of a registered
pad can't be pushed by pistons, blown up or changed where it stands: an axe can't scrape the wax off a copper top
block, nor a mob change it.

Items resting up to one block above the top block count, so carpets or slabs on the pad are fine.

**Pads pull loose items in.** On every check, a pad throws the items lying on the ground within its type's
`pull-radius` blocks of its top in an arc that ends, half a second later, one and a half blocks over its centre;
there the item is put right over the pad, stops and drops onto it, and a check sends it. It only pulls what it can
see on a straight line to that point: a wall in between stops it, but a carpet or slab resting on the pad doesn't. An
item lying within a block of the pad's side, whose arc would hit it, hops straight up instead, if nothing is over it,
and is put over the pad the same way; one under the pad stays. A pad only pulls while the block right over it is
empty or leaves room on top (a carpet, a bottom slab) and the one above that is free. It doesn't pull items already
on a pad, items so far above the pad that they couldn't take off, or what FrameSort itself dropped at a drop spot,
except once if that item merged with another one lying there. Items no target takes stay on top of the pad. With
WorldGuard, a pad only pulls items lying in exactly its own regions; without it, or another plugin cancelling
`PadPullEvent`, a pad pulls across claim borders, so set every type's `pull-radius` to `0` on servers that need it.

**Pads that take inventories.** On every check, a pad whose type has `takes-from-inventory: true` sends one stack
from the main inventory (the 27 slots above the hotbar) of every player crouching on its top, picked at random like a
sorter's: a stack no target takes stays in its slot and the next one is tried, so it never holds up the rest. With
the default `sweep-interval`, a full inventory empties in under 15 seconds. The hotbar, armour and off hand stay. A
player crouching beside the pad, or in spectator mode, is left alone. What it sends goes where the pad sends
anything, to its owner's targets and those of players the owner trusts, and shulker boxes and bundles go whole.

The bundled types are a waxed copper grate on a waxed block of copper (`copper`), a lodestone on smooth stone
(`lodestone`) and chiseled tuff bricks on polished tuff (`tuff`), which takes inventories and pulls nothing. Copper
ages into another block, so a pad of plain copper would end once it did: both copper blocks have to be waxed
(honeycomb) before they are placed, and `/framesort give pad copper` hands the grate out waxed.

A vanilla block in `structure` is only that block: a CraftEngine block never counts as it, whatever vanilla block it
shows as to the server (bricks by default), and one put in place of a pad's top block ends the pad at the next
sweep. A CraftEngine block in `structure` is only that CraftEngine block, whatever it looks like or does. Its
furniture can't be part of a pad: it is not a block. A pad with a CraftEngine block works like any other:
- it is created when that block is placed on the base;
- in `item` mode, the special item is CraftEngine's item for that block with FrameSort's mark, so it doesn't stack
  with the same item from `/ce give`, and breaking the pad gives it back;
- while `/ce reload` runs, the pad is kept and protected but sends nothing, because CraftEngine's blocks can't be
  told apart until it ends;
- in `item` mode, a pad whose item CraftEngine can't build (while `/ce reload` runs, or for an ID it no longer knows)
  can't be broken, so its item is never lost; the player is told why. A player in creative mode can still break it;
- an ID CraftEngine doesn't know never makes a pad.

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
  of the name while CraftEngine doesn't know the item (a typo, or during `/ce reload`).
- **Warnings.** Each of these is reported with its path (`tags.yml > tags.<name>`) and ignored: a malformed name or
  member, an unknown material or one that is not an item, a `#tag` the game doesn't have, an item plugin FrameSort
  doesn't read (only `craftengine:` today), a CraftEngine item while CraftEngine isn't installed, a CraftEngine item
  without its namespace (`craftengine:topaz_axe`; write `craftengine:default:topaz_axe`: two packs can use the same
  name, so `default` is never assumed), a tag named twice (`logs` and `minecraft:logs`: the first one counts) and a
  tag left without valid members. The rest of a CraftEngine ID is not checked, because CraftEngine may not have loaded
  its items yet.

## database.yml

Where FrameSort keeps who trusts whom. Everything else stays in the world. Read on start only.

| Key | Default | Meaning |
|---|---|---|
| `type` | `sqlite` | `sqlite`: a file in `plugins/FrameSort/`, nothing to set up. `mysql` or `mariadb`: the `remote` server, so several servers share the trust list. An unknown type stops FrameSort from starting. |
| `sqlite.file` | `data.db` | The SQLite file, inside `plugins/FrameSort/`. |
| `remote.host`, `port`, `database`, `username`, `password` | `localhost`, `3306`, `framesort`, `root`, `''` | The MySQL or MariaDB server. FrameSort's tables start with `framesort_`, so it can share a database with other plugins. |
| `remote.properties` | `useSSL=false` | Extra JDBC URL parameters. |

FrameSort reads the whole list when it starts; if the database can't be reached, FrameSort does not start. Each
`/framesort trust` or `untrust` is saved before it applies. With MySQL or MariaDB, each server reads the list again
every minute, so trust given on another server applies here within a minute; one given on this server applies at once.
MariaDB's driver is downloaded on the first start, like HikariCP; the SQLite and MySQL drivers come with the server.

## Language

`language` in `config.yml` picks `lang/<language>.yml` (`en_US`, `es_ES`), written the first time it is used. A
key missing from your file comes from the bundled copy of that language, then from the bundled `en_US`; a key missing
everywhere shows as the key and is reported once in the console. Messages use
[MiniMessage](https://docs.papermc.io/adventure/minimessage/format/); `<prefix>` is the `prefix` key.

### Help

The help is one message built from `command.help.layout` (a list: one line per entry). `<entries>` in it is one
`command.help.<id>` line for each command the player may run, where `<usage>` is the first path of that command in
`commands.yml`. If you move a
command, the help follows it, and so do `trace.started` (`<stop>`), `tag.unknown-hint` and `give.players-only`
(`<usage>`); any path you wrote by hand in the text does not.

### Pages

Lists (`tag list`, `tag search`, `tag show` and the inspection lists) are built from the `pager` keys:

| Key | Content |
|---|---|
| `pager.layout` | The whole page, one line per entry. Placeholders: `<entries>`, `<title>`, `<total>`, `<page>`, `<pages>`, `<previous>`, `<next>`. |
| `pager.empty` | Shown instead of the entries when the list is empty. |
| `pager.previous.enabled`, `pager.next.enabled` | The buttons; `<page>` is the page they open. Clicking is added by FrameSort, the hover is part of the text. |
| `pager.previous.disabled`, `pager.next.disabled` | Shown when there is no page in that direction. |

### Inspection title

`inspect.title` and `inspect.title-item` stay short; `<details>` in them is the hover with the `inspect.details`
lines that apply: where the source is (`place`), its owner (`owner`, or `no-owner`) and, for a sorter, how many items
it has sent (`sorted`). Longer messages work the same way throughout `lang/`: a few words in chat, the rest in a
hover.

### Frame card

What a frame accepts is shown as one message built from `frame.layout`, framed like a page: `<title>` is
`frame.title` and `<lines>` are the `frame.status`, `frame.owner`, `frame.into`, `frame.on-sorter`, `frame.accepts`
and `frame.sources` lines that apply. A target without an owner shows `frame.no-owner`. A tag line is
`frame.accepts.tag-link` when its click lists the tag, else `frame.accepts.tag`.
