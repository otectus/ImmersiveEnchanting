# Immersive Enchanting

*Forge 1.20.1.* Enchanting is performed, not selected.

The enchanting table works as always: put in an item or book and lapis, and three offers appear. Clicking an offer no
longer enchants on the spot. It begins a short **rune-weaving ritual** instead. Runes rise out of the table's sigil
and travel to four binding anchors, and you bind each one as it arrives by tapping, holding or pressing two keys
together. The table still decides *what magic was rolled*. Your ritual decides *how cleanly it binds*.

- Play well and you get exactly the enchantments the table rolled, never more.
- Play imperfectly and the same roll binds weaker: levels drop, and secondary enchantments slip away.
- Fail and nothing binds.
- A ritual never swaps in an enchantment that was not rolled.

Any enchantment the table can roll works automatically, including enchantments from other mods, with no
integration required.

## Playing

1. Open an enchanting table and click an offer as usual.
2. A three-beat count-in pulses on the sigil. **Esc during the count-in cancels for free.**
3. Runes travel to their anchors. Press the anchor's key as a rune reaches it:
   - **Tap rune**: press once.
   - **Hold rune**: press as the head arrives, keep holding while its trail flows in, and release at its end.
   - **Chord**: two linked runes arrive together; press both keys.
4. Timing is judged **Perfect**, **Good**, **Graze** or **Miss**. The outer resonance ring fills as you bind cleanly
   and fractures as you miss.
5. The result panel shows what bound (✔), what weakened (▼ with its new level), what was lost (✖), and what was spent.

The default binding keys are **W A S D**, one per direction: **A** left, **S** down, **W** up and **D** right (Bind
Rune: Left, Down, Up, Right). Change them under *Options > Controls > Immersive Enchanting*; the ritual always labels
anchors with your actual keys. In the circle layout four anchors sit west, south, north and east (A, S, W, D); three
anchors arch west, north and east and use A, W and D. Classic lanes read left to right in the same order.

Pressing Esc after the first rune has arrived asks for confirmation. Abandoning counts as a failed binding, as does
closing the table, disconnecting, or letting the ritual time out.

### Complexity

Harder offers produce harder rituals: more runes, a faster tempo, tighter timing, and holds (from tier 2) and chords
(from tier 3). Complexity comes from the enchantments actually rolled: the offer's level, each enchantment's rolled
level within its range, its rarity, how many enchantments the offer holds, and whether any is a treasure or a curse.
On the vanilla enchanting screen, hovering an offer shows its ritual tier under the clue (*Ritual: Expert ◆◆◆◆◇◇*),
and a small gauge sits beside each offer. This reveals difficulty only, though a harder ritual does hint that more magic is hidden in the offer.

| Tier | Name | Runes | Tempo (BPM) | Windows Perfect / Good / Graze (ms) |
|---|---|---|---|---|
| 0 | Initiate | 12-16 | 80-90 | ±95 / ±145 / ±200 |
| 1 | Apprentice | 16-22 | 90-100 | ±85 / ±135 / ±185 |
| 2 | Adept | 22-30 | 100-115 | ±75 / ±120 / ±170 |
| 3 | Expert | 28-38 | 115-130 | ±70 / ±110 / ±155 |
| 4 | Master | 36-48 | 130-145 | ±60 / ±100 / ±145 |
| 5 | Arcane | 44-60 | 145-160 | ±55 / ±90 / ±130 |

### Outcomes

The final score is `accuracy × 0.75 + best combo × 0.15 + stability × 0.10` (each out of 100). A single early miss
does not ruin a run.

| Score | Binding | Keeps |
|---|---|---|
| 90-100 | Perfect | the full roll, with prestige effects |
| 80-89 | Stable | the full roll |
| 65-79 | Frayed | 75% of the roll's arcane value |
| 50-64 | Weak | 45% of the roll's arcane value |
| 35-49 | Failed | nothing |
| 0-34 | Shattered | nothing |

A partial binding lowers the highest-level secondary enchantments first, then removes secondaries. The **primary
binding**, the enchantment the offer showed as its clue, is weakened last and always survives a Frayed or Weak
binding at least at its minimum level.

**Costs.** Any resolved ritual costs the offer's normal price: in vanilla, one to three levels and lapis for offers
one to three, with the displayed level as a requirement only. By default a failed ritual costs the same and rerolls
your offers, so hard offers cannot be retried for free (see `failureCostMode`). Cancelling during the count-in is
always free.

### Practice

`/ritualpractice [tier]` opens a practice ritual (tier 0-5, default 2) using the real patterns and scoring. It is
played entirely on your client: nothing is enchanted or spent.

## Accessibility

Nothing depends on sound or color. Every lane has its own rune shape (triangle, diamond, circle, star) as well as a
color. The count-in is visual, judgements can be shown as text, and there is no camera movement. The client config
(`immersive_enchanting-client.toml`) offers:

| Option | Default | Effect |
|---|---|---|
| `ritualLayout` | `RADIAL` | `CLASSIC_LANES`: four vertical lanes falling to a binding line (same patterns and scoring) |
| `approachTimeMultiplier` | 1.0 | how long runes are visible before their binding time (0.5-2.0); timing unchanged |
| `backgroundDim` | 0.55 | dimming of the enchanting screen behind the ritual |
| `showNumericScore` / `showTimingLabels` | true | score, combo and accuracy text; judgement words beside anchors |
| `showOfferComplexity` | true | the complexity gauge beside offers |
| `reducedMotion` | false | still glyphs, no pulses, no shards |
| `screenShake` | false | a very small jolt on a miss |
| `highContrastRunes` | false | white runes with dark outlines |
| `colorblindSafeMode` | true | Okabe-Ito palette (off: violet, cyan, gold, rose) |
| `ritualSoundVolume` | 1.0 | ritual sounds |
| `metronomeCue` | false | a tick on every beat, not only during the count-in |
| `timingAssist` | 1.0 | widen your timing windows up to 1.5×; the server caps it (`maxClientTimingAssist`) |

## Server configuration

`serverconfig/immersive_enchanting-server.toml` is per world and synced to clients.

| Section | Option | Default | Notes |
|---|---|---|---|
| general | `enabled` | true | master switch; off = vanilla enchanting |
| | `ritualsForItems`, `ritualsForBooks` | true | which targets need a ritual |
| | `previewRitualDifficulty` | true | offer complexity gauge and tooltip line |
| difficulty | `globalDifficultyMultiplier` | 1.0 | scales every complexity score |
| | `tempoMultiplier`, `eventCountMultiplier`, `timingWindowMultiplier` | 1.0 | independent scaling |
| | `maxClientTimingAssist` | 1.25 | 1.0 gives everyone identical windows |
| | `vanillaReferenceMaxCost` | 30 | displayed level that counts as full power at a vanilla table |
| outcome | `perfectScore`, `fullRewardScore`, `frayedScore`, `weakScore`, `failureScore` | 90, 80, 65, 50, 35 | must descend; invalid settings are logged and replaced by the defaults |
| | `frayedRetention`, `weakRetention` | 0.75, 0.45 | arcane value kept by partial bindings |
| costs | `failureCostMode` | `FULL` | `FULL`, `LAPIS_ONLY` or `NONE` for failed and abandoned rituals |
| | `advanceSeedOnFailure` | true | failures reroll the offers |
| limits | `maxPatternEvents`, `maxRitualDurationSeconds`, `sessionGraceSeconds` | 96, 25, 5 | hard caps |
| bypass | `creativeBypass` | false | creative players enchant instantly |
| | `allowServerBypassPermission` | true | players with permission `immersive_enchanting.bypass_ritual` enchant instantly |
| compat | `serverGuardVanillaEnchantButton` | true | refuse direct enchant-button packets while a ritual is required |
| | `vanillaCompatibleMenus` | [] | class names of `EnchantmentMenu` subclasses that keep vanilla selection and clicks |
| | `apotheosisReferenceMaxCost` | 50 | full-power level for Apotheosis tables |
| debug | `verboseLogging` | false | log adapter choice, profiles, complexity and session transitions at INFO |

## Datapacks

Profiles and pattern sets are server data, reloaded with `/reload`. A malformed file is reported in the log with its
id and skipped; if a reload fails outright, the previous data stays active.

### Enchantment profiles

`data/<namespace>/immersive_enchanting/enchantment_profiles/<name>.json`. Every field is optional; a profile changes
only what it sets.

```json
{
  "priority": 100,
  "selector": { "enchantments": ["examplemod:storm_binding"] },
  "difficulty": { "multiplier": 1.2, "offset": 5.0, "min_tier": 3, "max_tier": 5, "arcane_value_multiplier": 1.15 },
  "pattern": {
    "pattern_set": "immersive_enchanting:default",
    "motif_weight_overrides": { "immersive_enchanting:echo": 1.4, "immersive_enchanting:hold_anchor": 1.2 },
    "chord_bias": 0.15,
    "hold_bias": 0.1
  },
  "behavior": { "enabled": true, "protect_as_primary": false },
  "presentation": { "theme": "immersive_enchanting:arcane" }
}
```

- **Selector**: `enchantments` (ids), `namespaces`, `rarities` (`common`, `uncommon`, `rare`, `very_rare`), `curse`,
  `treasure`, and `all: true`. Every criterion given must match; within a list, any entry matches. An empty selector
  matches nothing.
- **difficulty**: `multiplier` (0-10) and `offset` (-100 to 100) adjust the 0-100 complexity score; `min_tier` and
  `max_tier` clamp it; `arcane_value_multiplier` changes how much the enchantment is worth when a partial binding
  decides what to keep.
- **pattern**: `pattern_set` and `motif_weight_overrides` are taken from the primary binding's profile, while the
  biases are averaged across the bundle. Overrides multiply the weight of motifs already in the set; a motif not in
  the set is added with that weight. Biases range from -1 to 1: -1 removes motifs containing holds or chords, and 1
  triples their weight.
- **behavior**: `enabled: false` makes offers containing the enchantment enchant directly, without a ritual.
  `protect_as_primary: true` makes a secondary enchantment as hard to lose as the primary binding.
- **presentation**: `theme` tints the ritual. Built-in themes: `arcane` (the default), `ember`, `frost`, `tide`,
  `verdant`, `storm` and `void`, all in the `immersive_enchanting` namespace. Unknown themes fall back to `arcane`.

In a bundle of several enchantments, multipliers and offsets are averaged, weighted by each enchantment's arcane
value. The minimum tier is the highest requested and the maximum tier the highest allowed.

**Precedence.** Matching profiles apply from least to most specific, each overriding only the fields it sets:

1. the generic fallback (used for any enchantment no profile names);
2. bundled profiles, meaning anything in the `immersive_enchanting` namespace (this mod's own files);
3. profiles with broad selectors (rarity, curse, treasure, `all`);
4. profiles with namespace selectors;
5. profiles naming exact enchantments;
6. then the server configuration's multipliers and caps.

Within one layer, a higher `priority` applies later and wins. Equal priorities apply in id order, so the lexically
greater id wins; ties are logged at debug level. `/immersiveenchanting debug enchantment <id>` shows exactly which
profiles matched and the result.

### Pattern sets

`data/<namespace>/immersive_enchanting/pattern_sets/<name>.json`. The built-in `immersive_enchanting:default` (the
tier table above) is always available. A set has either the **flat** form, whose values apply at every tier with
runes and tempo interpolated across the whole 0-100 complexity range:

```json
{
  "event_count": { "min": 20, "max": 44 },
  "bpm": { "min": 95.0, "max": 145.0 },
  "timing_windows_ms": { "perfect": 70, "good": 115, "graze": 165 },
  "allowed_event_types": ["tap", "hold", "chord"],
  "motifs": [ { "id": "immersive_enchanting:alternating_pair", "weight": 1.0 },
              { "id": "immersive_enchanting:palindrome", "weight": 0.75 } ],
  "limits": { "max_chord_size": 2, "max_simultaneous_holds": 1, "max_duration_seconds": 25.0 }
}
```

The flat form may also set `anchors` (3 or 4); otherwise rituals use three anchors below tier 2 and four from tier 2.

The **tiered** form is `"tiers": [ ... ]`: exactly six objects with the same fields plus `anchors` and an optional
per-tier `motifs` list. Values interpolate within each tier. Top-level `motifs`, `limits` and `count_in_beats` (2-4,
default 3) apply to both forms. Missing fields take the default set's values for the same tier; in the flat form,
tier 3's. Chords always use two lanes, and at most one hold is active at a time in this version.

Motifs (id prefix `immersive_enchanting:`), with the lowest tier they appear at:

| Motif | Tier | Notes | Motif | Tier | Notes |
|---|---|---|---|---|---|
| `scatter` | 0 | always-available fallback | `mirrored_cascade` | 2 | up and back |
| `alternating_pair` | 0 | A B A B | `left_right_split` | 2 | needs 4 anchors |
| `ascending_cascade` | 0 | sweep I → IV | `center_cross` | 2 | needs 4 anchors |
| `descending_cascade` | 1 | sweep IV → I | `simple_hold` | 2 | hold |
| `pulse` | 1 | one anchor repeated | `hold_anchor` | 3 | hold while tapping others |
| `echo` | 1 | a phrase repeated | `chord_pulse` | 3 | chords |
| `palindrome` | 1 | A B C B A | `syncopated_echo` | 3 | off-beat pairs |
| | | | `chord_cascade` | 4 | chords |

A motif entry may set `"min_tier"` to override its tier. Unknown motif ids are ignored with a warning.

## Commands

Operators (permission level 2); output can reveal hidden enchantments:

- `/immersiveenchanting debug offer [0-2]`: adapter, true costs, planned enchantments, complexity breakdown,
  pattern set and theme for the open table.
- `/immersiveenchanting debug enchantment <id> [level]`: matched profiles in precedence order, the resolved
  profile, arcane values by level, and a sample complexity.
- `/immersiveenchanting debug pattern <tier> [seed]`: a generated pattern's shape and events.
- `/immersiveenchanting session inspect <player>` / `session abort <player>`: an abort is always free.

Client: `/ritualpractice [tier]`.

## Compatibility

- **Enchantment mods** need nothing: whatever the table rolls is used as-is, including custom applicability,
  enchantability, compatibility rules (for example Universal Enchants) and Forge's `#forge:enchanting_fuels`.
  Unknown enchantments get a generic profile from their rarity, level range and cost curve.
- **Enchanting-screen mods** keep working. The vanilla screen is never replaced; screens that subclass or replace it
  over a supported menu are handled when they send their enchant click, so their layout is never guessed.
- **Easy Magic 8.x** (tested with 8.0.1): dedicated adapter. Persistent table inventory, rerolls untouched, vanilla
  costs.
- **Apotheosis 7.x** (tested with 7.4.8, Placebo 8.6.3, Apothic Attributes 1.3.7): dedicated adapter. Offers come from
  Apotheosis's own selection (Eterna, Quanta, Arcana, clues, blacklists and treasure); costs are charged in Apotheosis's
  experience points; its advancement trigger receives the table stats. Level caps and treasure status come from
  Apotheosis's configuration, so a Sharpness IX roll counts as harder than Sharpness V. Infusion recipes need a full
  (Stable or Perfect) binding; a partial binding is charged as a failure and leaves the item unchanged. Eleven of
  Apotheosis's own enchantments have bundled ritual themes, and Life-Mending and Knowledge of the Ages are Expert or
  harder and as hard to lose as the primary binding.
- **Other enchanting blocks** with their own menus are left untouched unless an adapter claims them.
- Clients need the mod: the network channel is required on both sides.

## Java API

Register adapters (and vanilla-compatible menu classes) during mod construction or `FMLCommonSetupEvent`, on both
sides:

```java
ImmersiveEnchantingApi.registerAdapter(new MyTableAdapter());
ImmersiveEnchantingApi.registerVanillaCompatibleMenu("com.example.MyEnchantmentMenu");
```

An `EnchantingContextAdapter` claims menus by type (`supportsMenu`, which also runs on the client).
- `snapshot` captures the exact plan and true cost from the system's own code.
- `checkStart` and `stillMatches` apply the system's own rules.
- `apply` charges and applies a resolved outcome, validating before mutating anything.
- `passThrough` performs the normal enchant for exempt players.
- `maxLevel` and `isTreasure` (optional) report the system's own level caps and treasure rules, when they differ
  from the enchantment's vanilla values.

The resolved list is always a subset of the plan at equal or lower levels, and must not be re-validated against
vanilla rules.

Server-thread events on the Forge bus:
- `RitualPlanEvent`: scale or offset the complexity, override the pattern set. Also posted for previews; see
  `isPreview()`.
- `RitualResultEvent.Pre` (cancelable): cancelling aborts without cost, and `setResolved` narrows the result.
- `RitualResultEvent.Post`: reports what was applied.

## Building and testing

```
./gradlew build                  # jar in build/libs, plus the JUnit suite (pure ritual logic)
./gradlew runGameTestServer      # server GameTests: real tables, sessions, costs, invalidation, datapacks
./gradlew runClient -PsmokeTest  # client agent: plays real rituals, writes run/smoketest/smoketest-result.json
./gradlew runClient -PsmokeTest -PcompatMods=apotheosis -PsmokeExpect=apotheosis   # or easymagic
./gradlew runGameTestServer -PcompatMods=apotheosis   # adds real Apotheosis tables: level caps, infusion
```

Sounds are synthesized by `tools/generate_sounds.py` (numpy and sox) and the logo is drawn by `tools/generate_logo.py`
(Pillow). Neither uses third-party assets.

## License

GPL-3.0. See `LICENSE`.
