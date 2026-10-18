# Commands and permissions

## Commands

| Command | What it does |
|---|---|
| `/framesort` | Help: how to inspect, then one line for each command you may run. |
| `/framesort tag show <tag> [page]` | The items or blocks in a tag, 8 per page (`inspect.page-size`), in a framed list with « » buttons. Hover an entry to see its id. A tag from `tags.yml` lists its custom items after them, by their CraftEngine name, translated as in your inventory; hovering one shows its reference. Vanilla tags can be written without `minecraft:`. |
| `/framesort tag list [page]` | Every item and block tag, and the tags in `tags.yml`. Click a tag to show its contents. |
| `/framesort tag search <text> [page]` | Only the tags whose name contains `<text>`. |
| `/framesort tag apply <tag>` | Applies the tag to the item in your main hand, which takes the look of `targets.tagged-item` (name, lore and glint). A tagged item gets the new tag instead. The [Apply] button in `/framesort tag show <tag>` runs it. In a frame a tagged item accepts only its tag. |
| `/framesort tag remove` | Takes the tag off the item in your main hand and gives it back the name, lore and glint it had before. |
| `/framesort where` | Where the item in your main hand would go from the nearest sorter or pad you may inspect within `delivery.max-distance`, listed and highlighted like an inspection with that item in your off hand. Its components count (enchantments, name, contents). |
| `/framesort trace [seconds]` | For a while (`inspect.trace-default-seconds`, at most `inspect.trace-max-seconds`), deliveries from the sorters and pads near you that you may inspect show in the action bar, with a streak of particles converging on their destination and a small splash when they arrive. A sorter turn draws one streak per target frame, and at most one delivery shows every 0.15 s, so a busy sorter never floods the screen. |
| `/framesort trace stop` | Stops tracing. |
| `/framesort trust <player>` | Your sorters and pads also send to that player's frames (see [Owners and trust](#owners-and-trust)). Only a player who has played on this server. |
| `/framesort untrust <player>` | Stops trusting that player. Suggests the players you trust. |
| `/framesort trusted [page]` | Whom you trust, since when, and who trusts you. |
| `/framesort give sorter [player] [amount]` | The sorter activator from `config.yml`. Needed when `sorter.activator.require-marked` is true. A CraftEngine activator is built by CraftEngine; while it can't (an ID it doesn't know, or during `/ce reload`) nothing is given and you are told so. |
| `/framesort give pad <type> [player] [amount]` | The special top block of a pad type. Needed when `pads.yml` has `creation: item`. A CraftEngine top block's item is built by CraftEngine; while it can't, nothing is given and you are told so. |
| `/framesort reload` | Reloads `config.yml`, `pads.yml`, `tags.yml` and the language file. If a file is broken, the previous configuration stays active. |

## Inspecting

Sneak with an empty main hand; this needs `framesort.inspect`. Standing up, every click stays vanilla.

| Action | Result |
|---|---|
| Sneak + right-click a sorter or the top block of a pad | Only yours, those of a player who trusts you, or any with `framesort.inspect.others` (see [Owners and trust](#owners-and-trust)). The items in the target frames it can send to glow for you only (green: into a container, yellow: dropped, red: lava; see `inspect.colors`) and those targets are listed, nearest first. Frames that WorldGuard regions (or another plugin cancelling `TargetBindEvent`) keep it from reaching are left out; a sorter's list also shows how many items it has sent. With an item in your off hand, only where that item would go, as numbered levels in order of preference: each level's containers nearest first, then the one drop spot or lava that takes the rest (the nearest, a drop spot before lava); the next level only gets what doesn't fit. From a sorter, a shulker box or bundle with contents comes with a note: the sorter opens it first, and the list shows where it goes whole once nothing inside can go. |
| Sneak + left-click a frame with an item | Whether it is a target and, if so, who owns it; where its items go, what it accepts, and how many sorters and pads reach it. |
| Sneak + right- or left-click a sorter's frame | Inspects the sorter, as clicking its dispenser does. That frame is never a target. |
| Sneak + right-click a frame with an item | Marks or unmarks it as a target (`targets.registration: manual`, needs `framesort.inspect` and `framesort.target.create`). Marking makes it yours, and unmarking frees it; with `ownership.enabled`, only you unmark it then. A mark lives with the item: once another item is put in, the frame is no longer a target, and whoever put it in is told so in the action bar. The item does not rotate. |

The frame keeps its item and does not rotate. Empty frames are left alone, so they can still be filled or broken.

## Owners and trust

With `ownership.enabled` (the default), what a player sets up is theirs: a target frame belongs to whoever marked
it (with `targets.registration: automatic`, nothing is marked, so to whoever put its item in), a sorter to whoever
put the activator in, and a pad to whoever placed its top block. A sorter or pad sends only to frames of its owner, so
nobody can mark a frame near your sorter and take your items.

`/framesort trust Beto`, typed by Ana, means: Beto's frames count as Ana's for Ana's sorters and pads.
- **It shares Ana's network.** A sorter can't tell whose item is whose, so any item in Ana's sorter, hers too, can go
  to a chest of Beto's when his frame asks for it first, or as much and nearer to the sorter.
- **One way.** Beto's sorters still don't send to Ana's frames until Beto trusts Ana: a team trusts both ways.
- **Not passed on.** If Ana trusts Beto and Beto trusts Carla, Ana's sorters don't send to Carla.
- **Beto can see it:** he may inspect Ana's sorters and pads and sees their deliveries in `/framesort trace`.
- **It works while Ana is offline or banned,** and `/framesort untrust Beto` stops it from the next delivery. What
  was delivered stays where it is, and Beto's frames stay his.

Only a target's owner unmarks it: unmarking someone else's frame would send their items to the next level, maybe
into lava. Unmarking frees the frame, so anyone may mark it next and own it, and so does taking its item out: the
next item put in leaves the frame unmarked and without an owner. Taking the item out means hitting the frame, which
the server's protection decides, like breaking it. Breaking blocks, opening the dispenser or
dropping items on a pad is left to the server's protection (claims, regions, locks): FrameSort only decides where
items go from afar, and who sees it. Items dropped on someone's pad go to that pad's owner. A frame on someone
else's chest belongs to whoever marked it, so their sorter can fill that chest unless the chest's protection stops
them from touching the frame.

With `ownership.enabled: false`, `/framesort trust`, `untrust` and `trusted` leave the help, tab completion and
links, and can't be run; `/framesort reload` with it back on brings them back. Names come from the server's profile
cache: a player it no longer remembers shows as their UUID, which `untrust` also takes.

What has no owner (built before owners existed, or pasted without its data) works only with what has no owner, and
only admins inspect it. Unmarking and marking a frame again (with `targets.registration: automatic`, taking its item
out and putting it back), putting the activator back, or breaking and placing a pad's top block again gives it an
owner.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `framesort.*` | op | everything |
| `framesort.use` | everyone | `framesort.inspect`, `framesort.target.create` and the help, tag, where, trace, trust, untrust and trusted commands (every `framesort.command.tag-*`) |
| `framesort.admin` | op | `framesort.pad.create`, `framesort.inspect.others` and the give and reload commands |
| `framesort.inspect` | everyone | inspecting sorters, pads and frames |
| `framesort.inspect.others` | op | inspecting, `/framesort where` and `/framesort trace` on sorters and pads whose owner doesn't trust you |
| `framesort.target.create` | everyone | marking frames as targets |
| `framesort.pad.create` | op | creating pads when `creation: permission` |

## commands.yml

Each section of `commands.yml` is one command feature: `help`, `tag-list`, `tag-search`, `tag-show`, `tag-apply`,
`tag-remove`, `where`, `trace`, `trust`, `untrust`, `trust-list`, `give` and `reload`. Each tag action is
its own feature, so it has its own paths, permission and switch. The key is fixed; what can change is:

| Key | Meaning |
|---|---|
| `enabled` | `false` removes the feature from every path. |
| `permission` | Needed to see and run it. Empty means everyone. A permission FrameSort doesn't declare is granted to ops only, unless a permissions plugin gives it. |
| `usage` | Every full path that runs it. The first word is the root command; the arguments follow the path. |

Paths that start with the same words share them, so a feature can move under another root or get a command of its
own. Page buttons repeat the path you typed. Links to another command (a tag's contents, the [Apply] button) use
that command's first path and only show to players who may run it. An inspection's page buttons run no command: each
click lists the targets again, if you may still inspect that sorter or pad. Changes need a server restart.

```yaml
trace:
  enabled: true
  permission: 'framesort.command.trace'
  usage:
    - '/framesort trace'
    - '/fs trace'
    - '/fstrace'      # also /fstrace [seconds|stop]
```

Moving the tag list to `/fs tags`, while only staff may apply tags:

```yaml
tag-list:
  enabled: true
  permission: 'framesort.command.tag-list'
  usage:
    - '/framesort tags'
    - '/fs tags'

tag-apply:
  enabled: true
  permission: 'myserver.staff'   # not declared by FrameSort, so ops only unless a permissions plugin grants it
  usage:
    - '/framesort tag apply'
    - '/fs tag apply'
```
