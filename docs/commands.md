# Commands and permissions

## Commands

| Command | What it does |
|---|---|
| `/framesort` | Help: how to inspect, then one line for each command you may run. |
| `/framesort tag show <tag> [page]` | The items or blocks in a tag, 8 per page (`inspect.page-size`), in a framed list with « » buttons. Hover an entry to see its id. Vanilla tags can be written without `minecraft:`. |
| `/framesort tag list [page]` | Every item and block tag. Click a tag to show its contents. |
| `/framesort tag search <text> [page]` | Only the tags whose name contains `<text>`. |
| `/framesort tag apply <tag>` | Applies the tag to the item in your main hand (its name and glint come from `targets.tagged-item`). The [Apply] button in `/framesort tag show <tag>` runs it. In a frame a tagged item accepts only its tag. |
| `/framesort tag remove` | Takes the tag off the item in your main hand. |
| `/framesort where` | Where the item in your main hand would go from the nearest sorter or pad within `delivery.max-distance`, listed and highlighted like an inspection with that item in your off hand. Its components count (enchantments, name, contents). |
| `/framesort trace [seconds]` | For a while (`inspect.trace-default-seconds`, at most `inspect.trace-max-seconds`), deliveries from sorters and pads near you show in the action bar, with a streak of particles converging on their destination and a small splash when they arrive. A sorter turn draws one streak per target frame, and at most one delivery shows every 0.15 s, so a busy sorter never floods the screen. |
| `/framesort trace stop` | Stops tracing. |
| `/framesort inspect <page>` | Another page of the last inspection list (the page buttons run it). |
| `/framesort give sorter [player] [amount]` | The sorter activator from `config.yml`. Needed when `sorter.activator.require-marked` is true. |
| `/framesort give pad <type> [player] [amount]` | The special top block of a pad type. Needed when `pads.yml` has `creation: item`. |
| `/framesort reload` | Reloads `config.yml`, `pads.yml` and the language file. If a file is broken, the previous configuration stays active. |

## Inspecting

Sneak with an empty main hand; this needs `framesort.inspect`. Standing up, every click stays vanilla.

| Action | Result |
|---|---|
| Sneak + right-click a sorter or the top block of a pad | The items in the target frames it can send to glow for you only (green: into a container, yellow: dropped, red: lava; see `inspect.colors`) and those targets are listed, nearest first. Frames that WorldGuard regions (or another plugin cancelling `TargetBindEvent`) keep it from reaching are left out; a sorter's list also shows how many items it has sent. With an item in your off hand, only where that item would go, as numbered levels in order of preference: each level's containers first, then its drop spot or lava; the next level only gets what doesn't fit. |
| Sneak + left-click a frame with an item | Whether it is a target, where its items go, what it accepts, and how many sorters and pads reach it. |
| Sneak + right-click a frame with an item | Marks or unmarks it as a target (`targets.registration: manual`, needs `framesort.target.create`). The item does not rotate. |

The frame keeps its item and does not rotate. Empty frames are left alone, so they can still be filled or broken.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `framesort.*` | op | everything |
| `framesort.use` | everyone | `framesort.inspect`, `framesort.target.create` and the help, tag, where, trace and inspect commands (every `framesort.command.tag-*`) |
| `framesort.admin` | op | `framesort.pad.create` and the give and reload commands |
| `framesort.inspect` | everyone | inspecting sorters, pads and frames |
| `framesort.target.create` | everyone | marking frames as targets |
| `framesort.pad.create` | op | creating pads when `creation: permission` |

## commands.yml

Each section of `commands.yml` is one command feature: `help`, `tag-list`, `tag-search`, `tag-show`, `tag-apply`,
`tag-remove`, `where`, `trace`, `inspect`, `give` and `reload`. Each tag action is its own feature, so it has its
own paths, permission and switch. The key is fixed; what can change is:

| Key | Meaning |
|---|---|
| `enabled` | `false` removes the feature from every path. |
| `permission` | Needed to see and run it. Empty means everyone. A permission FrameSort doesn't declare is granted to ops only, unless a permissions plugin gives it. |
| `usage` | Every full path that runs it. The first word is the root command; the arguments follow the path. |

Paths that start with the same words share them, so a feature can move under another root or get a command of its
own. Page buttons repeat the path you typed. Links to another command (a tag's contents, the [Apply] button, the
pages of an inspection) use that command's first path and only show to players who may run it. Changes need a
server restart.

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
