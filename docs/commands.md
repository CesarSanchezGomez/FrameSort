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
| Right-click a sorter or the top block of a pad | Its targets glow for you only (green: into a container, yellow: dropped, red: lava; see `inspect.colors`) and are listed, nearest first. With an item in your off hand, only where that item would go: containers first, then drop spots. |
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

Each command's name, aliases, permissions and whether it is enabled can be changed in `commands.yml`. The top
level key is an internal id: don't change it. Changes need a server restart.

```yaml
framesort:
  enabled: true
  name: framesort
  aliases: [fs]
  permission: framesort.command.help
  subcommands:
    tag:     { permission: framesort.command.tag }
    give:    { permission: framesort.command.give }
    # ...
```
