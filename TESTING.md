# Manual testing guide

There is no automated test suite, so the only way to verify a change is to run the plugin on a
live server. This document describes every plugin mechanic and how to exercise it by hand.

Requirements: Paper 1.21.5+ (or Folia), Java 21.

Every value below was read from the source. If they ever disagree, trust the code.

Русская версия: [TESTING.ru.md](TESTING.ru.md).

---

## Test server setup

Edit `plugins/BreweryX/config.yml` first, or half of these mechanics take hours to observe:

| Key | Default | Use for testing | Why |
|---|---|---|---|
| `debug` | `false` | `true` | `BreweryRunnable` logs and timings |
| `autosave` | `10` | `1` | autosave once per minute |
| `agingYearDuration` | `20` | `1` | one aging year takes 1 minute instead of 20 |
| `enableKickOnOverdrink` | `false` | `true` | lets you test the overdrink kick |
| `storage.type` | `SQLITE` | `FLATFILE` and `SQLITE` in turn | the two backends run different code |

Give yourself `brewery.*`.

### Scheduler timings

| Task | Interval | Delay | Work |
|---|---|---|---|
| `BreweryRunnable` | 1200 ticks (60 s) | 650 ticks (32.5 s) | cauldrons, barrels, MC barrels, sobering up, autosave check |
| `DrunkRunnable` | 120 ticks (6 s) | 120 ticks | nausea, puke chances |
| `CauldronParticles` | 1 tick | 0 | cauldron particles (not on Folia) |

Everything that happens "per minute" only advances on the `BreweryRunnable` tick. Nothing
happens during the first 32.5 seconds after startup — that is expected.

---

## 1. Cauldron (cooking)

**How it works.** A cauldron with water (level 1–3) and a heat source below it: `FIRE`,
`SOUL_FIRE`, `MAGMA_BLOCK`, `LAVA`, or a lit `CAMPFIRE`/`SOUL_CAMPFIRE`. No cauldron object
exists until the first ingredient is added. Right-clicking with an ingredient consumes one item
(or the whole stack if you hold more than one). The `state` counter is the cook time in minutes;
it increases by 1 per `BreweryRunnable` tick, **only while the chunk is loaded and the fire is
still burning**. Every ingredient added rolls the counter back by 1. Right-click with a glass
bottle to extract: the water level drops by one, and the cauldron disappears on the last.

**How to test:**

1. Cauldron + water + campfire below. Right-click 6× with wheat (the Beer recipe).
2. Particles appear immediately and shift colour as cooking progresses (redrawn every 15 ticks).
3. After 8 minutes, right-click with a glass bottle → a "Beer" potion with quality stars.
4. Negative cases:
   - put the fire out mid-cook → the counter freezes;
   - walk out of the chunk → the counter freezes;
   - add a junk ingredient (rotten flesh) → quality drops;
   - bottle too early → "Thick Brew" with no recipe.

Fast path, no waiting: `/brew simulate -c 8 wheat/6`.

---

## 2. Distilling

**How it works.** A brewing stand: blaze powder as fuel, **`GLOWSTONE_DUST` is required in the
top slot**, and cooked brews in the three bottom slots. Run duration comes from the recipe
(`distillTime`, configured in seconds), not from the vanilla 400 ticks. Each run increments
`distillRuns` by 1 and recalculates quality and alcohol.

Distill quality = `10 − |required_runs − actual_runs|`. Over-distilling hurts exactly as much as
under-distilling.

**How to test:**

1. Cook Vodka (10 potatoes, 15 minutes) — it needs exactly 3 runs and no aging.
2. Distil one run at a time and read the lore each time: the "X times Distilled" line and the
   alcohol value must change.
3. Quality peaks on run 3. Do a 4th — quality must drop.
4. Remove the glowstone dust — distilling a custom brew must not start at all.

Fast path: `/brew distill 3` on the item in hand.

---

## 3. Barrels (aging)

### Small barrel

8 stairs of one wood type stacked 2×2×2. The **sign itself is the spigot**: place it on the
front face and write `Barrel` on any line. Permission: `brewery.createbarrel.small`.

### Large barrel

34 blocks: 4 sections long, 3×3 cross-section. The cross-section corners are stairs, everything
else is planks. The two middle sections have a hollow centre (y=1, z=0); the end sections are
filled. A **fence** at the front is the spigot. Place the sign in the same column as the fence,
within −2 to +1 blocks vertically (easiest: right on top of the fence), and write `Barrel`.
Permission: `brewery.createbarrel.big`.

Large barrel cross-section, viewed from the end:

```
y=2   stairs   planks   stairs
y=1   planks   (empty)  planks     ← empty only in sections 2 and 3
y=0   stairs   planks   stairs
      z=-1      z=0      z=+1
```

The barrel type is decided by the spigot: a sign spigot makes it small, a fence spigot makes it
large. The `Barrel` keyword is mandatory while `requireKeywordOnSigns: true` (the default).

### Wood types

`BIRCH`, `OAK`, `JUNGLE`, `SPRUCE`, `ACACIA`, `DARK_OAK`, `CRIMSON`, `WARPED`, `MANGROVE`,
`CHERRY`, `BAMBOO`, `CUT_COPPER` (including the waxed and oxidised variants), `PALE_OAK`, plus
`ANY`. Wood affects quality: the recipe names the wood it wants, and the penalty is based on the
"distance" between wood groups (OAK/DARK_OAK/PALE_OAK are neighbours, copper is far from
everything).

### Time

`time += 1 / agingYearDuration` per minute. At the default of 20 that is 20 real minutes per
aging year.

**How to test:**

1. Build a small oak barrel and right-click the sign — the inventory opens. Put in Mead
   (it wants 4 years of oak).
2. With `agingYearDuration: 1`, wait 4 minutes and take it out: the lore shows "4 Years" and
   high quality.
3. Build a large spruce barrel and age the same Mead there — quality must be noticeably lower
   (wrong wood).
4. Regressions worth re-checking after any optimisation work:
   - break one barrel block → the barrel breaks and **drops its contents**;
   - blow a barrel up with a creeper or TNT;
   - push a barrel block with a piston;
   - unload a world containing a barrel, wait for an autosave, restart → **the barrel must
     still be there**.

Fast path: `/brew age oak 4`.

---

## 4. Vanilla barrels (MCBarrel)

A vanilla `barrel` block also ages brews, using the barrel's own wood type, for at most
`maxBrewsInMCBarrels` (default 6) of the first brews inside. Time is measured between opening
and closing. Toggled by `ageInMCBarrels` (default `true`).

**How to test:** put 8 brews into a vanilla barrel, close it, wait, reopen — only the first 6
should have aged.

---

## 5. Sealing table

**How it works.** The recipe is 2 glass bottles on top and 4 planks (any type) below, in a 2×3
grid. It produces a **`SMOKER`** with a custom name and a PDC tag; the block is configurable via
`sealingTableBlock`. Put a brew in its inventory, and after 20 ticks in the same slot it gets
sealed:

- `stripped` — ingredients, age and wood data are wiped;
- `immutable` — the brew can no longer be modified;
- `unlabeled` — only stars remain in the lore, no numbers;
- quality is rounded to an even number (minimum 2), alcohol is recalculated once.

The point: two identical sealed brews have **identical NBT**, so they stack and can be traded in
shop plugins.

**How to test:** seal two identical brews and try to stack them — they must stack. Then put a
sealed brew in a barrel and wait — its age must **not** change.

`/brew seal` opens the GUI without crafting the block.

---

## 6. Quality, recipes and lore

Final quality (1–10):

- recipe requires aging → `(ingredients + cook time + wood + age) / 4`;
- it does not → `(ingredients + cook time) / 2`.

Each term is out of 10. The tolerance for deviation depends on `difficulty`:
`allowedDiff = round((11 − difficulty) × (amount / 10))`. Difficulty 1 is very forgiving,
8+ demands near-exact values.

Stars: `⭑` full, `⭒` half, 5 maximum. Colour: >8 green, >6 yellow, >4 orange, >2 red,
≤2 dark red.

### Default recipes for testing

| Recipe | Ingredients | Cook | Distill | Wood | Age | Alc | Diff |
|---|---|---|---|---|---|---|---|
| Beer | wheat ×6 | 8 min | — | any | 3 y | 6 | 1 |
| Wheatbeer | wheat ×3 | 8 min | — | birch | 2 y | 5 | 1 |
| Darkbeer | wheat ×6 | 8 min | — | dark oak | 8 y | 7 | 2 |
| Mead | sugar cane ×6 | 3 min | — | oak | 4 y | 9 | 2 |
| Apple Mead | sugar cane ×6, apple ×2 | 4 min | — | oak | 4 y | 11 | 4 |
| Red Wine | sweet berries ×5 | 5 min | — | any | 20 y | 8 | 4 |
| Apple Cider | apples ×14 | 7 min | — | any | 3 y | 7 | 4 |
| Vodka | potatoes ×10 | 15 min | 3× | any | — | 20 | 4 |
| Rum | sugar cane ×18 | 6 min | 2× (30 s) | oak | 14 y | 30 | 6 |
| Gin | wheat ×9, blue flowers ×6, apple ×1 | 6 min | 2× | any | — | 20 | 6 |
| Whiskey | wheat ×10 | 10 min | 2× (50 s) | spruce | 18 y | 26 | 7 |
| Apple Liquor | apples ×12 | 16 min | 3× (60 s) | acacia | 6 y | 14 | 5 |
| Absinthe | short grass ×15 | 3 min | 6× (80 s) | any | — | 42 | 8 |
| Coffee | cocoa beans ×12, milk ×2 | 2 min | — | any | — | **−6** | 3 |
| Potato Soup | potatoes ×5, short grass ×3 | 3 min | — | any | — | 0 | 1 |

Coffee's negative alcohol makes it a convenient test for a sobering brew.

### Brew identity

Brew data lives in the item's PDC in binary form, XOR-scrambled with the save seed when
`enableEncode: true`. Whenever serialization changes, **verify that a brew created by the
previous build still decodes**.

Diagnostics: `/brew debuginfo` on the item in hand prints the full quality breakdown.

---

## 7. Drunkenness

Drinking does `drunkenness += alcohol`, and `quality` accumulates weighted by alcohol. Sobering
up is **2 points per minute** (tunable via the `brewery.recovery.<N>` permission); sensitivity
comes from `brewery.sensitive.<N>`.

| Level | Effect |
|---|---|
| ≥ 10 | stumbling: a random velocity push 1–2× per second (`stumblePercent`) |
| > 30 | nausea, reapplied every 6 seconds |
| ≥ 30 | teleport to a wakeup point on login (`enableWake`) |
| ≥ 70 | 10% puke chance every 6 s; 40% chance of being denied login (`enableLoginDisallow`) |
| ≥ 80 | 15% puke chance |
| ≥ 90 | 20% puke chance; 60% chance of being denied login |
| > 100 | overdrink: kicked if `enableKickOnOverdrink: true`, otherwise 60–120 puke items |

Chat distortion is **not** gated on a single threshold: every rule in `words.yml` carries its own
`alcohol` value and fires when `alcohol <= drunkenness`, subject to its own `percentage`.
Chat, the commands listed in `distortCommands` and — with `distortSignText: true` — sign text are
all intercepted. Text between the `distortBypass` markers (default `*,*` and `[,]`) is left alone.

**How to test** without actually drinking:

```
/brew set <name> 15      → stumbling starts
/brew set <name> 35      → stumbling plus nausea on screen
/brew set <name> 75      → chat distortion and puking join in
/brew set <name> 101     → kick or a pile of puke
/brew info               → current level and quality
```

Also check:

- sobering up: set 20, wait 5 minutes → should be around 10;
- the `brewery.bypass.chatdistort` permission → chat is not distorted.

---

## 8. Hangover, login/logout, wakeup points

**Hangover** triggers on login when `drunkenness < 10` but `offlineDrunk > 20`. It applies
Slowness and Hunger for `offlineDrunk × 25 × hangoverQuality` ticks, where hangover quality is
inverted (`11 − quality`) — cheap booze gives a worse hangover. It lasts `hangoverDays` days
(default 7).

**Wakeup points** are safe locations a drunk player is teleported to on login. A point is valid
only if the block and the block above it are non-solid. Two random points in the same world are
picked and the nearest valid one wins.

```
/brew wakeup add          → set a point here
/brew wakeup list         → list them
/brew wakeup check 3      → check point 3 (teleports you to it)
/brew wakeup remove 3     → delete it
/brew wakeup cancel       → cancel the active one
```

**Scenario:** set a point, `/brew set <name> 50`, log out, log back in → you should be
teleported. With `offlineDrunk > 60` and `enableHome: true` you get sent home instead.

Check `brewery.bypass.logindeny`, `brewery.bypass.overdrink` and `brewery.bypass.teleport`
separately — each must disable its corresponding behaviour.

---

## 9. Drain items

Defaults: `BREAD/4`, `MILK_BUCKET/2` — eating bread removes 4 drunkenness. The format is
`MATERIAL/STRENGTH`.

**How to test:** `/brew set <name> 30`, eat bread, `/brew info` → should read 26. Then add your
own item to `drainItems`, run `/brew reload` **without restarting**, and confirm the new item
works. The drain item map is cached and only invalidated by `/brew reload`.

---

## 10. Storage and autosave

The default is **SQLite**, file `brewery-data`, table prefix `brewery_`. Tables and sections:
`barrels`, `cauldrons`, `players`, `wakeups`, `misc`. Supported backends are `FLATFILE`,
`SQLITE`, `MYSQL` and `POSTGRESQL`.

> **The thing to know while testing:** saving is a **full rewrite**. The plugin does no dirty
> tracking — it writes the entire contents of its in-memory maps every time. Anything dropped
> from memory for any reason disappears from storage on the next autosave.

**How to test:**

1. `FLATFILE`: create 3 barrels, 2 cauldrons and a drunk player. Run `/brew data save`, stop the
   server, inspect `data.yml` — is everything there?
2. **Deletion round-trip:** delete the **last** barrel, wait for an autosave, restart.
   It must stay deleted.
3. Switch to `SQLITE` and restart twice — the data must survive both restarts.
4. **Serializer compatibility:** run the new build against a database written by the previous
   version. Everything must decode.
5. `/brew showstats` for a quick sanity check on the counters.

---

## 11. Command reference

Root command `/breweryx`, aliases `/brewery` and `/brew` (configurable).

| Command | Effect |
|---|---|
| `/brew create <recipe> [quality] [player]` | give a brew; quality accepts a range such as `5-8` |
| `/brew give …` | alias for `create` |
| `/brew drink <recipe> [quality] [player]` | simulate drinking |
| `/brew set <player> <drunkenness> [quality]` | set drunkenness directly |
| `/brew info [player]` | current state |
| `/brew distill [runs]` | distil the brew in hand |
| `/brew age <wood> <years>` | age the brew in hand |
| `/brew simulate -c 8 -d 2 -a oak 4 wheat/6` | full calculation with no waiting |
| `/brew debuginfo [recipe]` | quality breakdown for the brew in hand |
| `/brew seal` | open the sealing GUI |
| `/brew copy [N]` | copy the brew in hand |
| `/brew delete` | destroy the brew in hand |
| `/brew static` | freeze/unfreeze aging |
| `/brew unlabel` | strip part of the lore |
| `/brew puke [player] [N]` | make someone puke |
| `/brew wakeup add\|list\|remove\|check\|cancel` | wakeup points |
| `/brew data save\|reload` | force a save / reinitialise storage |
| `/brew reload` | reload configs |
| `/brew reloadaddons confirm` | reload addons |
| `/brew itemname` | material name of the held item (for writing recipes) |
| `/brew showstats` | server statistics |
| `/brew version` | version and loaded addons |

Commands that require an item in hand: `unlabel`, `copy`, `delete`, `static`, `distill`, `age`,
`debuginfo`, `itemname`.

---

## 12. Priority regression checklist

Five scenarios that catch the most breakage. Run them before every release.

1. **World unload.** A world with barrels, cauldrons and wakeup points → unload → wait for an
   autosave → restart. Nothing may disappear.
2. **Delete the last barrel** → autosave → restart. It must not come back.
3. **Barrel integrity check.** Build 5+ barrels, then break a block in one of them in a way the
   plugin notices via its background scan rather than an event, and wait a couple of minutes —
   the barrel must break.
4. **`/brew reload`** → drain items and the barrel inventory title pick up the new config.
5. **Old database on a new build** — a brew written by the previous serializer still decodes.
