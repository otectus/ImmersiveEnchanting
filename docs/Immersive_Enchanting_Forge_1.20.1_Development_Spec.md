# Immersive Enchanting — Forge 1.20.1 Development Specification

**Working title:** Immersive Enchanting  
**Platform:** Minecraft Java Edition 1.20.1  
**Loader:** Minecraft Forge 47.x  
**Java:** 17  
**Primary goal:** Replace the passive “click an enchantment and receive it” interaction at the enchanting table with a skill-based, rune-themed binding ritual while preserving vanilla/modded enchanting rules and broad compatibility with custom enchantments.

---

## 1. Product Vision

Immersive Enchanting should make enchanting feel like the player is actually performing magic rather than selecting a menu entry.

The enchanting table remains recognizable and compatible with the normal Minecraft enchanting ecosystem. The player still inserts an item or book, supplies enchanting fuel, sees three offers, and chooses an offer. The difference begins after selecting an offer: instead of immediately applying the enchantment, the table opens a short rune-weaving ritual.

The ritual is a timing-and-pattern mini-game inspired by rhythm-game readability, but it must have its own identity. Runes move through an arcane sigil toward binding anchors. The player taps, holds, or combines configurable inputs when rune events reach their binding points. More powerful enchantment rolls generate denser, faster, longer, and mechanically richer rituals.

Performance determines how cleanly the enchantment is bound:

- Excellent play produces the full rolled enchantment result.
- Imperfect play can produce a weakened version of the same rolled result.
- Poor play can remove secondary enchantments or reduce levels.
- Severe failure can produce no enchantment at all.
- Failure never invents a different beneficial enchantment that was not in the original roll.

The system must feel challenging without being arbitrary. Difficulty must be deterministic, explainable, configurable, accessible, and derived from the enchantments that Minecraft/mods actually rolled.

---

## 2. Non-Goals

Version 1.0 should **not**:

- Replace Minecraft's enchantment registry or invent a parallel enchantment system.
- Hardcode a whitelist of supported enchantments.
- Force treasure-only or otherwise unavailable enchantments into enchanting-table rolls.
- Reimplement every mod's enchantment selection algorithm from scratch.
- Require custom-enchantment authors to add Immersive Enchanting integration for ordinary enchantments to work.
- Turn enchanting into an audio-dependent music game.
- Use copyrighted Guitar Hero assets, music, branding, note highways, or copied UI.
- Automatically take over unrelated enchanting blocks such as entirely separate magical crafting systems unless an explicit adapter supports them.

---

## 3. Design Pillars

### 3.1 Preserve the existing enchanting ecosystem

The mod should wrap the normal enchanting process instead of replacing it. If vanilla, Forge, or another mod has already decided that an enchantment is valid for an item and has rolled it into an enchanting-table offer, Immersive Enchanting should accept that result as authoritative.

### 3.2 Skill changes binding quality, not enchantment identity

The mini-game operates on the rolled enchantment plan. It does not secretly reroll a completely different reward based on performance. This makes failure understandable and prevents skill from bypassing progression or mod-specific rarity rules.

### 3.3 Difficulty represents magical complexity

A single low-level common enchantment should be easy. A high-level very-rare enchantment with several hidden secondary enchantments should be visibly and mechanically harder.

### 3.4 Unknown modded enchantments work automatically

An enchantment from an unknown mod should receive a reasonable difficulty profile from its normal Enchantment properties and the actual generated offer. Dedicated integrations improve unusual systems but are not required for ordinary custom enchantments.

### 3.5 Server authority

The server owns the enchantment plan, session validity, resource cost, result calculation, and final item mutation. The client owns presentation and input collection.

### 3.6 Accessibility is a first-class feature

Difficulty should come from pattern complexity and timing—not illegible effects, tiny symbols, color dependence, forced camera movement, or mandatory audio.

---

## 4. Core Player Flow

1. Player opens an enchanting table.
2. Vanilla/modded logic generates the normal three offers.
3. Immersive Enchanting may display a small **ritual complexity indicator** beside each offer, without revealing hidden enchantment identities.
4. Player clicks an offer.
5. The normal immediate enchant action is intercepted before the vanilla button packet is sent.
6. Client sends `StartRitualC2S(containerId, offerIndex)`.
7. Server validates the menu, item, costs, player state, and offer.
8. Server captures the exact enchantment plan that the current enchanting implementation would apply.
9. Server calculates ritual difficulty and generates a deterministic pattern seed.
10. Server creates a short-lived `RitualSession` and sends only the information required to render/play the ritual. The hidden enchantment plan stays server-side.
11. The enchanting screen remains open and a full-screen rune ritual overlay takes control of input.
12. Player completes the pattern.
13. Client submits a bounded input/result record associated with the session ID.
14. Server validates the session and computes the authoritative performance grade.
15. The outcome resolver converts the original enchantment plan into the final plan according to the grade.
16. The server consumes the configured XP/fuel cost, applies the resulting enchantments if any, advances the enchantment seed as appropriate, fires normal side effects, and closes the session.
17. Result overlay reveals what was successfully bound and what was lost/downgraded.

The original enchanting UI should remain visually present beneath the ritual so the experience feels like the table itself has entered an active magical state rather than teleporting the player into an unrelated screen.

---

## 5. Rune-Weaving Mini-Game

### 5.1 Visual identity

Use an arcane binding circle rather than a conventional horizontal note highway.

Recommended default layout:

- Central enchanting-book/sigil motif.
- Four rune anchors around a circular or diamond-shaped binding ring.
- Incoming rune events travel toward their matching anchor.
- A thin outer **Resonance Ring** fills/stabilizes with successful inputs and fractures/destabilizes with misses.
- Each input lane has a distinct rune shape as well as a distinct color.
- Subtle enchanting glyphs move in the background, but do not obscure active events.

Provide an alternate **Classic Lanes** accessibility layout using four clear vertical lanes. It should use the same scoring and patterns.

### 5.2 Default inputs

Use four dedicated, fully remappable key mappings:

- Bind Rune I
- Bind Rune II
- Bind Rune III
- Bind Rune IV

Do not rely on hardcoded keyboard letters. Choose sensible defaults during implementation, but all UI should display the player's actual key bindings.

Optional fifth action for future versions: `Stabilize`, used for special events. Do not require it in v1.0.

### 5.3 Event types

#### Tap Rune
Press one binding input inside the timing window.

#### Hold Rune
Press at the start marker, keep held through the rune's arc, and release within the release window.

#### Chord Rune
Press two binding inputs together. Chords should appear only at medium/high complexity.

#### Echo Rune
A deliberately repeated sequence on one or more anchors. This is not a separate scoring primitive; it is a pattern motif composed of taps.

#### Cascade
An ordered sweep across anchors. This is a pattern motif and should not introduce mouse tracing.

For v1.0, scoring primitives should remain limited to **Tap, Hold, and Chord**. More exotic patterns should be compositions of those primitives so the validator stays simple.

### 5.4 Pattern grammar

Do not generate each note independently at random. Procedural patterns should be built from validated motifs so they feel intentional and remain physically playable.

Suggested motif library:

- alternating pair
- ascending cascade
- descending cascade
- mirrored cascade
- pulse/repeat
- left-right split
- center-cross pattern
- hold anchor + taps on free lanes
- two-note chord pulse
- palindrome
- syncopated echo

Each motif declares:

- minimum difficulty tier
- lane requirements
- length in beats
- event density
- whether it contains holds
- whether it contains chords
- incompatibilities with neighboring motifs

The pattern generator chooses motifs according to the resolved `PatternSet`, concatenates them, validates spacing/overlap, and only then emits concrete timestamped events.

### 5.5 Count-in

Every ritual starts with a short count-in, recommended 2–3 beats. Use pulsing runes or rings rather than requiring audio.

The first scorable event must never occur immediately on receipt of the start packet.

### 5.6 Feedback

Timing judgements:

- **Perfect**
- **Good**
- **Graze**
- **Miss**

Feedback should be readable but restrained. Avoid blocking the center of the play field with large text on every note.

Recommended effects:

- Perfect: rune locks into place with a crisp glow.
- Good: rune binds normally.
- Graze: brief flicker/crack.
- Miss: rune breaks into particles and resonance dips.
- High combo: outer sigil becomes more complete/stable.

No screen shake by default. If added, make it optional and very subtle.

---

## 6. Difficulty Model

Difficulty must be derived from the **actual planned enchantment bundle**, not merely the clicked offer row.

### 6.1 Inputs

For each planned `EnchantmentInstance`, inspect only stable/publicly meaningful properties:

- enchantment rarity
- rolled level
- enchantment minimum level
- enchantment maximum level
- level-specific minimum enchantability cost where useful
- whether the enchantment is a curse
- whether it is treasure-only
- datapack profile modifiers

For the offer/session also include:

- displayed enchanting requirement/cost
- number of rolled enchantments
- clicked offer index as a small secondary factor, not the primary definition of difficulty
- adapter-provided modifiers for overhaul mods
- global server difficulty multiplier

### 6.2 Default complexity score

Resolve a normalized score from `0.0` to `100.0`.

A recommended starting formula is:

```text
powerFactor       = clamp(displayedCost / referenceMaxCost, 0.0, 2.0)
levelFactor       = average(normalizedLevel(instance))
rarityFactor      = average(rarityWeight(instance.rarity))
multiFactor       = clamp((enchantmentCount - 1) / 3.0, 0.0, 1.0)
specialFactor     = clamp(treasureBonus + curseBonus, 0.0, 1.0)

base =
    28 * min(powerFactor, 1.0)
  + 10 * max(powerFactor - 1.0, 0.0)
  + 22 * levelFactor
  + 20 * rarityFactor
  + 15 * multiFactor
  +  5 * specialFactor

complexity = clamp(
    base * globalDifficultyMultiplier * profileMultiplier + profileOffset,
    profileMinimum,
    profileMaximum
)
```

Default `referenceMaxCost = 30` for a vanilla-style table. An adapter may provide a different reference scale for overhaul mods with higher enchanting levels.

Recommended rarity weights:

```text
COMMON     = 0.00
UNCOMMON   = 0.30
RARE       = 0.65
VERY_RARE  = 1.00
```

Recommended normalized level:

```text
if maxLevel <= minLevel:
    normalizedLevel = clamp((rolledLevel - 1) / 4.0, 0.15, 1.0)
else:
    normalizedLevel = (rolledLevel - minLevel) / (maxLevel - minLevel)
```

This formula is a starting point and must be tuned with real enchantment distributions before release.

### 6.3 Difficulty tiers

The raw score should remain available internally, while presentation uses six tiers.

| Tier | Score | Working name | Typical character |
|---|---:|---|---|
| 0 | 0–19 | Initiate | Short, forgiving taps |
| 1 | 20–34 | Apprentice | More notes, simple echoes |
| 2 | 35–49 | Adept | Four anchors, early holds |
| 3 | 50–64 | Expert | Faster patterns, occasional chords |
| 4 | 65–79 | Master | Dense mixed motifs, tighter windows |
| 5 | 80–100 | Arcane | Long, fast, complex but still readable |

### 6.4 Suggested default generation ranges

| Tier | Anchors | Events | Approx. BPM | Perfect window | Good window | Graze window | Mechanics |
|---|---:|---:|---:|---:|---:|---:|---|
| 0 | 3 | 12–16 | 80–90 | ±95 ms | ±145 ms | ±200 ms | taps |
| 1 | 3 | 16–22 | 90–100 | ±85 ms | ±135 ms | ±185 ms | taps, echoes |
| 2 | 4 | 22–30 | 100–115 | ±75 ms | ±120 ms | ±170 ms | taps, holds |
| 3 | 4 | 28–38 | 115–130 | ±70 ms | ±110 ms | ±155 ms | holds, light chords |
| 4 | 4 | 36–48 | 130–145 | ±60 ms | ±100 ms | ±145 ms | mixed motifs |
| 5 | 4 | 44–60 | 145–160 | ±55 ms | ±90 ms | ±130 ms | dense mixed motifs |

These values are intentionally less punishing than competitive rhythm games. Minecraft servers have latency variation and players did not necessarily install an enchanting mod to play a precision rhythm game.

The server config must allow scaling event count, tempo, and timing windows independently.

### 6.5 Monotonicity requirement

Without a datapack override, increasing any of the following should never make a ritual easier:

- displayed enchanting power
- rolled enchantment level
- rarity
- number of enchantments in the bundle

Unit tests must enforce this property across representative inputs.

---

## 7. Scoring

### 7.1 Per-event values

Recommended normalized values:

```text
Perfect = 1.00
Good    = 0.78
Graze   = 0.45
Miss    = 0.00
```

For Hold Runes, score start, sustained completion, and release as one composite event. A completely dropped hold should not generate dozens of individual misses.

For Chord Runes, all required inputs must occur inside a chord tolerance window. Partial chords receive Graze at most.

### 7.2 Final performance score

Recommended default:

```text
accuracyScore = weighted event accuracy, 0..100
comboScore    = longestCombo / totalEvents * 100
stability     = max(0, 100 - missRate * 160 - brokenHoldRate * 80)

finalScore =
    accuracyScore * 0.75
  + comboScore    * 0.15
  + stability     * 0.10
```

Do not make combo so valuable that one early miss mathematically ruins the run.

### 7.3 Judgement source

Client presentation may judge inputs instantly for responsiveness, but the server must receive enough bounded information to validate the claimed result against the deterministic event sequence.

Recommended compromise for v1.0:

- Client records event input deltas relative to ritual start.
- Input timestamps are quantized to milliseconds and capped to a sane count.
- Client submits the input batch at completion or in small bounded chunks.
- Server reconstructs the same pattern and runs the same scoring engine.
- Server rejects impossible, duplicated, oversized, stale, or out-of-order submissions.

Do not trust a client-submitted final percentage by itself.

This is not intended to be an anti-cheat system against a malicious custom client; it is intended to make the normal server authoritative and prevent trivial packet spoofing.

---

## 8. Outcome / Failure System

### 8.1 Default outcome bands

| Final score | Result | Default retained magical budget |
|---:|---|---:|
| 90–100 | Perfect Binding | 100% |
| 80–89 | Stable Binding | 100% |
| 65–79 | Frayed Binding | 75% |
| 50–64 | Weak Binding | 45% |
| 35–49 | Failed Binding | 0% |
| 0–34 | Shattered Binding | 0% |

Perfect and Stable both grant the full rolled enchantment. Perfect should grant visual/audio prestige only by default; it should **not** increase enchantment levels beyond the original roll.

The difference between Failed and Shattered is presentation and optional configurable resource handling. Neither grants a replacement enchantment by default.

### 8.2 Why use a retained budget

A budget-based downgrade system scales to unknown modded enchantments without authoring handcrafted fallback rules for every enchantment.

Calculate an **arcane value** for each enchantment instance from:

- rarity
- level
- the enchantment's level-specific cost curve
- exact/datapack profile modifiers

The planned bundle's total is its full budget.

For a degraded result:

1. Start from the exact rolled plan.
2. Mark the visible/clue enchantment as the **primary binding** when identifiable.
3. Calculate target retained budget from the outcome band.
4. Reduce levels one step at a time, preferring to preserve the primary binding.
5. Once an enchantment reaches its minimum valid level, remove secondary enchantments as necessary.
6. Drop the primary only if no non-empty result can fit the target budget.
7. Never raise a level.
8. Never introduce an enchantment that was not in the original plan.
9. Never retain an invalid level below the enchantment's declared minimum.
10. Use deterministic tie-breaking by registry ID so the same plan + score always produces the same result.

### 8.3 Suggested downgrade priorities

When reducing value:

1. Reduce high-level secondary enchantments first.
2. Remove low-priority secondary enchantments next.
3. Reduce the primary enchantment only after secondary losses are insufficient.
4. Remove the primary only for a true failure result.

This makes partial failure feel like the ritual could not hold all the magic rather than randomly replacing the enchantment the player thought they selected.

### 8.4 Resource costs on failure

Expose a server config enum:

```text
FULL
LAPIS_ONLY
NONE
```

Recommended default: `FULL`.

Rationale: beginning and completing a ritual is the enchanting attempt. If failed attempts are free, players can brute-force difficult rolls with no meaningful cost.

Default behavior:

- Any resolved attempt consumes the normal cost for that offer.
- Any resolved attempt advances the player's enchantment seed.
- Cancel before the first playable event: no cost and no seed change.
- Close/abort after the ritual has begun: treat as failure if the server can validly resolve the session.
- Creative-mode behavior remains configurable and should respect vanilla creative exemptions by default.

A less punitive server can select `LAPIS_ONLY` or `NONE`.

---

## 9. Preserving Vanilla Enchanting Semantics

For a normal 1.20.1 enchanting table, preserve vanilla/Forge behavior wherever possible:

- Existing offer generation remains authoritative.
- Existing enchantment applicability remains authoritative.
- Existing enchanting fuel tag support remains intact.
- Existing item enchantability values remain intact.
- The displayed level requirement is still only an eligibility requirement.
- Actual vanilla resource consumption for an offer remains equivalent to the selected slot's normal cost semantics.
- Books convert to enchanted books only after a non-empty successful result.
- Item NBT such as custom name and lore must be preserved through book conversion.
- The player's enchantment seed advances on resolved attempts according to configured behavior.
- Normal enchanting stat/criterion/sound behavior is reproduced.

Do not silently reinterpret the displayed cost as the number of XP levels consumed unless an adapter explicitly says that the current enchanting implementation does so.

---

## 10. Technical Architecture

### 10.1 Core principle: a thin enchanting gate

Do not replace `EnchantmentMenu` globally.

For the standard enchanting screen:

- Detect the click on one of the three offer rows using Forge screen input events.
- Cancel the normal click before `EnchantmentScreen` sends the normal container-button packet.
- Start the Immersive Enchanting protocol instead.
- Render the ritual as an overlay on the existing screen.

This minimizes conflicts with texture/UI mods and leaves offer generation to the code that already owns enchanting.

### 10.2 Why a small Mixin/Invoker is justified

Vanilla `EnchantmentMenu` keeps the method that constructs the actual selected enchantment list private.

Use a narrowly scoped Mixin `@Invoker` (or an Access Transformer if preferred) to call that exact private method when creating a server-side snapshot.

Rules:

- No overwrite of `clickMenuButton` for primary behavior.
- No copy-paste reimplementation of vanilla `selectEnchantment` logic.
- Keep the invoker isolated in one class.
- Add automated startup/integration tests so a mapping/signature mismatch fails clearly.

The goal is to capture the exact list the table would have used, including Forge and mod behavior that influences selection.

### 10.3 Optional server guard

A malicious or incompatible client could still send the vanilla container-button packet directly.

Provide a minimal server-side guard around `EnchantmentMenu#clickMenuButton` that can reject direct enchanting while Immersive Enchanting is enabled for that menu/player.

This guard should be:

- narrowly injected at method head
- conditional
- adapter-aware
- disabled when another integration declares ownership
- covered by compatibility tests

If this guard causes widespread mod conflicts, ship it behind a server config and log when it is disabled.

### 10.4 Adapter model

Create a menu/system adapter SPI instead of scattering mod-ID checks through core code.

```java
public interface EnchantingContextAdapter {
    ResourceLocation id();
    int priority();

    boolean supports(ServerPlayer player, AbstractContainerMenu menu);

    Optional<OfferSnapshot> snapshot(
        ServerPlayer player,
        AbstractContainerMenu menu,
        int offerIndex
    );

    ApplyResult apply(
        ServerPlayer player,
        AbstractContainerMenu menu,
        RitualSession session,
        List<EnchantmentInstance> resolvedEnchantments,
        Outcome outcome
    );
}
```

`OfferSnapshot` should contain at least:

```java
record OfferSnapshot(
    int containerId,
    int offerIndex,
    int displayedRequirement,
    CostSnapshot cost,
    ResourceLocation primaryClueId,
    int primaryClueLevel,
    List<EnchantmentInstance> plannedEnchantments,
    ItemFingerprint itemFingerprint,
    int playerEnchantSeed,
    DifficultyScale difficultyScale,
    CompoundTag adapterData
) {}
```

The adapter owns system-specific resource charging and application semantics.

### 10.5 Adapter selection

At ritual start:

1. Query registered adapters by descending priority.
2. First adapter whose `supports(...)` returns true owns the session.
3. Fall back to the vanilla/Forge adapter.
4. Log the selected adapter at debug level.

Select by **actual menu/context type and behavior**, not only by `ModList.isLoaded(...)`. This handles installations where several enchanting mods coexist.

---

## 11. Suggested Package Structure

```text
com.<author>.immersiveenchanting/
├── ImmersiveEnchanting.java
├── api/
│   ├── ImmersiveEnchantingApi.java
│   ├── EnchantingContextAdapter.java
│   └── event/
│       ├── RitualPlanEvent.java
│       └── RitualResultEvent.java
├── config/
│   ├── CommonConfig.java
│   └── ClientConfig.java
├── enchanting/
│   ├── AdapterRegistry.java
│   ├── VanillaEnchantingAdapter.java
│   ├── OfferSnapshot.java
│   ├── CostSnapshot.java
│   ├── EnchantmentPlanService.java
│   ├── DifficultyCalculator.java
│   ├── ArcaneValueCalculator.java
│   ├── OutcomeResolver.java
│   ├── EnchantmentApplier.java
│   └── ItemFingerprint.java
├── ritual/
│   ├── RitualSession.java
│   ├── RitualSessionManager.java
│   ├── RitualState.java
│   ├── RitualPattern.java
│   ├── PatternGenerator.java
│   ├── PatternValidator.java
│   ├── PatternGrammar.java
│   ├── RitualEvent.java
│   ├── TapEvent.java
│   ├── HoldEvent.java
│   ├── ChordEvent.java
│   ├── ScoreEngine.java
│   └── Outcome.java
├── data/
│   ├── EnchantmentProfile.java
│   ├── EnchantmentProfileManager.java
│   ├── EnchantmentProfileReloadListener.java
│   ├── ProfileSelector.java
│   ├── PatternSet.java
│   └── PatternSetReloadListener.java
├── network/
│   ├── ModNetwork.java
│   ├── StartRitualC2S.java
│   ├── RitualStartedS2C.java
│   ├── RitualInputBatchC2S.java
│   ├── RitualResolvedS2C.java
│   ├── RitualAbortedS2C.java
│   └── OfferDifficultyS2C.java
├── client/
│   ├── ClientRitualController.java
│   ├── EnchantmentScreenHooks.java
│   ├── RitualOverlayRenderer.java
│   ├── RitualResultOverlay.java
│   ├── RitualKeyMappings.java
│   ├── layout/
│   │   ├── RadialRitualLayout.java
│   │   └── LaneRitualLayout.java
│   └── sound/
│       └── RitualSoundController.java
├── compat/
│   ├── CompatibilityBootstrap.java
│   ├── apotheosis/
│   └── easymagic/
└── mixin/
    ├── EnchantmentMenuInvoker.java
    └── EnchantmentMenuGuardMixin.java
```

Exact package names may change, but keep client code, optional integrations, data resolution, ritual math, and Minecraft mutation logic separated.

---

## 12. Ritual Session Model

### 12.1 State machine

```text
CREATED
  -> COUNTDOWN
  -> PLAYING
  -> RESOLVING
  -> RESOLVED

Any active state may transition to ABORTED for an invalid menu/item/session.
```

A player may have at most one active ritual.

### 12.2 Session contents

Server session should store:

- UUID session ID
- player UUID
- adapter ID
- container ID
- offer index
- immutable `OfferSnapshot`
- item fingerprint
- original enchantment seed
- resolved complexity score/tier
- pattern seed
- pattern configuration ID/hash
- start server tick/time
- expected duration
- expiration time
- last accepted packet sequence
- current state
- whether gameplay has crossed the point where failure costs apply

### 12.3 Item fingerprint

Fingerprint enough state to detect swapping/mutation during a ritual without serializing arbitrary giant NBT into every packet.

Recommended fingerprint fields:

- item registry ID
- count
- stable hash of item NBT/tag
- damage value
- optionally slot index/menu context

Store the full server-side item snapshot only if required for restoration logic. Do not trust a client-supplied fingerprint.

### 12.4 Invalidation

Abort without applying an enchantment if:

- container ID changes
- player no longer has the same enchanting context
- target stack fingerprint changes unexpectedly
- offer becomes invalid
- server reload invalidates required pattern/profile data
- session expires
- duplicate resolution packet arrives after completion

Resolution must be idempotent.

---

## 13. Deterministic Pattern Generation

### 13.1 Seed

Do **not** include the world seed.

A good deterministic seed can be derived server-side from:

```text
hash(
    player UUID,
    player enchantment seed,
    container ID,
    offer index,
    target item fingerprint,
    selected pattern-set ID,
    server-side salt/non-secret mod constant
)
```

The server sends the resulting pattern seed, not the original ingredients.

Using the player's enchantment seed helps keep the ritual stable for the same underlying offer and changes naturally when the enchanting seed advances.

### 13.2 Generator requirements

The generator must guarantee:

- no event before count-in completes
- no overlapping impossible presses
- no new tap on a lane that is occupied by a hold unless explicitly supported
- minimum spacing between repeated presses on the same lane
- chord size capped at two in v1.0
- maximum event count hard capped, recommended `96`
- total duration hard capped, recommended `25 seconds` by default
- generated result independent of rendering FPS

### 13.3 Determinism test

Given the same:

- pattern seed
- pattern-set definition
- difficulty score
- generator version

both client and server must generate byte-for-byte equivalent event timelines.

Include an explicit generator version integer in the protocol/session. Bump it when generation logic changes in a network-incompatible way.

---

## 14. Networking

Use Forge networking (`SimpleChannel` on the 1.20.1 target).

### 14.1 Protocol messages

#### `StartRitualC2S`

```text
containerId
offerIndex
```

Server validates everything else.

#### `RitualStartedS2C`

```text
sessionId
containerId
complexityScore or tier
patternSeed
patternSetId
patternGeneratorVersion
countdownMs
serverStartTimestamp/tick reference
resolved gameplay parameters
primary visible clue (optional, already known to client)
```

Do **not** send the hidden enchantment bundle before resolution.

#### `RitualInputBatchC2S`

```text
sessionId
sequenceNumber
inputs[]
```

Each input:

```text
relativeTimeMs
inputIndex
pressed/released
```

Bound maximum input count per packet and maximum packet size.

#### `RitualResolvedS2C`

```text
sessionId
finalScore
outcomeBand
resulting enchantment IDs + levels
resource outcome summary
```

At this point revealing the applied result is fine.

#### `RitualAbortedS2C`

```text
sessionId
reasonCode
```

Use localizable client messages for reason codes.

### 14.2 Offer difficulty preview

Optional `OfferDifficultyS2C` can send three compact tier values associated with the current menu state.

The preview should reveal **difficulty only**, not hidden enchantment identities.

This deliberately gives the player a subtle clue that one offer may contain more/stronger magic. Make it configurable with `previewRitualDifficulty`.

---

## 15. Screen Integration

### 15.1 Do not replace the enchanting screen by default

Use Forge `ScreenEvent` hooks to cooperate with UI/resource-pack mods.

On `EnchantmentScreen`:

- detect press inside one of the three option regions
- if no ritual is active and Immersive Enchanting owns this menu, cancel the pre-click event
- send `StartRitualC2S`
- do not invoke/send the vanilla enchant button action

### 15.2 Render overlay

Render using the screen render post event while a client ritual is active.

The overlay should:

- dim, blur, or tint the underlying panel lightly
- keep enough of the enchanting table UI visible for context
- capture relevant keyboard/mouse input
- prevent inventory manipulation while active
- remain resolution-independent
- respect GUI scale

If shader-based blur creates compatibility/performance problems, ship simple dimming as the default.

### 15.3 Unsupported custom screens

If a mod uses an entirely different screen/menu and no adapter claims it:

- do not guess coordinates
- do not intercept input
- leave that enchanting system untouched
- log a debug message that the menu is unsupported

This is preferable to corrupting items or double-charging resources.

---

## 16. Automatic Custom-Enchantment Compatibility

This is a hard requirement.

### 16.1 Default behavior

Any custom enchantment that the active enchanting implementation legitimately rolls into the selected offer must work without dedicated support.

Do **not** build compatibility by scanning the registry and manually deciding what may appear on the table. Registry inspection is useful for metadata/profile resolution, but selection must remain owned by the active enchanting system.

This preserves mod-specific behavior such as:

- custom `canApplyAtEnchantingTable` logic
- custom book allowance
- custom enchantability curves
- incompatibility rules
- treasure/discoverability behavior
- custom items with non-vanilla enchantability values
- Forge enchanting-fuel tag behavior

### 16.2 Unknown enchantment fallback profile

When no datapack profile exists:

1. Read rarity.
2. Read min/max level.
3. Read rolled level.
4. Read curse/treasure flags.
5. Sample level-specific minimum cost if useful.
6. Apply the generic difficulty formula.
7. Use the default rune/pattern theme.

Unknown namespace must never mean “unsupported.”

### 16.3 Do not revalidate a captured plan with vanilla assumptions

Once the active adapter has captured an authoritative enchantment plan, do not reject it because a later generic check disagrees.

This matters for mods that deliberately alter normal enchantment compatibility.

---

## 17. Datapack Support

Use server datapacks to customize difficulty and pattern behavior without requiring code.

Recommended implementation: reload listeners registered through Forge's server resource reload event, with compiled immutable maps swapped atomically after successful parse/validation.

### 17.1 Resource locations

```text
data/<namespace>/immersive_enchanting/enchantment_profiles/*.json
data/<namespace>/immersive_enchanting/pattern_sets/*.json
```

### 17.2 Enchantment profile schema

Example:

```json
{
  "priority": 100,
  "selector": {
    "enchantments": [
      "examplemod:storm_binding"
    ]
  },
  "difficulty": {
    "multiplier": 1.20,
    "offset": 5.0,
    "min_tier": 3,
    "max_tier": 5,
    "arcane_value_multiplier": 1.15
  },
  "pattern": {
    "pattern_set": "immersive_enchanting:default",
    "motif_weight_overrides": {
      "immersive_enchanting:echo": 1.4,
      "immersive_enchanting:hold_anchor": 1.2
    },
    "chord_bias": 0.15,
    "hold_bias": 0.10
  },
  "behavior": {
    "enabled": true,
    "protect_as_primary": false
  },
  "presentation": {
    "theme": "immersive_enchanting:arcane"
  }
}
```

### 17.3 Selector options

Support selectors for:

- exact enchantment IDs
- namespace(s)
- rarity values
- curse flag
- treasure flag
- all enchantments fallback

Avoid arbitrary Java class names in datapacks.

Optional future selectors may include enchantment tags if a reliable/tag-friendly representation is exposed for the target version.

### 17.4 Merge/precedence rules

Resolve from least specific to most specific:

1. generated generic fallback
2. built-in bundled compatibility profiles
3. datapack broad selectors
4. datapack namespace selectors
5. datapack exact enchantment selectors
6. global server hard caps/multipliers

Within the same specificity, higher numeric `priority` wins. If priority ties, use deterministic lexical resource-location ordering and emit a debug log.

Document this behavior so modpack authors can reason about overrides.

### 17.5 Pattern-set schema

Example:

```json
{
  "event_count": {
    "min": 20,
    "max": 44
  },
  "bpm": {
    "min": 95.0,
    "max": 145.0
  },
  "timing_windows_ms": {
    "perfect": 70,
    "good": 115,
    "graze": 165
  },
  "allowed_event_types": [
    "tap",
    "hold",
    "chord"
  ],
  "motifs": [
    {
      "id": "immersive_enchanting:alternating_pair",
      "weight": 1.0
    },
    {
      "id": "immersive_enchanting:palindrome",
      "weight": 0.75
    }
  ],
  "limits": {
    "max_chord_size": 2,
    "max_simultaneous_holds": 1,
    "max_duration_seconds": 25.0
  }
}
```

All parsed values must be clamped/validated server-side. Reject malformed definitions with useful resource-location-aware log messages, while keeping the previous successful data snapshot if a reload fails catastrophically.

### 17.6 Presentation and resource packs

Server datapacks must not assume a client owns arbitrary textures referenced by a pack.

If profile `theme` or rune presentation IDs are unknown on the client, fall back to built-in visuals. Modpacks may distribute an accompanying resource pack for custom art.

---

## 18. Dedicated Compatibility Strategy

There are two different compatibility categories:

### 18.1 Category A — Mods that add ordinary enchantments

These should generally require **no code adapter**. Ship curated datapack profiles only when their enchantments benefit from hand-tuned difficulty.

Recommended curated/profile test targets for Forge 1.20.1:

- Ensorcellation
- Majrusz's Enchantments
- Iron's Spells enchantment add-ons where applicable
- other high-use enchantment packs discovered during release testing

The generic fallback remains the compatibility guarantee.

### 18.2 Category B — Mods that alter the enchanting table/system

These may require a code adapter because their menu, costs, offer scale, or item application differs from vanilla.

#### Apotheosis 7.x — P0 dedicated adapter

Apotheosis materially expands enchanting-table mechanics and can use substantially different enchanting power/cost concepts.

Adapter responsibilities:

- recognize the Apotheosis enchanting menu rather than relying on vanilla menu assumptions
- capture the exact offered enchantment list from Apotheosis-owned logic
- use the mod's true resource/XP cost semantics
- provide an appropriate difficulty reference scale instead of assuming a vanilla max requirement of 30
- preserve Eterna/Quanta/Arcana-derived offer behavior
- allow Apotheosis to remain authoritative for any specialized application behavior
- ensure ritual difficulty scales with high-end Apotheosis offers without instantly pinning everything to tier 5

Do not make Apotheosis a hard dependency.

#### Easy Magic 8.x — P0/P1 dedicated adapter

Easy Magic changes enchanting-table quality-of-life behavior such as persistent inventory and reroll/offer interaction.

Adapter responsibilities:

- recognize its custom menu
- preserve persistent item/fuel behavior
- preserve its reroll mechanics
- never duplicate a reroll because a ritual completes/fails
- ensure item slots remain synchronized if the ritual is cancelled

Test both Easy Magic alone and any commonly used compatibility layer with Apotheosis.

#### Universal Enchants — behavioral compatibility target

Universal Enchants can alter what enchantments are mutually compatible/applicable.

Immersive Enchanting should not need a dedicated application fork if it treats the captured plan as authoritative.

Test specifically that the outcome resolver never reimposes vanilla incompatibility rules on a plan already produced by the active enchanting system.

#### Enchantment Descriptions — UI coexistence target

No gameplay integration should be required. Verify that tooltip/description rendering remains functional and that Immersive Enchanting does not replace screens in a way that removes its UI additions.

### 18.3 Alternative enchanting blocks

For systems that do not use a recognizable enchanting-table menu at all:

- v1.0 default: leave untouched
- future: add explicit adapters if the interaction maps cleanly to an enchantment plan + cost + application contract

Do not claim compatibility merely because a mod is installed.

---

## 19. Optional-Mod Loading Safety

No core class may have a static reference to an optional mod class that can be loaded when the mod is absent.

Use a compatibility bootstrap such as:

```java
if (ModList.get().isLoaded("apotheosis")) {
    CompatModules.loadApotheosis();
}
```

Keep the actual optional-class references inside the compat package/module loaded only after the presence check.

Where practical, avoid reflection after bootstrap and compile dedicated integrations against `compileOnly`/development dependencies.

If an adapter fails to initialize:

- log one clear warning
- disable only that adapter
- retain generic behavior where safe
- never crash the game merely because optional integration failed unless continuing would risk item loss/corruption

---

## 20. Server Application Semantics

### 20.1 Vanilla adapter application

For a successful/non-empty resolved plan, reproduce normal table effects:

- validate session and item fingerprint one final time
- charge the correct XP/fuel cost
- convert a normal book to enchanted book where appropriate
- apply each resolved `EnchantmentInstance`
- preserve unrelated NBT
- invoke normal player enchanting bookkeeping / seed update
- award the normal enchant-item statistic
- trigger the normal enchanted-item advancement criterion where applicable
- mark/update the container slots
- play the enchanting-table use sound
- broadcast slot changes

For a zero-enchantment failure, still perform configured resource/seed behavior, but do not convert a book to an enchanted book.

### 20.2 Atomicity

The server should validate all preconditions before mutating anything.

Conceptually:

```text
validate -> compute final result -> verify costs -> mutate item/cost/seed -> sync -> resolve session
```

Do not consume fuel and then discover that the item no longer matches the session.

### 20.3 Error recovery

If an exception occurs before mutation, abort safely.

If an exception occurs during mutation, log a high-severity diagnostic containing session ID, adapter ID, and relevant registry IDs but never duplicate the reward by retrying blindly.

Keep the mutation section compact and well tested.

---

## 21. Client Accessibility / UX Settings

Client config should include:

```text
ritualLayout = RADIAL | CLASSIC_LANES
reducedMotion = false
screenShake = false
backgroundDim = 0.55
showNumericScore = true
showTimingLabels = true
highContrastRunes = false
colorblindSafeMode = true
ritualSoundVolume = 1.0
metronomeCue = false
```

Key mappings are handled through normal Minecraft key-binding settings rather than config fields.

Server config should be able to apply an optional accessibility timing multiplier ceiling/floor if a competitive server wants identical windows for everyone, but the default philosophy should favor usability rather than competition.

Never make successful play depend on:

- distinguishing red from green
- hearing rhythm/audio cues
- rapid mouse movement
- reading tiny text
- camera shake

---

## 22. Common/Server Configuration

Suggested options:

```text
enabled = true
ritualsForItems = true
ritualsForBooks = true
previewRitualDifficulty = true

globalDifficultyMultiplier = 1.0
tempoMultiplier = 1.0
eventCountMultiplier = 1.0
timingWindowMultiplier = 1.0

fullRewardScore = 80
perfectScore = 90
frayedScore = 65
weakScore = 50
failureScore = 35

failureCostMode = FULL
advanceSeedOnFailure = true

maxPatternEvents = 96
maxRitualDurationSeconds = 25
sessionGraceSeconds = 5

creativeBypass = false
allowServerBypassPermission = true

serverGuardVanillaEnchantButton = true
```

Validate ordering of score thresholds at config load. If invalid, log a clear error and use safe defaults rather than producing impossible bands.

---

## 23. Public API / Extension Points

A small API will make future compatibility easier.

### 23.1 Adapter registration

```java
ImmersiveEnchantingApi.registerAdapter(EnchantingContextAdapter adapter);
```

Registration should happen during a defined lifecycle stage before gameplay.

### 23.2 Difficulty event

Optional Forge event:

```java
RitualPlanEvent
```

Exposes:

- server player
- adapter ID
- read-only offer snapshot
- mutable difficulty multiplier/offset
- mutable pattern-set override

Do not allow arbitrary client-only code to alter the server plan.

### 23.3 Result events

`RitualResultEvent.Pre` may allow another mod to cancel/adjust application in a controlled way.

`RitualResultEvent.Post` reports the final applied result.

Keep these events server-only and document threading.

---

## 24. Debug / Administration Tools

Recommended commands:

```text
/immersiveenchanting debug offer
/immersiveenchanting debug enchantment <id>
/immersiveenchanting debug pattern <tier> [seed]
/immersiveenchanting session inspect <player>
/immersiveenchanting session abort <player>
```

Useful debug output for an offer:

- selected adapter
- displayed requirement
- true resource cost
- planned enchantment IDs/levels (operator/debug only)
- generic difficulty components
- matched datapack profiles in precedence order
- final complexity score/tier
- pattern set

Do not spam normal player logs.

A client-only practice/debug screen may be useful during development, but it should not affect real enchanting progression.

---

## 25. Data / Compatibility Diagnostics

On datapack reload, optionally log a compact summary at debug level:

```text
Loaded 14 Immersive Enchanting profiles, 4 pattern sets.
Resolved 87 registered enchantments: 63 generic, 24 explicit/profiled.
```

Provide a debug command that explains why a specific enchantment received its difficulty values. This will be extremely valuable to modpack authors.

---

## 26. Testing Strategy

### 26.1 Unit tests

#### Difficulty calculator

- rarity monotonicity
- level monotonicity
- enchantment count monotonicity
- displayed power monotonicity
- profile multiplier/offset correctness
- clamps
- single-level enchantments
- enchantments with unusual min/max levels

#### Pattern generator

- deterministic output for fixed seed/config
- no impossible overlapping events
- max event count respected
- max duration respected
- chord size cap respected
- hold/tap lane collision prevention
- all tiers can generate many seeds without failure

Use property-style tests across thousands of deterministic seeds.

#### Score engine

- exact center hits produce 100
- boundary timings classify correctly
- duplicate presses cannot double-score one event
- chord partials behave consistently
- holds score start/sustain/release correctly

#### Outcome resolver

For every degraded result:

- every retained enchantment existed in the original plan
- retained level is `<=` original level
- retained level is valid for the enchantment
- no duplicate incompatible artifacts are introduced
- deterministic output for the same plan/score
- primary enchantment is preserved according to policy when budget permits

### 26.2 Game/integration tests

Test at least:

- sword enchant
- armor enchant
- bow/crossbow enchant
- tool enchant
- normal book -> enchanted book
- item with name/lore/NBT
- custom item with modded enchantability
- custom item used as enchanting fuel through the Forge tag
- enchantment with max level 1
- curse
- multi-enchantment roll
- custom enchantment from a tiny internal test mod
- failure with every resource-cost mode
- closing screen before ritual starts
- closing screen after ritual starts
- disconnect during ritual
- inventory/menu mutation attack
- replayed resolve packet
- malformed input packet

### 26.3 Compatibility matrix

Release CI/manual test matrix should include:

| Configuration | Expected |
|---|---|
| Forge only | full support |
| common custom-enchantment test mod | generic auto-support |
| Ensorcellation | generic + curated profile support |
| Majrusz's Enchantments | generic + curated profile support |
| Universal Enchants | captured compatibility rules preserved |
| Enchantment Descriptions | UI coexistence |
| Easy Magic | dedicated adapter |
| Apotheosis | dedicated adapter |
| Apotheosis + known compatible enchanting QoL stack | no double charge/reroll |

Record exact tested mod versions in the release notes/compatibility file, not only in source comments.

---

## 27. Acceptance Criteria

Version 1.0 is not complete until all of the following are true:

### Core gameplay

- Clicking a supported enchanting-table offer starts a ritual instead of immediately enchanting.
- Ritual generation is deterministic and derived from the actual rolled enchantment bundle.
- Higher-complexity rolls are observably harder on average.
- Tap, Hold, and Chord events work at intended tiers.
- Server computes final score from validated input data.
- Full, degraded, and failed outcomes all work.

### Safety

- No item duplication from duplicate/replayed packets.
- No double resource charge.
- No enchantment can exceed its originally rolled level because of the mini-game.
- Failure never introduces a new enchantment ID.
- A changed target item invalidates the session before mutation.
- Disconnect/reconnect cannot resolve the same ritual twice.

### Generic compatibility

- A previously unknown registry enchantment can appear through the normal table and complete a ritual without code changes.
- The mod does not make normally unavailable enchantments table-obtainable by itself.
- Modded item enchantability is respected.
- Forge enchanting-fuel tags are respected.

### Datapacks

- Exact enchantment override works.
- Namespace-wide override works.
- Profile priorities are deterministic.
- `/reload` replaces the compiled profile/pattern state safely.
- Bad JSON reports the resource location and does not corrupt active server state.

### UX/accessibility

- Entire ritual can be played without audio.
- Rune anchors are distinguishable without color.
- GUI scale changes do not break hit/input regions.
- Classic-lanes layout is available.
- All four binding inputs are remappable.

### Dedicated integrations

- Apotheosis and Easy Magic adapters either pass their compatibility suite or are clearly marked experimental rather than silently claiming full support.

---

## 28. Development Milestones

### Milestone 0 — Project skeleton

- Forge 1.20.1 workspace
- Java 17
- configs
- logging
- networking skeleton
- mixin/accessor configuration
- automated test setup

### Milestone 1 — Vanilla enchanting gate

- intercept enchanting-option clicks
- create server `OfferSnapshot`
- private enchantment-list invoker
- start/cancel session
- temporary debug overlay
- exact-success application path only

Exit criterion: a vanilla offer can be intercepted, confirmed through a dummy ritual, then applied exactly once with correct costs.

### Milestone 2 — Rune game

- radial overlay
- four remappable inputs
- Tap/Hold/Chord
- deterministic generator
- scoring
- result overlay
- classic-lanes accessibility layout

### Milestone 3 — Difficulty and failure

- complexity calculator
- arcane-value calculator
- downgrade resolver
- configurable thresholds
- failure costs
- offer difficulty preview

### Milestone 4 — Datapacks and API

- reload listeners
- profile schema
- pattern-set schema
- precedence resolver
- debug commands
- adapter API/events

### Milestone 5 — Dedicated compatibility

- Apotheosis adapter
- Easy Magic adapter
- curated profiles for popular ordinary-enchantment mods
- Universal Enchants behavior tests
- Enchantment Descriptions coexistence tests

### Milestone 6 — Hardening/polish

- packet fuzz/validation
- disconnect/menu-change handling
- performance profiling
- sound/particles
- translation keys
- accessibility audit
- dedicated server test
- release compatibility matrix

---

## 29. Performance Requirements

The ritual should be cheap compared with normal rendering/gameplay.

Server:

- no per-tick registry scans
- no per-tick datapack selector evaluation; compile profiles on reload
- no world-wide ticking objects
- session manager keyed by player UUID
- score a ritual only from bounded input arrays
- remove expired sessions promptly

Client:

- precompute pattern event positions where practical
- reuse textures/buffers
- avoid allocation-heavy streams in per-frame rendering
- keep particle count bounded
- no shader requirement

A server with no active rituals should have effectively zero ongoing tick cost beyond negligible bookkeeping.

---

## 30. Localization

All player-facing text must use translation keys.

Suggested key families:

```text
immersive_enchanting.ritual.*
immersive_enchanting.result.*
immersive_enchanting.judgement.*
immersive_enchanting.abort.*
immersive_enchanting.config.*
immersive_enchanting.command.*
immersive_enchanting.compat.*
```

Do not bake English text into packets; send enums/reason IDs and localize on the client.

---

## 31. Suggested Initial Visual/Audio Assets

Minimum art set:

- 4 distinct binding-rune symbols
- incoming tap rune
- hold head/body/tail
- chord connector
- binding ring/sigil
- resonance ring states
- crack/failure mask
- compact 0–5 complexity icons

Minimum sounds:

- ritual begin
- rune perfect
- rune good
- rune miss/break
- hold sustain loop or soft pulse
- ritual success
- ritual partial/frayed
- ritual failure

Use Minecraft-style enchanting ambience as inspiration, not copied third-party assets.

---

## 32. Recommended Defaults and Design Decisions

Unless testing strongly argues otherwise, use these as the initial product decisions:

- Keep vanilla/modded offer generation unchanged.
- Intercept at offer selection instead of replacing the table.
- Keep hidden enchantments hidden until resolution.
- Show only a 0–5 rune complexity preview.
- Use four binding inputs.
- Radial layout is default; classic lanes is an accessibility alternative.
- Full reward begins at score 80.
- Score 65–79 gives a meaningfully degraded but usually useful result.
- Score 50–64 gives a weak result.
- Below 50 gives no enchantment by default.
- Any resolved attempt consumes normal resources by default.
- Perfect play gives prestige, not extra enchantment power.
- Unknown custom enchantments always use automatic fallback profiling.
- Dedicated code support is reserved for mods that change enchanting mechanics, not merely mods that register enchantments.

---

## 33. Known Risks and Mitigations

### Risk: another mod replaces/intercepts `EnchantmentScreen`

**Mitigation:** only intercept recognized screens/menus; adapter architecture; avoid screen replacement.

### Risk: another mod changes the private enchantment-list method

**Mitigation:** narrow invoker; integration tests; dedicated adapter for major overhaul mods; fail safe rather than reimplement blindly.

### Risk: high latency makes a timing game feel unfair

**Mitigation:** play locally with deterministic pattern; submit relative timestamps; server reconstructs rather than requiring each input to arrive in real time.

### Risk: client can fabricate perfect timestamps

**Mitigation:** server-side validation prevents accidental/trivial spoofing but do not promise strong anti-cheat. Competitive anti-cheat is out of scope.

### Risk: failure feels too punitive

**Mitigation:** configurable failure-cost mode, generous default windows, full reward at 80 rather than requiring perfection, practice/debug mode during tuning.

### Risk: custom enchantments use extreme/unusual level scales

**Mitigation:** normalized level calculation, hard clamps, datapack overrides, namespace profiles, debug explain command.

### Risk: difficulty leaks hidden multi-enchant information

**Mitigation:** this is an intentional gameplay clue, configurable via `previewRitualDifficulty`.

### Risk: an overhaul mod uses a non-standard XP economy/application path

**Mitigation:** adapter owns cost/apply behavior; never force vanilla mutation semantics onto an adapter that claims the menu.

---

## 34. Implementation Notes for the Coding Agent

1. Build the vanilla/Forge path first. Do not begin with Apotheosis.
2. Prove exact offer capture and exact-success application before implementing failure outcomes.
3. Keep all ritual math free of Minecraft rendering classes so it is unit-testable.
4. Keep the server authoritative plan out of client memory until the result packet.
5. Make deterministic generators pure functions wherever possible.
6. Do not use registry scans in hot loops.
7. Treat optional mod classes as unsafe to reference until their compatibility module is conditionally loaded.
8. Favor small Mixins/Invokers over broad method overwrites.
9. Never reimplement a mod's enchantment-validity rules if its own offer generator can be queried.
10. Add a synthetic test enchantment with deliberately unusual properties early; this prevents accidentally designing only for vanilla.
11. Add debug logging for adapter choice, profile resolution, complexity components, and session transitions before compatibility work begins.
12. Separate **enchantment selection**, **ritual difficulty**, **player performance**, and **final outcome** into distinct layers. This separation is what makes generic compatibility practical.

---

## 35. Definition of the Core Architecture in One Sentence

**Immersive Enchanting should let the existing enchanting system decide *what magic was rolled*, let the rune ritual decide *how successfully the player binds that magic*, and let a small adapter layer decide *how that enchanting system charges and applies the final result*.**

