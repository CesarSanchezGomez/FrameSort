# FrameSort

Item sorting without hopper chains. A **sorter** (a dispenser with an Eye of Ender in an item frame on it) and
**teleport pads** (a column of blocks) send items straight to the **target frames** that ask for them: into the
chest, barrel or hopper behind the frame, or dropped in front of it when there is no container.

- **Server:** Paper 26.2 or newer, Java 25.
- **Dependencies:** none.
- **Storage:** none to set up. Targets, pads and items keep their data in the world (persistent data containers).

## Installation

1. Put `FrameSort-<version>.jar` in the server's `plugins/` folder.
2. Start the server once. FrameSort writes `config.yml`, `pads.yml`, `commands.yml` and `lang/` to
   `plugins/FrameSort/`.
3. Edit them and run `/framesort reload` (`commands.yml` needs a restart).

`/version FrameSort` shows the installed version.

## How it works

1. **Targets.** Put an item in a frame on a container and sneak + right-click the frame with a stick to mark it
   as a target. A frame accepts items in this order of preference:
   1. the exact item;
   2. a tag, when the frame item is renamed to it (`logs`, `#minecraft:logs`);
   3. the same material;
   4. what a shulker box or bundle in the frame contains.

   A frame holding a carrot on a stick takes whatever matches nothing else. A frame on a lava cauldron destroys
   leftovers.
2. **Sorters.** Put an Eye of Ender in a frame on a dispenser. The frame disappears and the dispenser sends one
   random stack from its inventory every second, opening shulker boxes and bundles inside it.
3. **Teleport pads.** Place crying obsidian on gilded blackstone. Items resting on top are sent to targets. Who
   can create pads is configurable: anyone, players with a permission, or only with a special pad block.
4. **Inspect.** Right-click a sorter or pad with the stick: its targets' items glow (only for you, in configurable
   colours) and are listed page by page. Right-click a target frame to see what it accepts. `/framesort trace`
   shows every delivery around you live.

Containers are looked up the moment items go in, so a container that was moved or broken is skipped: items are
never duplicated.

## Commands

| Command | Permission | Default |
|---|---|---|
| `/framesort` (help) | `framesort.command.help` | everyone |
| `/framesort tag <tag> [page]` | `framesort.command.tag` | everyone |
| `/framesort tags [page]`, `/framesort tags search <text> [page]` | `framesort.command.tags` | everyone |
| `/framesort trace [seconds\|stop]` | `framesort.command.trace` | everyone |
| `/framesort give sorter\|pad <type> [player] [amount]` | `framesort.command.give` | op |
| `/framesort reload` | `framesort.command.reload` | op |

Every command also runs as `/fs`. Where each command lives, its permission and whether it is enabled are set in
`commands.yml`; see [docs/commands.md](docs/commands.md). The help lists only the commands the player may run.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `framesort.*` | op | everything |
| `framesort.use` | everyone | using the tool, marking targets and the help, tag, tags, trace and inspect commands |
| `framesort.admin` | op | creating pads in `permission` mode and the give and reload commands |
| `framesort.inspect` | everyone | using the tool (a stick by default) |
| `framesort.target.create` | everyone | marking frames as targets |
| `framesort.pad.create` | op | creating pads when `pads.yml` has `creation: permission` |
| `framesort.command.<command>` | as in the table above | each command |

## Configuration

`config.yml` (sorters, targets, delivery, the tool), `pads.yml` (pad types and who can create them) and
`lang/`. See [docs/configuration.md](docs/configuration.md).

## Build

```bash
./gradlew build
```

Needs nothing installed beyond a JDK: Gradle downloads Java 25 if it is missing. The jar is written to
`build/libs/`, and `./gradlew runServer` starts a Paper 26.2 test server with it. See
[docs/development.md](docs/development.md).
