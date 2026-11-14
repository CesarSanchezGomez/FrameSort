# Commands and permissions

## Commands

| Command | What it does |
|---|---|
| `/framesort [page]` | Help: how to inspect, then one line for each command you may run, 8 per page (`inspect.page-size`), with « » buttons. |
| `/framesort automark` | Switches between marking frames by hand and automark. With automark on, an item you put in a frame on a container (see [`insert-into-containers`](configuration.md#delivery)) or composter marks the frame as your target as sneak + right-click does, if a sorter or pad you may inspect has it in range (see [Where frames answer](#where-frames-answer)); elsewhere it marks nothing, without a message. Its item glows and the action bar says so; frames on any other block (walls, lava cauldrons, furnaces…) are still marked by hand, and putting the sorter activator in never marks its frame. Your choice stays with you on this server, also after leaving, and only acts while you may run this command and mark targets (`framesort.target.create`); without the latter the command says you can't mark targets. Off by default. |
| `/framesort tag show <tag> [page]` | The items or blocks in a tag, 8 per page (`inspect.page-size`), in a framed list with « » buttons. Hover an entry to see its id. A tag from `tags.yml` lists its custom items after them, by their CraftEngine name, translated as in your inventory; hovering one shows its reference. Vanilla tags can be written without `minecraft:`. |
| `/framesort tag list [page]` | Every item and block tag, and the tags in `tags.yml`. Click a tag to show its contents. |
| `/framesort tag search <text> [page]` | Only the tags whose name contains `<text>`. |
| `/framesort tag apply <tag>` | Applies the tag to the item in your main hand, which takes the look of `targets.tagged-item` (name, lore and glint). A tagged item gets the new tag instead. The [Apply] button in `/framesort tag show <tag>` runs it. In a frame a tagged item accepts only its tag. |
| `/framesort tag remove` | Takes the tag off the item in your main hand and gives it back the name, lore and glint it had before. |
| `/framesort where` | Where the item in your main hand would go from the nearest sorter or pad you may inspect whose `range` reaches you, listed and highlighted like an inspection with that item in your off hand. Its components count (enchantments, name, contents). |
| `/framesort trace [seconds]` | For a while (`inspect.trace-default-seconds`, at most `inspect.trace-max-seconds`), deliveries from the sorters and pads near you that you may inspect show in the action bar, with a streak of particles from the sorter's dispenser or the pad's top block, converging on their destination, and a small splash when they arrive. A sorter turn or pad sweep draws one streak per target frame, and at most one delivery shows every 0.15 s, so a busy sorter never floods the screen. |
| `/framesort trace stop` | Stops tracing. |
| `/framesort trust <player>` | Your sorters and pads also send to that player's frames (see [Owners and trust](#owners-and-trust)). Only a player who has played on this server. |
| `/framesort untrust <player>` | Stops trusting that player. Suggests the players you trust. |
| `/framesort trusted [page]` | Whom you trust, since when, and who trusts you. |
| `/framesort give sorter [player] [amount]` | The sorter activator from `config.yml`. Needed when `sorter.activator.require-marked` is true. A CraftEngine activator is built by CraftEngine; while it can't (an ID it doesn't know, or during `/ce reload`) nothing is given and you are told so. |
| `/framesort give pad <type> [player] [amount]` | The special top block of a pad type. Needed when `pads.yml` has `creation: item`. A CraftEngine top block's item is built by CraftEngine; while it can't, nothing is given and you are told so. |
| `/framesort reload` | Reloads `config.yml`, `pads.yml`, `tags.yml` and the language file, and says how many milliseconds it took until the answer, and how many warnings there were (see the console). If a file is broken, the previous configuration stays active. Asked while another reload runs, it says so and does nothing. |

## Inspecting

Sneak with an empty main hand; this needs `framesort.inspect`. Standing up, every click stays vanilla, except that a
hit doesn't take the item out of a target or a sorter's frame you may not change (see
[Owners and trust](#owners-and-trust)); every click in a world listed in
[`disabled-worlds`](configuration.md#disabled-worlds) stays vanilla. A target frame and a
sorter's frame always answer; any other frame answers only within range of a sorter or pad you may inspect (see
[Where frames answer](#where-frames-answer)), and elsewhere clicks on it stay vanilla too, without a message. A fixed
frame (one fixed in place by a command, an adventure map or another plugin) answers no sneak-click, as the server
lets nobody turn or hit it; to unmark a fixed target, unfix it first (`Fixed:0b` with `/data merge entity`).

| Action | Result |
|---|---|
| Sneak + right-click a sorter or the top block of a pad | Only yours, those of a player who trusts you, or any with `framesort.inspect.others` (see [Owners and trust](#owners-and-trust)); for anyone else, the action bar says whose it is, or that it has no owner. The items in the target frames it can send to glow for you only (green: into a container, yellow: dropped, red: lava; see `inspect.colors`) and those targets are listed, nearest first; hovering one shows where it is, how far and, for a container or composter, how full. Two targets with the same item and block read alike and stay as two lines, each with its own hover. Frames that WorldGuard regions and the `framesort-deliver` flag (see [WorldGuard regions](configuration.md#worldguard-regions)), or another plugin cancelling `TargetBindEvent`, keep it from reaching are left out; a sorter's list also shows how many items it has sent. With an item in your off hand, only where that item would go, as numbered levels in order of preference: each level's containers nearest first, then the one drop spot or lava that takes the rest (the nearest, a drop spot before lava); the next level only gets what doesn't fit. From a sorter, a shulker box or bundle with contents comes with a note: the sorter opens it first, and the list shows where it goes whole once nothing inside can go. |
| Sneak + left-click a frame with an item | Whether it is a target. If it is, and it is yours, of a player who trusts you, or you have `framesort.inspect.others`: who owns it, where its items go, what it accepts and how many of the sorters and pads you may inspect reach it; otherwise the action bar says whose it is, or that it has no owner, as for a sorter you can't inspect. If not: how to mark it, or that its kind of frame can't be one. |
| Sneak + right- or left-click a sorter's frame | Inspects the sorter, as clicking its dispenser does. That frame is never a target. |
| Sneak + right-click a frame with an item | Marks or unmarks it as a target (needs `framesort.inspect` and `framesort.target.create`). Its item glows green or red for you, and the action bar says whether it was marked or unmarked, or why it can't be. Marking makes it yours, and unmarking frees it; with `ownership.enabled`, only you, the players you trust and admins unmark it then (see [Owners and trust](#owners-and-trust)). A mark lives with the item: when one of them takes it out by hand, the mark ends and their action bar says so, and with a projectile of theirs (an arrow, a snowball) it ends without a message; anyone else's hit or projectile leaves the item in, and by hand their action bar says whose it is. An item that leaves any other way (an explosion, a mob, a dispenser's arrow, another plugin) ends the mark without a message, at once or when the next item is put in (with `/framesort automark` on, the new item marks a frame on a container or composter for whoever put it in); its card (sneak + left-click) says whether it is one. The item does not rotate. |

Where FrameSort answers, the frame keeps its item and does not rotate. Empty frames are left alone, so they can still
be filled or broken.

## Owners and trust

With `ownership.enabled` (the default), what a player sets up is theirs: a target frame belongs to whoever marked
it, a sorter to whoever put the activator in, and a pad to whoever placed its top block. A sorter or pad sends only
to frames of its owner, so nobody can mark a frame near your sorter and take your items.

`/framesort trust Beto`, typed by Ana, means: Beto's frames count as Ana's for Ana's sorters and pads.
- **It shares Ana's network.** A sorter can't tell whose item is whose, so any item in Ana's sorter, hers too, can go
  to a chest of Beto's when his frame asks for it first, or as much and nearer to the sorter.
- **One way.** Beto's sorters still don't send to Ana's frames until Beto trusts Ana: a team trusts both ways.
- **Not passed on.** If Ana trusts Beto and Beto trusts Carla, Ana's sorters don't send to Carla.
- **Beto can see it:** he may inspect Ana's sorters and pads and the cards of her target frames, and sees their
  deliveries in `/framesort trace`.
- **Beto can change it:** he may unmark Ana's frames and take the item out of them or the activator out of her
  sorters, as she can. A frame he marks again, or a sorter he puts his activator in, is his.
- **It works while Ana is offline or banned,** and `/framesort untrust Beto` stops it from the next delivery. What
  was delivered stays where it is, and Beto's frames stay his.

Only a target's owner, the players they trust and admins (`framesort.unmark.others`) unmark it: anyone else
unmarking it would send its owner's items to the next level, maybe into lava. The same goes for taking its item out,
by hand or with a projectile, and for taking a sorter's activator out, which would let someone else put theirs in and
own the sorter with what is in it: anyone else's hit or projectile leaves the item in, and by hand their action bar
says whose it is. Unmarking frees the frame, so anyone may mark it next (within range of a sorter or pad they may
inspect) and own it, and so does taking its item out: the action bar of whoever takes it out by hand says the frame
is unmarked, and the next item put in marks it only with automark on a container or composter, for whoever put it in.
Only a player's own hit or projectile is stopped. An explosion (lit by a player too), a mob, a dispenser's arrow,
another plugin, or breaking or moving the block the frame hangs on still take the item out or the frame down. That,
breaking blocks, opening the dispenser and dropping items on a pad are left to the server's protection (claims,
regions, locks): FrameSort only decides where items go from afar, who sees it and who undoes it. A frame that a
protection (a lock, a claim) keeps you from hitting or using doesn't answer your sneak-clicks either. Items dropped
on or near someone's pad (within its type's `pull-radius`), or sent from your inventory by crouching on it, go to
that pad's owner. A frame on someone else's chest belongs to whoever marked it, so their sorter can fill that chest
unless the chest's protection stops them from touching the frame.

With `ownership.enabled: false`, `/framesort trust`, `untrust` and `trusted` leave the help, tab completion and
links, and can't be run; `/framesort reload` with it back on brings them back. Names come from the server's profile
cache: a player it no longer remembers shows as their UUID, which `untrust` also takes.

Owners show with their head next to the name: when inspecting, in `/framesort trusted`, and when FrameSort says whose
something is ([which head each account gets](configuration.md#language)).


What has no owner (built before owners existed, or pasted without its data) works only with what has no owner, only
admins inspect it, and anyone unmarks it or takes its item out. Unmarking and marking a frame again (within range of
a sorter or pad you may inspect), putting the activator back, or breaking and placing a pad's top block again gives
it an owner.

### Where frames answer

A frame that is already a target answers sneak-clicks for everyone, wherever it is, unless it is fixed (see
[Inspecting](#inspecting)). Its card shows to whoever may inspect its owner's sorters and pads; anyone else's action
bar says whose it is, or that it has no owner. With `ownership.enabled` only its owner, the players they trust and
admins unmark it; anyone else's action bar says whose it is.
Marking a frame, by hand or with automark, and the card of a frame that isn't a target work only within range
(`sorter.range`, or the pad type's `range`) of a sorter or pad you may inspect: yours, one of a player who trusts
you, or anyone's with `framesort.inspect.others`; with `ownership.enabled: false`, any. So a sorter or pad goes up
before its frames are marked. Anywhere else sneak-clicks on a frame that isn't a target stay vanilla and automark
marks nothing, without a message, so building with frames away from your sorters and pads is left alone.
Only distance and owner count: a sorter or pad in an unloaded chunk doesn't, a pad waiting for its base does, and
WorldGuard regions and the rule that a sorter never sends to another are left to the frame card, whose
`Sources in range` counts what can really reach the frame, among the sorters and pads you may inspect.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `framesort.*` | op | everything |
| `framesort.use` | everyone | `framesort.inspect`, `framesort.target.create` and the help, automark, tag, where, trace, trust, untrust and trusted commands (every `framesort.command.tag-*`) |
| `framesort.admin` | op | `framesort.pad.create`, `framesort.inspect.others`, `framesort.unmark.others` and the give and reload commands |
| `framesort.inspect` | everyone | inspecting sorters, pads and frames |
| `framesort.inspect.others` | op | inspecting, `/framesort where` and `/framesort trace` on sorters and pads whose owner doesn't trust you, the cards of their target frames, and the frame gestures and automark near them |
| `framesort.unmark.others` | op | unmarking target frames whose owner doesn't trust you, and taking the item out of them or the activator out of their sorters |
| `framesort.target.create` | everyone | marking frames as targets, by hand or with automark |
| `framesort.pad.create` | op | creating pads when `creation: permission` |

## commands.yml

Each section of `commands.yml` is one command feature: `help`, `automark`, `tag-list`, `tag-search`, `tag-show`,
`tag-apply`, `tag-remove`, `where`, `trace`, `trust`, `untrust`, `trusted`, `give` and `reload`. Each tag action is
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
    # Also /fstrace [seconds|stop].
    - '/fstrace'
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
  # Not declared by FrameSort, so ops only unless a permissions plugin grants it.
  permission: 'myserver.staff'
  usage:
    - '/framesort tag apply'
    - '/fs tag apply'
```
