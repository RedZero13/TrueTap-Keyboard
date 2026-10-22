#!/usr/bin/env python3
"""Builds the bundled word lists in app/src/main/assets/dict/.

Source: the `wordfreq` package (pip install wordfreq), whose data is CC-BY-SA 4.0
(Wikipedia, subtitles, news, books, web text). Run: python3 tools/build_dictionaries.py

Output format, one word per line, most frequent first:
    <display form>\t<frequency 1..255>
where frequency 255 is the most common word and each step is 11.5/254 nats
(see Dictionary.FREQ_SPAN_NATS in the app).
"""
import math
import os
import unicodedata

import wordfreq

LANGS = {"en": 45000, "es": 40000, "fr": 40000, "de": 40000, "it": 40000, "pt": 40000}
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "dict")
SPAN_NATS = 11.5

EN_SHORT = set("""a i am an as at be by do go he hi if in is it me my no of oh ok on or so to up us we
ah aw eh ha hm uh um yo ya ad ex ox""".split())
EN_CAPITALISED = {
    "i": "I", "i'm": "I'm", "i'll": "I'll", "i've": "I've", "i'd": "I'd",
    "monday": "Monday", "tuesday": "Tuesday", "wednesday": "Wednesday", "thursday": "Thursday",
    "friday": "Friday", "saturday": "Saturday", "sunday": "Sunday", "january": "January",
    "february": "February", "april": "April", "june": "June", "july": "July", "august": "August",
    "september": "September", "october": "October", "november": "November", "december": "December",
    "english": "English", "american": "American", "america": "America", "london": "London",
    "paris": "Paris", "christmas": "Christmas", "google": "Google", "pixel": "Pixel",
    "android": "Android", "iphone": "iPhone", "tv": "TV", "ok": "OK", "usa": "USA", "uk": "UK",
    "youtube": "YouTube", "facebook": "Facebook", "instagram": "Instagram", "whatsapp": "WhatsApp",
    "europe": "Europe", "india": "India", "china": "China", "canada": "Canada", "australia": "Australia",
    "easter": "Easter", "halloween": "Halloween", "god": "God", "spanish": "Spanish", "french": "French",
    "german": "German", "italian": "Italian", "british": "British", "new york": "New York",
}
# Elided articles/pronouns that wordfreq splits off ("l'homme" -> "l", "homme").
ELISIONS = {
    "fr": ["l", "d", "j", "c", "n", "s", "m", "t", "qu", "jusqu", "lorsqu", "puisqu"],
    "it": ["l", "un", "dell", "all", "nell", "dall", "sull", "c", "d", "po", "quell", "quest"],
}
VOWELS = set("aeiouyàáâãäåèéêëìíîïòóôõöùúûüýÿæœ")


def is_word(w):
    if not w or not w[0].isalpha():
        return False
    return all(ch in "'-" or unicodedata.category(ch).startswith("L") for ch in w)


def build(lang, limit):
    zipf = {}
    for w in wordfreq.top_n_list(lang, limit * 2):
        if not is_word(w) or w.endswith("-") or w.endswith("'"):
            continue
        z = wordfreq.zipf_frequency(w, lang)
        short = len(w) <= 2
        if lang == "en" and short and w not in EN_SHORT:
            continue
        if lang != "en" and short and z < 4.3:
            continue
        if len(w) > 2 and not (set(w) & VOWELS) and z < 4.5:
            continue
        zipf[w] = z
    for e in ELISIONS.get(lang, []):
        if e in zipf:
            zipf[e + "'"] = zipf.pop(e)
    ranked = sorted(zipf.items(), key=lambda kv: -kv[1])[:limit]
    zmax, zmin = ranked[0][1], ranked[-1][1]
    # zipf is log10 per billion words; express the range in natural-log units.
    span = (zmax - zmin) * math.log(10)
    path = os.path.join(OUT_DIR, lang + ".txt")
    with open(path, "w", encoding="utf-8") as out:
        for w, z in ranked:
            nats_below_top = (zmax - z) * math.log(10)
            freq = max(1, 255 - int(round(254 * nats_below_top / SPAN_NATS)))
            display = EN_CAPITALISED.get(w, w) if lang == "en" else w
            out.write(f"{display}\t{freq}\n")
    print(f"{lang}: {len(ranked)} words, data span {span:.1f} nats -> {path}")


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    for lang, limit in LANGS.items():
        build(lang, limit)
