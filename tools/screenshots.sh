#!/usr/bin/env bash
# Captures promo screenshots of the real debug build on an emulator set to Pixel 9 geometry.
# Run by .github/workflows/screenshots.yml; needs one booted device on adb.
# Taps are real touch events on real key positions (reported by DebugDump in debug builds).
set -o pipefail

OUT=screenshots
PKG=com.truetap.keyboard
IME=$PKG/.ime.TrueTapIME
mkdir -p "$OUT"
RANDOM=7

sh() { adb shell "$@"; }
shot() { sleep 1.2; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
prefs() { sh am broadcast -n $PKG/.demo.DemoReceiver "$@" > /dev/null; sleep 2.5; }
demo() { sh am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }

# Always keep whatever we captured plus diagnostics.
on_exit() {
    adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
    adb shell dumpsys input_method > "$OUT/input_method.txt" 2>/dev/null
    echo "===== crash buffer ====="
    adb logcat -d -b crash 2>/dev/null | tail -80
    echo "===== keyboard-related errors ====="
    adb logcat -d 2>/dev/null | grep -iE "truetap|InputMethodManagerService|AndroidRuntime" | grep -iE "exception|error|fatal|died|crash|reset|switch" | tail -60
    exit 0
}
trap on_exit EXIT

declare -A X Y W
mark() { adb logcat -c; }
refresh() {
    sleep 1.5
    X=(); Y=(); W=()
    while IFS='|' read -r _ name x y w; do
        [ -n "$name" ] || continue
        X["$name"]=$x; Y["$name"]=$y; W["$name"]=$w
    done < <(adb shell run-as com.truetap.keyboard cat files/ttdump.txt | tr -d '\r' | grep '|')
    echo "positions known: ${#X[@]}"
}
# If the keyboard isn't up, tap the message box (bottom of the screen) to bring it back.
ensure_keyboard() {
    refresh
    if [ "${#X[@]}" -lt 10 ]; then
        echo "keyboard not visible; tapping message field"
        sh dumpsys input_method | grep -E "mInputShown|mCurId|mIsInputViewShown" | head -5
        sh input tap 540 2270
        refresh
    fi
}
tap() {
    local name=$1 dx=${2:-0} dy=${3:-0}
    if [ -z "${X[$name]:-}" ]; then echo "!! no position for '$name'"; return; fi
    sh input tap $((X[$name] + dx)) $((Y[$name] + dy))
    sleep 0.2
}
# Types text with taps scattered around each key (like a hurried thumb). $2 = scatter in % of key width.
type_text() {
    local text=$1 spread=${2:-0} i c name w jx jy
    for ((i = 0; i < ${#text}; i++)); do
        c=${text:i:1}
        case "$c" in
            " ") name=space ;;
            *) name=$(echo "$c" | tr '[:upper:]' '[:lower:]') ;;
        esac
        w=${W[$name]:-100}
        jx=0; jy=0
        if [ "$spread" -gt 0 ] && [ "$name" != space ]; then
            local r=$((w * spread / 100))
            jx=$((RANDOM % (2 * r + 1) - r)); jy=$((RANDOM % (2 * r + 1) - r))
        fi
        tap "$name" $jx $jy
    done
}
# Glide through the letters of $1, leaving the finger down; call glide_end to lift.
GX=0; GY=0
glide() {
    local word=$1 i c px py nx ny s
    c=${word:0:1}; px=${X[$c]:-0}; py=${Y[$c]:-0}
    if [ "$px" = 0 ]; then echo "!! no positions for glide"; return; fi
    sh input motionevent DOWN $px $py
    for ((i = 1; i < ${#word}; i++)); do
        c=${word:i:1}; nx=${X[$c]:-$px}; ny=${Y[$c]:-$py}
        for s in 1 2 3 4 5 6; do
            sh input motionevent MOVE $((px + (nx - px) * s / 6)) $((py + (ny - py) * s / 6))
        done
        px=$nx; py=$ny
    done
    GX=$px; GY=$py
}
glide_end() { sh input motionevent UP $GX $GY; sleep 0.8; }
chat() { mark; prefs --es reset 1; ensure_keyboard; }

# ---------------------------------------------------------------- device setup
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
sh wm size 1080x2424
sh wm density 420
sh cmd overlay enable com.android.internal.systemui.navbar.gestural || true
sh ime enable $IME
sh ime set $IME
sh settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi hide
demo network -e mobile show -e level 4 -e datatype 5g -e fully true
demo network -e airplane hide
demo notifications -e visible false
sh cmd uimode night yes
sleep 3

prefs --es number_row true --es show_touch_points false --es mode normal --es theme system \
      --es languages en --es current_language en --es haptics false --es key_preview true
mark
sh am start -W -n $PKG/.demo.DemoActivity
refresh

# ---------------------------------------------------------------- keyboard & accuracy
shot 01_keyboard

prefs --es show_touch_points true
chat
type_text "running lat" 30
shot 02_fat_finger_typing
type_text "e, be there in ten " 30
shot 03_typed_what_you_meant

# ---------------------------------------------------------------- glide typing & accents
prefs --es show_touch_points false
chat
glide "hello"
sleep 0.5
adb exec-out screencap -p > "$OUT/04_glide_typing.png"; echo "captured 04_glide_typing"
glide_end
sh input motionevent DOWN ${X[e]:-540} ${Y[e]:-1800}
sleep 0.9
adb exec-out screencap -p > "$OUT/05_accents.png"; echo "captured 05_accents"
sh input motionevent UP ${X[e]:-540} ${Y[e]:-1800}
sleep 0.5

# ---------------------------------------------------------------- symbols, emoji, tools
chat
mark; tap "?123"; refresh
shot 06_symbols
mark; tap ABC; refresh
mark; tap emojikey; refresh
shot 07_emoji
mark; tap Keyboard; refresh
mark; tap More; refresh
shot 08_all_tools

# ---------------------------------------------------------------- dictionary & thesaurus
chat
type_text "happy"
mark; tap More; refresh
tap Define
sleep 5
shot 09_definition
mark; tap Keyboard; refresh
mark; tap More; refresh
tap Thesaurus
sleep 5
shot 10_thesaurus

# ---------------------------------------------------------------- layouts
mark; prefs --es mode one_right --es reset 1; ensure_keyboard
shot 11_one_handed
mark; prefs --es mode floating --es reset 1; ensure_keyboard
shot 12_floating
prefs --es mode normal

mark; prefs --es languages en,fr --es current_language fr --es reset 1; ensure_keyboard
type_text "bonjour "
shot 13_french_azerty
prefs --es languages en --es current_language en

# ---------------------------------------------------------------- light theme
sh cmd uimode night no
sleep 3
chat
type_text "see you soon "
shot 14_light_theme

# ---------------------------------------------------------------- GIFs & stickers (GIPHY)
sh cmd uimode night yes
sleep 3
chat
mark; tap GIFs; refresh
sleep 6
shot 15_gifs
mark; tap Keyboard; refresh
mark; tap More; refresh
tap Stickers
sleep 6
shot 16_stickers
mark; tap Keyboard; refresh

# ---------------------------------------------------------------- set-up & calibration
sh am start -W -n $PKG/.settings.SettingsActivity
shot 17_setup
sh am start -W -a android.settings.INPUT_METHOD_SETTINGS
shot 18_enable_keyboard
mark
prefs --es launch calibration
sleep 2
refresh
type_text "the quick brown fox ju" 25
shot 19_calibration
ls -la "$OUT"
