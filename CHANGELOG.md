# Changelog

Notable changes to FrameSort. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Taking the item out of a marked frame ends its mark at once; by hand, the action bar says so.
- Marking a frame, by hand or with automark, works only within range of a sorter or pad that sends to the player
  (any, with `framesort.inspect.others`); elsewhere sneak-clicks on a frame that isn't a target stay vanilla, without
  a message. A target answers sneak-clicks everywhere, for everyone.

## [1.0.0] - 2026-11-08

First release, for Paper 26.2 – 26.3 on Java 25.

### Added

- Target frames on chests, barrels, shulker boxes, hoppers, dispensers, droppers and composters, or a drop spot on any
  other block: marked by hand or, with `/framesort automark`, by putting the item in. A frame takes the exact item
  first, then an applied tag, then the same material; a recovery compass takes what nothing else does, and a frame on
  a lava cauldron destroys leftovers.
- Sorters: a hopper in a frame on a dispenser sends up to 64 items a second, from inside shulker boxes and bundles too.
- Sorting pads: the copper and lodestone pads send what rests on them and pull in loose items nearby; the tuff pad
  sends the inventory of a player crouching on it. Pad types, their range and who may build them are in `pads.yml`.
- Inspection of a sorter or pad, which highlights and lists its targets, a card for each target frame,
  `/framesort where` and `/framesort trace`.
- Owners and trust: sorters and pads send only to their owner's frames and to those of players the owner trusts
  (`/framesort trust`, `untrust`, `trusted`). Trust is kept in SQLite, or in MySQL or MariaDB to share it between
  servers.
- Tags: the game's tags and your own in `tags.yml`, with `/framesort tag list`, `search`, `show`, `apply` and
  `remove`.
- WorldGuard: sorters and pads follow its rule for pistons, with the `framesort-deliver` and `framesort-pull` flags.
- CraftEngine: custom items sorted by their ID, its blocks as pad blocks, and its storage blocks and drawers as
  targets.
- `disabled-worlds`, commands placed and switched off in `commands.yml`, messages in English and Spanish, and
  `/framesort reload`.
- `TargetBindEvent` and `PadPullEvent`, for other plugins.
- Usage metrics with bStats.

[Unreleased]: https://github.com/CesarSanchezGomez/FrameSort/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/CesarSanchezGomez/FrameSort/releases/tag/v1.0.0
