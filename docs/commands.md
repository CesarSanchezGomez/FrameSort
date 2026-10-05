# Commands and permissions

## Commands

| Command | What it does |
|---|---|
| `/framesort` | Short help. |
| `/framesort tag <tag> [page]` | The items or blocks in a tag, 8 per page (`inspect.page-size`), in a framed list with « » buttons. Hover an entry to see its id. Vanilla tags can be written without `minecraft:`. |
| `/framesort tags [page]` | Every item and block tag. Click a tag to list its contents. |
| `/framesort tags search <text> [page]` | Only the tags whose name contains `<text>`. |
| `/framesort trace [seconds]` | For a while (default 60 s, at most `inspect.trace-max-seconds`), every delivery from sorters and pads near you shows in the action bar with a particle line to its destination. |
| `/framesort trace stop` | Stops tracing. |
| `/framesort inspect <page>` | Another page of the last list the tool showed you (the page buttons run it). |
| `/framesort give sorter [player] [amount]` | The sorter activator from `config.yml`. Needed when `sorter.activator.require-marked` is true. |
| `/framesort give pad <type> [player] [amount]` | The special top block of a pad type. Needed when `pads.yml` has `creation: item`. |
| `/framesort reload` | Reloads `config.yml`, `pads.yml` and the language file. If a file is broken, the previous configuration stays active. |
| `/framesort version` | The plugin version. |

## The tool

The tool is a stick by default (`inspect.tool`) and needs `framesort.inspect`.

| Action | Result |
|---|---|
| Right-click a sorter or the top block of a pad | The items in its target frames glow for you only (green: into a container, yellow: dropped, red: lava; see `inspect.colors`) and the targets are listed, nearest first. With an item in your off hand, only where that item would go: containers first, then drop spots. |
| Right-click a frame with an item | Whether it is a target, where its items go, what it accepts, and how many sorters and pads reach it. |
| Sneak + right-click a frame with an item | Marks or unmarks it as a target (`targets.registration: manual`, needs `framesort.target.create`). |

Empty frames are left alone, so the tool item can still be put into a frame.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `framesort.*` | op | everything |
| `framesort.use` | everyone | `framesort.inspect`, `framesort.target.create` and the help, tag, tags, trace and inspect commands |
| `framesort.admin` | op | `framesort.pad.create` and the give, reload and version commands |
| `framesort.inspect` | everyone | using the tool |
| `framesort.target.create` | everyone | marking frames as targets |
| `framesort.pad.create` | op | creating pads when `creation: permission` |

## commands.yml

Each section of `commands.yml` is one command feature: `help`, `tag`, `tags`, `trace`, `inspect`, `give`,
`reload` and `version`. The key is fixed; what can change is:

| Key | Meaning |
|---|---|
| `enabled` | `false` removes the feature from every path. |
| `permission` | Needed to see and run it. Empty means everyone. A permission FrameSort doesn't declare is granted to ops only, unless a permissions plugin gives it. |
| `usage` | Every full path that runs it. The first word is the root command; the arguments follow the path. |

Paths that start with the same words share them, so a feature can move under another root or get a command of its
own. Clickable links in chat (pages, tags) use the first path of their feature. Changes need a server restart.

```yaml
trace:
  enabled: true
  permission: 'framesort.command.trace'
  usage:
    - '/framesort trace'
    - '/fs trace'
    - '/fstrace'      # also /fstrace [seconds|stop]
```
