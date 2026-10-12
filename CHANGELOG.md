# Changelog

All notable changes to Immersive Enchanting are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## 1.1.1 - 2026-10-12

1.1.0 was never released; its changes ship in this version.

### Added

- **Ritual gating for other mods.** `RitualPlanEvent.deny(Component)` refuses an offer to a player with a reason, so a
  progression mod can lock enchanting before the player plays a ritual. `isDenied()` and `getDenial()` report the
  denial. The first denial stands, and later calls do not replace it.
  - A denied offer is refused before anything is charged: no levels or lapis are spent, the enchantment seed does not
    advance and no session starts. The player sees the denial's reason, and the abort carries the new
    `AbortReason.DENIED_BY_MOD`.
  - A denial takes precedence over profiles with `behavior.enabled: false`, so a gated offer cannot be enchanted
    directly instead. Players exempt from rituals altogether (creative bypass, the bypass permission, or rituals
    switched off globally or for that item or book) enchant as before.
  - The event is also posted for offer previews, so the enchanting screen marks a locked offer: its complexity gauge
    is dimmed and its tooltip gains a red *Locked: reason* line.
- **Advancements.** `immersive_enchanting:first_binding` is awarded for the first ritual that binds at least one
  enchantment, and `immersive_enchanting:perfect_binding` for a Perfect binding. The `immersive_enchanting:root`
  advancement is earned with the first binding. All three are awarded from code, and nothing in the mod depends on
  them.

### Changed

- **Minecraft's own look.** The ritual, its result, the "Preparing the ritual" box and the offer gauge use vanilla's
  GUI in place of the Stoneborn theme (dark stone with brass corners), as Immersive Smithing does since its 1.1.0.
  - The panels are vanilla container panels with dark grey labels. The runes play in a dark display, and the result's
    sigil and readout sit in dark displays so their colours stay readable. Key labels are vanilla button faces that
    light up while held.
  - The offer gauge lights its steps in the gold of the tooltip's ritual line; empty steps are slot grey, and a locked
    offer's steps are dimmed grey.
  - Messages over the table use the player's text background with white text.
  - Rune themes, lane colours and shapes, layouts, timing and controls are unchanged. The panels are drawn from
    Minecraft's textures, so a resource pack that restyles vanilla containers restyles them too. The mod's own
    `gui/stone.png` and `gui/slot.png` are removed.
- When the enchanting table closes mid-ritual, the outcome is written to chat: the binding, what happened to each
  enchantment and what was consumed. 1.0.0 showed only the binding band in the action bar.
- Notices over the table, such as the reason a ritual did not start, stay on screen for at least five seconds and
  longer for longer text. In 1.0.0 they stayed for 3.5 seconds.
- **Compatibility:** the network protocol is now `2`, because the offer-preview and abort packets carry denial
  reasons. The client and server must both run 1.1.1.

### Fixed

- Holding Esc no longer confirms abandoning a ritual. Its key repeat counted as the confirming second press; now only
  a new press confirms.
- A key held as the result panel appears no longer dismisses the panel through key repeat. A new press is required.

## 1.0.0 - 2026-09-26

The first release, for Minecraft 1.20.1 on Forge.

### Added

- **Rune-weaving rituals.** Clicking an enchanting offer starts a short ritual instead of enchanting on the spot.
  Runes travel to three or four binding anchors, and the player binds each one with a tap, a hold or a two-key chord.
  Timing is judged Perfect, Good, Graze or Miss.
- **The table still decides the roll.** A ritual never adds or swaps an enchantment. The score sets the binding band:
  Perfect and Stable keep the full roll, Frayed and Weak keep part of its arcane value, and Failed and Shattered bind
  nothing. A partial binding weakens or drops secondary enchantments first and keeps the primary binding at least at
  its minimum level. Cancelling during the count-in is free.
- **Complexity tiers** from Initiate to Arcane, derived from the enchantments actually rolled. Each tier sets the rune
  count, tempo, timing windows, and whether holds and chords appear. The enchanting screen previews each offer's tier
  as a gauge and a tooltip line.
- `/ritualpractice [tier]` opens a client-side practice ritual that uses the real patterns and scoring.
- **Accessibility options:** a classic-lanes layout, approach-time scaling, a distinct rune shape per lane,
  high-contrast runes, a colorblind-safe palette (on by default), reduced motion, text judgements, a metronome cue and
  client timing assist capped by the server.
- **Server configuration** for the master switch, difficulty scaling, score thresholds, failure costs, hard limits,
  creative and permission bypasses, and the button guard.
- **Datapacks:** enchantment profiles (difficulty, pattern bias, ritual opt-out, protection and theme) with a defined
  precedence, and pattern sets in flat or six-tier form. Both reload with `/reload`.
- **Operator commands:** `/immersiveenchanting debug offer|enchantment|pattern` and
  `/immersiveenchanting session inspect|abort`.
- **Compatibility:** enchantments from any mod work without integration. Easy Magic and Apotheosis have dedicated
  adapters, and screens that replace the vanilla enchanting screen keep working.
- **Java API:** `EnchantingContextAdapter` for other enchanting systems, `RitualPlanEvent` to adjust complexity or the
  pattern set, and `RitualResultEvent.Pre` and `.Post` to narrow or observe results.
