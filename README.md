<img src="docs/logo.svg" width="96" alt="TrueTap logo">

# TrueTap Keyboard

An Android keyboard modelled on Gboard. Its main job is to type the key you were aiming for, even when
your thumb lands on the one next to it.

Tuned first for the **Pixel 9** (1080 × 2424, 420 dpi, 6.3"). The default key height (56 dp) and spacing
were chosen for that screen. Larger and smaller screens will work, but they haven't been tuned yet.

## Features

| Gboard feature | TrueTap |
| --- | --- |
| Accurate typing | Probabilistic key detection, a learned per-thumb touch model, and a dictionary decoder with autocorrect (details below) |
| Number row | On by default (Settings → Layout → Number row). With it off, long-press the top row for digits |
| Languages | English, Español, Français (AZERTY), Deutsch (QWERTZ), Italiano, Português. Each has its own dictionary, layout and accents. Switch with the globe key or by long-pressing space |
| Glide typing | Slide across the letters to type a word |
| GIFs & stickers | GIPHY search, trending and quick categories. Inserted as real GIFs in apps that accept them (Messages, WhatsApp…). Other apps get a link |
| Emoji | Categories plus recents |
| Floating keyboard | Toolbar ⊞ → Floating. Drag the bar underneath to move it; tap ⤢ to dock it |
| One-handed mode | Toolbar → One-handed. The side buttons switch sides or go back to full width |
| Voice typing | Mic in the toolbar. Continuous dictation through the system speech recogniser |
| Definition / thesaurus | Toolbar 📖 / Aa→ looks up the word at the cursor (or the selected word). Tap a synonym to swap it in |
| Clipboard | History kept for an hour, with pinning |
| Gestures | Swipe on space moves the cursor, swipe left from ⌫ deletes whole words, long-press keys for accents and symbols |
| Other | Key previews, haptics, auto-caps, double-space period, Material You colours, light/dark theme |

## Installing on your Pixel 9

**Option A – download a build (no tools needed)**
1. Open the repo on GitHub → **Actions** → latest **Build APK** run → download the `truetap-keyboard-apk` artifact.
2. Unzip it and copy `app-release.apk` to the phone, then open it. You'll need to allow installs from your browser or file manager.

**Option B – build it yourself**
1. Open the project in Android Studio (Ladybug or newer), let Gradle sync, plug in the Pixel with USB debugging on, and press **Run**.
2. Or from a terminal: `./gradlew installDebug`

**Then, on the phone**
1. Open **TrueTap Keyboard** from the app drawer.
2. Tap **1. Enable TrueTap** and turn it on.
3. Tap **2. Switch to TrueTap**.
4. Recommended: tap **Calibrate typing** and type the six short phrases. It takes about two minutes and teaches TrueTap where *your* thumbs actually land.
5. For GIFs and stickers, paste a free GIPHY API key under *GIFs & stickers*. You can get one at
   developers.giphy.com → Create an App → API. To bake a key into your own builds, put `GIPHY_API_KEY=...` in
   `local.properties` or set it as a repository secret for CI.
6. For voice typing, tap **Allow microphone**.

While you test, turn on **Show touch points**. Dots then mark where each tap actually landed.

## How it avoids hitting the wrong key

Gboard's typical failure is that one or two slightly-off taps per word turn into a different word. Or the
autocorrect "fixes" it into something else. TrueTap treats every tap as uncertain evidence, not as a key press:

1. **Touch model per key** (`engine/SpatialModel.kt`). Each tap gets a likelihood for every nearby key from
   a 2-D Gaussian. The Gaussian is centred where you tend to hit that key, not on the key's printed
   centre. The offsets are learned per key and per layout from words you type and keep, and from the
   calibration screen.
2. **Language-aware hit boxes** (`Decoder.nextCharPrior` / `resolveTap`). A tap on the border between two
   keys goes to whichever letter makes sense after what you've already typed. The nudge has a hard cap, so
   a tap clearly inside a key always types that key.
3. **Word decoder** (`engine/Decoder.kt`). This is a beam search over a 40–45k-word dictionary (per
   language), scoring how well each word explains *all* the taps together. It tolerates a missed, extra or
   swapped letter on longer words and adds apostrophes for you ("dont" → "don't"). It also learns your own
   words and word pairs.
4. **Careful autocorrect** (`engine/TypingEngine.kt`). It only replaces a real word you typed when the
   evidence is strong, and it never touches passwords, emails or URLs. **Backspace straight after a
   correction undoes it** and remembers not to do it again.
5. **Fat-finger specific fixes**:
   - Hitting `.` at its edge next to the space bar, then carrying on typing → turned back into a space
     ("hello.world" → "hello world").
   - Hitting `b`/`n`/`m` just above the space bar → the word can be split ("thisnis" → "this is").
   - Rolling from one key to the next with two thumbs keeps the letters in order.
   - Palm touches are ignored.

### Measured on simulated sloppy typing

`tools/sim.sh` (and the `SimulationTest` unit test) types 80 everyday messages with simulated thumb scatter
on Pixel 9 key geometry. It compares the key under the thumb with what TrueTap types:

| Typist (tap scatter, in key widths) | Words right: key under thumb | Words right: TrueTap |
| --- | --- | --- |
| Careful (0.25) | 83.3% | **98.9%** |
| Average thumb (0.35 + offset) | 44.9% | **96.8%** |
| Fat finger (0.45 + offset) | 21.5% | **87.5%** |
| Very sloppy (0.55 + offset) | 11.8% | **68.8%** |

With a strong personal offset, learning adds a few more points: 90.5% → 94.2% after two passes. Uncommon
real words typed carefully were all left alone (no over-correction). These numbers come from a simulator,
not from real thumbs. Calibrating on the Pixel and using the keyboard for a while are what tune it to you.

## Project layout

```
app/src/main/java/com/truetap/keyboard/
  engine/     Pure-Kotlin typing brain (no Android deps, unit-tested): dictionary trie, touch model,
              decoder, autocorrect policy, glide decoder, user history
  ime/        The InputMethodService, keyboard view, layouts, toolbar, theme, icons, media provider
  ime/panels/ Emoji, GIF/sticker, clipboard, definition/thesaurus, voice and "all tools" panels
  net/        GIPHY, dictionaryapi.dev, Datamuse, image loading
  settings/   Settings and calibration screens
app/src/main/assets/dict/   Word lists (built by tools/build_dictionaries.py)
tools/sim.sh                Accuracy simulator
```

The app uses only the Android framework and the Kotlin standard library (no AndroidX), so the APK stays small.

## Known limitations / next steps

- **No built-in next-word model.** Predictions after a space, and context for close calls like "in"/"on",
  come from your own history, so they get better with use. A compact n-gram model would close that gap
  with Gboard.
- **German nouns:** the word lists are lower-case, so German nouns are not capitalised automatically.
- **Definitions** are reliable in English; dictionaryapi.dev coverage for other languages is patchy.
  Synonyms are available in English and Spanish.
- GIF search needs your own GIPHY key (GIPHY requires one per app).
- Tuning beyond the Pixel 9 (tablets, foldables, small phones) is the next step. The engine works in
  key-width units, so learned offsets carry over between sizes.

## Contributors

- **Piyush Banerjee**
- **TerraTales Media Entertainment**

See [AUTHORS](AUTHORS) for the full list.

## Credits

Word frequencies come from [wordfreq](https://github.com/rspeer/wordfreq) (CC BY-SA 4.0). Other sources:
definitions from [Free Dictionary API](https://dictionaryapi.dev), synonyms from
[Datamuse](https://www.datamuse.com/api/), GIFs and stickers from [GIPHY](https://developers.giphy.com).
Everything TrueTap learns about your typing stays on the device.
