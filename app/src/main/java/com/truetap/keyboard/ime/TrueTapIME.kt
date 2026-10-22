package com.truetap.keyboard.ime

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.truetap.keyboard.Brain
import com.truetap.keyboard.Languages
import com.truetap.keyboard.Prefs
import com.truetap.keyboard.engine.Candidate
import com.truetap.keyboard.engine.GestureDecoder
import com.truetap.keyboard.engine.Tap
import com.truetap.keyboard.engine.TypingEngine
import com.truetap.keyboard.ime.panels.ClipboardPanel
import com.truetap.keyboard.ime.panels.EmojiPanel
import com.truetap.keyboard.ime.panels.LookupPanel
import com.truetap.keyboard.ime.panels.MediaPanel
import com.truetap.keyboard.ime.panels.MorePanel
import com.truetap.keyboard.ime.panels.VoicePanel
import com.truetap.keyboard.net.Giphy
import com.truetap.keyboard.net.ImageLoader
import com.truetap.keyboard.net.Net
import com.truetap.keyboard.settings.CalibrationActivity
import com.truetap.keyboard.settings.SettingsActivity
import java.io.File

/**
 * The TrueTap keyboard. Every letter tap goes through the [TypingEngine] so that what gets typed is
 * what you were aiming for, not just the key your thumb happened to touch.
 */
class TrueTapIME : InputMethodService(), KeyboardView.Listener, SuggestionBar.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var theme: KbTheme
    private lateinit var images: ImageLoader
    private lateinit var clipboard: ClipboardStore
    private var root: InputRoot? = null
    private lateinit var bar: SuggestionBar
    private lateinit var content: FrameLayout
    private lateinit var kbView: KeyboardView
    private var viewDirty = true

    // Editor state
    private var editor: EditorInfo? = null
    private var corrections = true
    private var learning = true
    private var urlOrEmail = false
    private var numeric = false
    private var phone = false
    private var selStart = 0
    private var selEnd = 0

    // Keyboard state
    private enum class Page { ALPHA, SYMBOLS, SYMBOLS2, NUMPAD }
    private var page = Page.ALPHA
    private var lang = "en"
    private var shiftAuto = false
    private var lastShiftTap = 0L
    private var returnToAlphaOnSpace = false

    // Typing state
    private val composer = Composer()
    private var engine: TypingEngine? = null
    private var engineVersion = -1
    private var engineLang = ""
    private var gesture: GestureDecoder? = null
    private var gestureAlternates: List<String> = emptyList()
    private var suggestionsShown: List<String> = emptyList()

    /** What the last word commit inserted, so backspace can undo a correction or re-open the word. */
    private class Commit(val inserted: String, val word: String, val literal: String, val taps: List<Tap?>,
                         val capsFirst: Boolean, val corrected: Boolean, val gesture: Boolean)
    private var lastCommit: Commit? = null
    /** Word + taps to learn from once the user moves on without fixing it. */
    private var pendingLearn: Pair<String, List<Tap>>? = null
    private var autoSpace = false
    private var lastSpaceTime = 0L
    private class Punct(val ch: String, val nearSpace: Boolean, val time: Long)
    private var lastPunct: Punct? = null
    private var commitsSinceSave = 0

    // Panels
    private enum class Panel { NONE, MORE, EMOJI, GIF, STICKER, CLIPBOARD, LOOKUP, VOICE }
    private var panel = Panel.NONE
    private var search: Pair<Giphy.Kind, StringBuilder>? = null
    private var lastMediaQuery = HashMap<Giphy.Kind, String>()
    private var voice: VoiceInput? = null
    private var voicePanel: VoicePanel? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!prefs.clipboardHistory) return@OnPrimaryClipChangedListener
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return@OnPrimaryClipChangedListener
        if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString()?.let { clipboard.add(it) }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        images = ImageLoader(this)
        clipboard = ClipboardStore(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener(clipListener)
        lang = prefs.currentLanguage
        Brain.withDictionary(this, lang) { }
    }

    override fun onDestroy() {
        voice?.cancel()
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        getSystemService(ClipboardManager::class.java).removePrimaryClipChangedListener(clipListener)
        Brain.save(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == "float_x" || key == "float_y" || key == "recent_emoji" || key == "current_language") return
        viewDirty = true
    }

    override fun onEvaluateFullscreenMode() = false

    // ------------------------------------------------------------------ views

    override fun onCreateInputView(): View {
        theme = KbTheme.load(this, prefs)
        val r = InputRoot(this, prefs.mode, prefs, theme,
            onExitMode = { setMode("normal") },
            onSwitchSide = { setMode(if (prefs.mode == "one_right") "one_left" else "one_right") })
        bar = SuggestionBar(this, theme)
        bar.listener = this
        content = FrameLayout(this)
        kbView = KeyboardView(this, theme)
        kbView.listener = this
        kbView.overlay = r.overlay
        kbView.keyHeightPx = prefs.keyHeightDp * resources.displayMetrics.density
        kbView.showPreview = prefs.keyPreview
        kbView.glideEnabled = prefs.glideTyping
        kbView.showTouchPoints = prefs.showTouchPoints
        content.addView(kbView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        r.card.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bar.barHeight))
        r.card.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (r.floating) r.card.addView(r.floatingHandle(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (28 * resources.displayMetrics.density).toInt()))
        root = r
        viewDirty = false
        engineVersion = -1
        panel = Panel.NONE
        loadLayout()
        return r
    }

    private fun setMode(mode: String) {
        prefs.mode = mode
        setInputView(onCreateInputView())
        refreshAfterRebuild()
    }

    private fun refreshAfterRebuild() {
        updateShift()
        updateSuggestions()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val r = root ?: return
        if (!r.floating || !isInputViewShown) return
        val h = window?.window?.decorView?.height ?: return
        outInsets.contentTopInsets = h
        outInsets.visibleTopInsets = h
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        val rect = Rect()
        r.cardBoundsInWindow(rect)
        outInsets.touchableRegion.set(rect)
    }

    // ------------------------------------------------------------------ editor lifecycle

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (viewDirty || root == null) setInputView(onCreateInputView())
        editor = info
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val flags = info.inputType and InputType.TYPE_MASK_FLAGS
        numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME
        phone = cls == InputType.TYPE_CLASS_PHONE
        val password = cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
            cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        urlOrEmail = cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val noSuggestions = flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 && variation != InputType.TYPE_TEXT_VARIATION_NORMAL
        corrections = cls == InputType.TYPE_CLASS_TEXT && !password && !urlOrEmail && !noSuggestions &&
            variation != InputType.TYPE_TEXT_VARIATION_FILTER
        learning = !password && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        selStart = info.initialSelStart
        selEnd = info.initialSelEnd
        composer.clear()
        lastCommit = null
        pendingLearn = null
        autoSpace = false
        lastPunct = null
        search = null
        if (lang != prefs.currentLanguage) lang = prefs.currentLanguage
        page = if (numeric || phone) Page.NUMPAD else Page.ALPHA
        voice?.cancel(); voice = null
        showKeyboard()
        loadLayout()
        updateShift()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        voice?.cancel(); voice = null
        applyPendingLearn()
        Brain.save(this)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composer.clear()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (!composer.isEmpty && (candidatesStart < 0 || newSelStart != newSelEnd || newSelEnd != candidatesEnd)) {
            // Either the user moved the cursor away from the word being typed, or this is a late update
            // from before our latest edit (fast typing). The editor's current text tells them apart.
            val ic = currentInputConnection
            val before = ic?.getTextBeforeCursor(composer.length, 0)?.toString()
            val stale = before != null && before == composer.text.toString() && ic.getSelectedText(0).isNullOrEmpty()
            if (!stale) {
                ic?.finishComposingText()
                composer.clear()
                lastCommit = null
                updateSuggestions()
            }
        }
        if (composer.isEmpty && search == null) updateShift()
    }

    // ------------------------------------------------------------------ layout & engine

    private fun enterIcon(): Icon {
        val info = editor ?: return Icon.ENTER
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return Icon.ENTER
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> Icon.SEARCH
            EditorInfo.IME_ACTION_SEND -> Icon.SEND
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> Icon.ARROW_RIGHT
            EditorInfo.IME_ACTION_DONE -> Icon.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> Icon.BACK
            else -> Icon.ENTER
        }
    }

    private fun loadLayout() {
        if (root == null) return
        val language = Languages.get(lang)
        val multi = prefs.languages.size > 1
        val icon = if (search != null) Icon.SEARCH else enterIcon()
        kbView.layout = when (page) {
            Page.ALPHA -> Layouts.letters(lang, language.layout, prefs.numberRow, multi, icon, urlOrEmail)
            Page.SYMBOLS -> Layouts.symbols(1, multi, icon)
            Page.SYMBOLS2 -> Layouts.symbols(2, multi, icon)
            Page.NUMPAD -> Layouts.numberPad(icon, phone)
        }
        kbView.spaceLabel = if (multi) language.name else ""
        Brain.withDictionary(this, lang) { if (it.size > 1) engineVersion = -1 }
    }

    /** The engine for the current letter layout, rebuilt whenever key geometry changes. */
    private fun engine(): TypingEngine? {
        if (page != Page.ALPHA) return null
        if (engine != null && engineVersion == kbView.geometryVersion && engineLang == lang) return engine
        val dict = Brain.dictionaryIfLoaded(lang) ?: return null
        val geom = kbView.letterGeometry()
        if (geom.isEmpty()) return null
        val layoutId = Languages.get(lang).layout + if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "_land" else ""
        val e = Brain.engine(this, lang, layoutId, geom, dict)
        e.autocorrectLevel = prefs.autocorrectLevel
        e.splitWords = prefs.splitWords
        engine = e
        engineVersion = kbView.geometryVersion
        engineLang = lang
        gesture = GestureDecoder(e.spatial, dict, e.history).also { g -> Net.pool.execute { g.prepare() } }
        currentLayoutId = layoutId
        return e
    }

    private var currentLayoutId = ""

    // ------------------------------------------------------------------ KeyboardView.Listener

    override fun resolveLetter(x: Float, y: Float, size: Float, nearSpace: Boolean): KeyboardView.Key? {
        if (search != null || !corrections) return null
        val e = engine() ?: return null
        if (!composer.isEmpty && !composer.decodable()) return null
        val idx = e.resolve(composer.tapList(), Tap(x, y, size, nearSpace))
        if (idx < 0) return null
        val c = e.spatial.keys[idx].char
        return kbView.keys.firstOrNull { it.spec.type == KeyType.LETTER && it.spec.label[0] == c }
    }

    override fun onPress(key: KeyboardView.Key) {
        if (prefs.haptics) {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
        if (prefs.sound) {
            val am = getSystemService(AudioManager::class.java)
            val fx = when (key.spec.code) {
                Codes.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                Codes.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Codes.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            am.playSoundEffect(fx, -1f)
        }
    }

    override fun onLetter(key: KeyboardView.Key, x: Float, y: Float, size: Float, nearSpace: Boolean) {
        val label = key.spec.label
        val ch = if (kbView.shiftState > 0) label.uppercase() else label
        search?.let { it.second.append(ch); afterShiftedChar(); showSearchBar(); return }
        val ic = currentInputConnection ?: return
        fixPunctuationThatWasMeantAsSpace(ic)
        if (composer.isEmpty) composer.allCaps = kbView.shiftState == 2
        val tap = if (page == Page.ALPHA) Tap(x, y, size, nearSpace) else null
        composer.add(ch, tap)
        ic.setComposingText(composer.text, 1)
        lastCommit = null
        autoSpace = false
        afterShiftedChar()
        updateSuggestions()
    }

    override fun onKey(key: KeyboardView.Key, nearSpace: Boolean) {
        val code = key.spec.code
        when (code) {
            Codes.SHIFT -> onShift()
            Codes.DELETE -> onDelete()
            Codes.SPACE -> onSpace()
            Codes.ENTER -> onEnter()
            Codes.SYMBOLS -> switchPage(Page.SYMBOLS)
            Codes.SYMBOLS2 -> switchPage(Page.SYMBOLS2)
            Codes.ALPHA -> switchPage(if (numeric || phone) Page.NUMPAD else Page.ALPHA)
            Codes.LANGUAGE -> nextLanguage()
            Codes.EMOJI -> onAction(Action.EMOJI)
            else -> {
                val text = key.spec.text ?: key.spec.label
                typeText(text, nearSpace)
            }
        }
    }

    override fun onText(text: String) {
        typeText(text, false)
    }

    override fun onLongPress(key: KeyboardView.Key): Boolean {
        return when (key.spec.code) {
            Codes.SPACE, Codes.LANGUAGE -> {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                true
            }
            Codes.EMOJI -> { onAction(Action.MORE); true }
            else -> false
        }
    }

    override fun onCursorMove(steps: Int) {
        val ic = currentInputConnection ?: return
        if (!composer.isEmpty) commitAsTyped(ic)
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
        lastCommit = null
    }

    private var swipeAnchor = -1
    private var swipeLength = 0

    override fun onDeleteSwipe(words: Int) {
        val ic = currentInputConnection ?: return
        if (swipeAnchor < 0) {
            if (!composer.isEmpty) commitAsTyped(ic)
            swipeAnchor = selEnd
        }
        val before = ic.getTextBeforeCursor(400, 0)?.toString() ?: ""
        val textBefore = before.substring(0, (before.length - (selEnd - swipeAnchor).coerceAtLeast(0)).coerceAtLeast(0))
        swipeLength = lengthOfLastWords(textBefore, words)
        ic.setSelection(swipeAnchor - swipeLength, swipeAnchor)
    }

    override fun onDeleteSwipeEnd(words: Int) {
        val ic = currentInputConnection ?: return
        if (swipeAnchor >= 0) {
            ic.setSelection(swipeAnchor, swipeAnchor)
            if (words > 0 && swipeLength > 0) ic.deleteSurroundingText(swipeLength, 0)
        }
        swipeAnchor = -1
        swipeLength = 0
        lastCommit = null
        updateShift()
        updateSuggestions()
    }

    private fun lengthOfLastWords(text: String, words: Int): Int {
        var i = text.length
        repeat(words) {
            while (i > 0 && text[i - 1].isWhitespace()) i--
            if (i > 0 && !text[i - 1].isLetterOrDigit()) {
                i--
            } else {
                while (i > 0 && (text[i - 1].isLetterOrDigit() || text[i - 1] == '\'')) i--
            }
        }
        return text.length - i
    }

    override fun onGesture(trail: List<FloatArray>) {
        val ic = currentInputConnection ?: return
        val e = engine() ?: return
        val g = gesture ?: return
        if (search != null) return
        if (!composer.isEmpty) commitWord(" ")
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        if (before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'¿¡") {
            ic.commitText(" ", 1)
        }
        val results = g.decode(trail, previousWord(), 4)
        if (results.isEmpty()) return
        val shift = kbView.shiftState
        val cased = results.map { applyShift(it.word, shift) }
        composer.set(cased[0], null, gesture = true)
        composer.capsFirst = shift > 0
        composer.allCaps = shift == 2
        gestureAlternates = cased
        ic.setComposingText(composer.text, 1)
        lastCommit = null
        afterShiftedChar()
        updateSuggestions()
    }

    private fun applyShift(word: String, shift: Int) = when (shift) {
        2 -> word.uppercase()
        1 -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    // ------------------------------------------------------------------ key handling

    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        kbView.shiftState = when {
            kbView.shiftState == 1 && now - lastShiftTap < 400 -> 2
            kbView.shiftState == 0 -> 1
            else -> 0
        }
        shiftAuto = false
        lastShiftTap = now
    }

    private fun afterShiftedChar() {
        if (kbView.shiftState == 1) {
            kbView.shiftState = 0
            shiftAuto = false
        }
    }

    private fun updateShift() {
        if (root == null || page != Page.ALPHA) return
        if (kbView.shiftState == 2) return
        val ic = currentInputConnection
        val info = editor
        val caps = prefs.autoCap && ic != null && info != null && composer.isEmpty && search == null &&
            ic.getCursorCapsMode(info.inputType) != 0
        if (caps) {
            kbView.shiftState = 1
            shiftAuto = true
        } else if (shiftAuto) {
            kbView.shiftState = 0
            shiftAuto = false
        }
    }

    private fun switchPage(p: Page) {
        page = p
        returnToAlphaOnSpace = false
        loadLayout()
        if (p == Page.ALPHA) updateShift()
    }

    private fun nextLanguage() {
        val langs = prefs.languages
        if (langs.size < 2) return
        if (!composer.isEmpty) commitAsTyped(currentInputConnection ?: return)
        val next = langs[(langs.indexOf(lang) + 1) % langs.size]
        lang = next
        prefs.currentLanguage = next
        engine = null
        loadLayout()
        updateSuggestions()
    }

    private fun onSpace() {
        search?.let { it.second.append(' '); showSearchBar(); return }
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (!composer.isEmpty) {
            commitWord(" ")
        } else {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (prefs.doubleSpacePeriod && autoSpace && now - lastSpaceTime < 800 && before.length == 2 &&
                before[1] == ' ' && before[0].isLetterOrDigit()
            ) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
            } else {
                ic.commitText(" ", 1)
                autoSpace = true
            }
            lastCommit = null
        }
        lastSpaceTime = now
        lastPunct = null
        if (returnToAlphaOnSpace && page != Page.ALPHA) switchPage(Page.ALPHA)
        updateShift()
        updateSuggestions()
    }

    private fun onEnter() {
        search?.let { s ->
            val q = s.second.toString().trim()
            search = null
            lastMediaQuery[s.first] = q
            loadLayout()
            showPanel(if (s.first == Giphy.Kind.GIFS) Panel.GIF else Panel.STICKER)
            return
        }
        val ic = currentInputConnection ?: return
        if (!composer.isEmpty) commitWord("")
        val info = editor
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = info == null || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (noAction) {
            ic.commitText("\n", 1)
        } else {
            ic.performEditorAction(action)
        }
        autoSpace = false
        lastCommit = null
        updateShift()
        updateSuggestions()
    }

    private fun onDelete() {
        search?.let { s -> if (s.second.isNotEmpty()) s.second.setLength(s.second.length - 1); showSearchBar(); return }
        val ic = currentInputConnection ?: return
        if (!composer.isEmpty) {
            if (composer.fromGesture) {
                ic.commitText("", 1)
                composer.clear()
            } else {
                composer.removeLast()
                if (composer.isEmpty) ic.commitText("", 1) else ic.setComposingText(composer.text, 1)
            }
            updateShift()
            updateSuggestions()
            return
        }
        if (selStart != selEnd) {
            ic.commitText("", 1)
            lastCommit = null
            updateShift()
            updateSuggestions()
            return
        }
        val last = lastCommit
        if (last != null && reopenLastWord(ic, last)) return
        lastCommit = null
        autoSpace = false
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateShift()
        updateSuggestions()
    }

    /**
     * Backspace right after a word: undo an autocorrection (back to what was typed), or step back
     * into the word so it can be edited and re-decoded.
     */
    private fun reopenLastWord(ic: InputConnection, last: Commit): Boolean {
        val before = ic.getTextBeforeCursor(last.inserted.length, 0)?.toString() ?: return false
        if (before != last.inserted) return false
        lastCommit = null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(last.inserted.length, 0)
        if (last.corrected && !last.gesture) {
            composer.set(last.literal, last.taps, gesture = false)
            composer.keepAsTyped = true
            engine()?.history?.rejectCorrection(last.literal, last.word)
            pendingLearn = null
        } else {
            composer.set(last.word, if (last.gesture) null else last.taps, last.gesture)
            if (last.inserted.length == last.word.length) {
                // Nothing after the word (no space): plain backspace removes its last letter.
                composer.removeLast()
            }
        }
        composer.capsFirst = last.capsFirst
        if (composer.isEmpty) ic.commitText("", 1) else ic.setComposingText(composer.text, 1)
        ic.endBatchEdit()
        autoSpace = false
        updateSuggestions()
        return true
    }

    /** Characters from the symbol keys, popups and alternates. */
    private fun typeText(text: String, nearSpace: Boolean) {
        search?.let { it.second.append(text); showSearchBar(); return }
        val ic = currentInputConnection ?: return
        val single = text.length == 1
        val c = text[0]
        // Accented letters and apostrophes continue the current word.
        if (single && c.isLetter()) {
            fixPunctuationThatWasMeantAsSpace(ic)
            val ch = if (kbView.shiftState > 0) text.uppercase() else text
            composer.add(ch, null)
            ic.setComposingText(composer.text, 1)
            afterShiftedChar()
            lastCommit = null
            updateSuggestions()
            return
        }
        if (single && (c == '\'' || c == '’') && !composer.isEmpty && page == Page.ALPHA) {
            val elision = composer.text.toString().lowercase() + "'"
            val dict = Brain.dictionaryIfLoaded(lang)
            if ((lang == "fr" || lang == "it") && dict != null && dict.contains(elision)) {
                ic.commitText(composer.text.toString() + text, 1)
                composer.clear()
            } else {
                composer.add(text, null)
                ic.setComposingText(composer.text, 1)
            }
            updateSuggestions()
            return
        }
        if (!composer.isEmpty) commitWord("")
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        if (single && c in ".,?!:;" && autoSpace && before == " ") {
            // "word ," -> "word, "
            ic.deleteSurroundingText(1, 0)
            ic.commitText("$text ", 1)
            autoSpace = true
        } else {
            ic.commitText(text, 1)
            autoSpace = false
        }
        lastPunct = if (single && (c == '.' || c == ',')) Punct(text, nearSpace, SystemClock.uptimeMillis()) else null
        lastCommit = null
        if ((page == Page.SYMBOLS || page == Page.SYMBOLS2) && !numeric && !phone) returnToAlphaOnSpace = true
        updateShift()
        updateSuggestions()
    }

    /**
     * A '.' tapped on its edge next to the space bar, immediately followed by a letter, was almost
     * always a missed space: "hello.world" becomes "hello world".
     */
    private fun fixPunctuationThatWasMeantAsSpace(ic: InputConnection) {
        val p = lastPunct ?: return
        lastPunct = null
        if (!prefs.smartPunctuation || !corrections || !p.nearSpace || !composer.isEmpty) return
        if (SystemClock.uptimeMillis() - p.time > 2500) return
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: return
        if (!before.endsWith(p.ch)) return
        val word = before.dropLast(1).takeLastWhile { it.isLetter() || it == '\'' }
        val dict = Brain.dictionaryIfLoaded(lang) ?: return
        if (word.length < 2 || !dict.contains(word)) return
        ic.deleteSurroundingText(1, 0)
        ic.commitText(" ", 1)
        if (shiftAuto) { kbView.shiftState = 0; shiftAuto = false }
    }

    // ------------------------------------------------------------------ committing words

    private fun commitAsTyped(ic: InputConnection) {
        ic.finishComposingText()
        composer.clear()
        updateSuggestions()
    }

    /** Finish the current word (with autocorrection) and append [separator]. */
    private fun commitWord(separator: String) {
        val ic = currentInputConnection ?: return
        val literal = composer.text.toString()
        var word = literal
        var corrected = false
        val e = engine()
        val prev = previousWord()
        if (e != null && corrections && composer.decodable() && !composer.keepAsTyped) {
            val r = e.finishWord(composer.tapList(), literal.lowercase(), prev)
            if (r.corrected) {
                word = composer.applyCase(r.text)
                corrected = word != literal
            }
        }
        ic.commitText(word + separator, 1)
        recordWord(prev, word, literal, corrected)
        lastCommit = Commit(word + separator, word, literal, ArrayList(composer.taps), composer.capsFirst, corrected, composer.fromGesture)
        composer.clear()
        autoSpace = separator == " "
    }

    /** After a commit: confirm the previous word for learning, queue this one, update history. */
    private fun recordWord(prev: String?, word: String, literal: String, corrected: Boolean) {
        applyPendingLearn()
        val e = engine() ?: return
        if (!learning) return
        val taps = composer.taps
        val letters = word.lowercase().filter { it.isLetter() }
        if (prefs.adaptiveLearning && taps.isNotEmpty() && taps.all { it != null } && letters.length == taps.size) {
            pendingLearn = letters to taps.filterNotNull()
        }
        if (word.any { it.isLetter() }) {
            e.history.record(prev, word)
            if (!corrected && !e.dictionary.contains(literal) && composer.decodable()) {
                if (e.history.recordUnknown(literal)) {
                    Brain.invalidateUserDictionary(lang)
                    engineVersion = -1
                }
            }
        }
        if (++commitsSinceSave >= 25) {
            commitsSinceSave = 0
            Brain.save(this)
        }
    }

    private fun applyPendingLearn() {
        val (word, taps) = pendingLearn ?: return
        pendingLearn = null
        val e = engine ?: return
        word.forEachIndexed { i, c -> e.spatial.learn(c, taps[i]) }
        Brain.markTouchDirty(currentLayoutId)
    }

    private fun previousWord(): String? {
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return null
        val text = before.dropLast(composer.length.coerceAtMost(before.length))
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty() || trimmed.last() in ".!?\n") return null
        return trimmed.takeLastWhile { it.isLetter() || it == '\'' }.ifEmpty { null }
    }

    // ------------------------------------------------------------------ suggestions

    private fun updateSuggestions() {
        if (root == null || search != null || panel != Panel.NONE) return
        val list = ArrayList<String>()
        var bold = false
        when {
            composer.isEmpty -> {
                val e = engine()
                val prev = previousWord()
                if (e != null && corrections && prefs.nextWordPrediction && prev != null) {
                    list.addAll(e.history.predictions(prev, 3))
                }
            }
            composer.fromGesture -> list.addAll(gestureAlternates.distinct())
            else -> {
                val literal = composer.text.toString()
                val e = engine()
                if (e != null && corrections && composer.decodable()) {
                    val taps = composer.tapList()
                    val prev = previousWord()
                    val will = if (composer.keepAsTyped) literal else
                        composer.applyCase(e.finishWord(taps, literal.lowercase(), prev, allowSplit = false).text).let { if (it.equals(literal, true)) literal else it }
                    list.add(will)
                    bold = will != literal
                    if (bold) list.add(literal)
                    for (c: Candidate in e.suggestions(taps, prev, 6)) {
                        val w = composer.applyCase(c.word)
                        if (list.none { it.equals(w, ignoreCase = true) }) list.add(w)
                        if (list.size >= 3) break
                    }
                } else {
                    list.add(literal)
                    val dict = Brain.dictionaryIfLoaded(lang)
                    val node = dict?.find(literal) ?: -1
                    if (dict != null && node >= 0) {
                        for (n in dict.topCompletions(node, 4)) {
                            val w = composer.applyCase(dict.display(n))
                            if (list.none { it.equals(w, true) }) list.add(w)
                            if (list.size >= 3) break
                        }
                    }
                }
            }
        }
        suggestionsShown = list.take(3)
        if (suggestionsShown.isEmpty()) bar.showToolbar() else bar.showSuggestions(suggestionsShown, bold)
    }

    override fun onSuggestion(index: Int, text: String) {
        val ic = currentInputConnection ?: return
        if (!composer.isEmpty) {
            val prev = previousWord()
            val literal = composer.text.toString()
            val wasGesture = composer.fromGesture
            ic.commitText("$text ", 1)
            recordWord(prev, text, literal, corrected = !text.equals(literal, true))
            // Picking a word is explicit, so a later backspace re-opens it rather than "undoing".
            lastCommit = Commit("$text ", text, literal, ArrayList(composer.taps), composer.capsFirst, false, wasGesture)
            composer.clear()
        } else {
            val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
            val lead = if (before.isNotEmpty() && !before[0].isWhitespace()) " " else ""
            val prev = previousWord()
            ic.commitText("$lead$text ", 1)
            engine()?.takeIf { learning }?.history?.record(prev, text)
            lastCommit = null
        }
        autoSpace = true
        updateShift()
        updateSuggestions()
    }

    override fun onSuggestionLongPress(text: String) {
        val e = engine() ?: return
        if (e.history.userWords().containsKey(text)) {
            e.history.removeUserWord(text)
            Brain.invalidateUserDictionary(lang)
            engineVersion = -1
            Toast.makeText(this, "Removed “$text” from your words", Toast.LENGTH_SHORT).show()
        } else if (!composer.isEmpty && composer.text.toString() == text) {
            e.history.addUserWord(text)
            Brain.invalidateUserDictionary(lang)
            engineVersion = -1
            Toast.makeText(this, "Added “$text” to your words", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ toolbar & panels

    override fun onAction(action: Action) {
        when (action) {
            Action.MORE -> if (panel == Panel.MORE) showKeyboard() else showPanel(Panel.MORE)
            Action.KEYBOARD -> {
                val s = search
                if (s != null) {
                    search = null
                    loadLayout()
                    showPanel(if (s.first == Giphy.Kind.GIFS) Panel.GIF else Panel.STICKER)
                } else {
                    showKeyboard()
                }
            }
            Action.EMOJI -> showPanel(Panel.EMOJI)
            Action.GIF -> showPanel(Panel.GIF)
            Action.STICKER -> showPanel(Panel.STICKER)
            Action.CLIPBOARD -> showPanel(Panel.CLIPBOARD)
            Action.DEFINE -> { lookupSynonyms = false; showPanel(Panel.LOOKUP) }
            Action.THESAURUS -> { lookupSynonyms = true; showPanel(Panel.LOOKUP) }
            Action.ONE_HANDED -> setMode(if (prefs.mode.startsWith("one_")) "normal" else "one_right")
            Action.FLOATING -> setMode(if (prefs.mode == "floating") "normal" else "floating")
            Action.VOICE -> startVoice()
            Action.CALIBRATE -> launch(Intent(this, CalibrationActivity::class.java))
            Action.THEME -> {
                prefs.theme = when (prefs.theme) { "system" -> "light"; "light" -> "dark"; else -> "system" }
                setInputView(onCreateInputView())
                refreshAfterRebuild()
            }
            Action.SETTINGS -> launch(Intent(this, SettingsActivity::class.java))
        }
    }

    private var lookupSynonyms = false

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requestHideSelf(0)
    }

    private fun keyboardHeight(): Int = kbView.height.takeIf { it > 0 } ?: kbView.measuredHeight.takeIf { it > 0 }
        ?: ((prefs.keyHeightDp * 4 + 10) * resources.displayMetrics.density).toInt()

    private fun showKeyboard() {
        if (root == null) return
        voice?.cancel(); voice = null; voicePanel = null
        panel = Panel.NONE
        if (kbView.parent !== content || content.childCount != 1) {
            content.removeAllViews()
            (kbView.parent as? ViewGroup)?.removeView(kbView)
            content.addView(kbView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        updateSuggestions()
    }

    private fun showPanel(p: Panel) {
        if (root == null) return
        val ic = currentInputConnection
        val h = keyboardHeight()
        val lookupWord = if (p == Panel.LOOKUP) wordForLookup() else null
        if (!composer.isEmpty && ic != null && p != Panel.LOOKUP) commitWord("")
        val view: View = when (p) {
            Panel.MORE -> MorePanel(this, theme, MORE_ACTIONS) { onAction(it) }
            Panel.EMOJI -> EmojiPanel(this, theme, { prefs.recentEmoji }, { insertEmoji(it) }, { onDelete() }, { showKeyboard() })
            Panel.GIF, Panel.STICKER -> {
                val kind = if (p == Panel.GIF) Giphy.Kind.GIFS else Giphy.Kind.STICKERS
                val key = prefs.giphyKey
                MediaPanel(this, theme, kind, if (key.isBlank()) null else Giphy(key), images, lang, lastMediaQuery[kind] ?: "",
                    onPick = { insertMedia(it, kind) },
                    onSearch = { startSearch(kind) },
                    onAbc = { showKeyboard() })
            }
            Panel.CLIPBOARD -> ClipboardPanel(this, theme, clipboard, prefs.clipboardHistory, { commitPlain(it) }, { showKeyboard() })
            Panel.LOOKUP -> LookupPanel(this, theme, lookupWord, lang, lookupSynonyms, { replaceLookupWord(lookupWord, it) }, { showKeyboard() })
            Panel.VOICE -> VoicePanel(this, theme, { voice?.stop() }, { showKeyboard() }).also { voicePanel = it }
            Panel.NONE -> { showKeyboard(); return }
        }
        panel = p
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, h))
        bar.showPanelMode(when (p) {
            Panel.MORE -> "All tools"; Panel.EMOJI -> "Emoji"; Panel.GIF -> "GIFs"; Panel.STICKER -> "Stickers"
            Panel.CLIPBOARD -> "Clipboard"; Panel.LOOKUP -> if (lookupSynonyms) "Thesaurus" else "Dictionary"
            Panel.VOICE -> "Voice typing"; Panel.NONE -> ""
        })
    }

    private fun commitPlain(text: String) {
        val ic = currentInputConnection ?: return
        if (!composer.isEmpty) commitWord("")
        ic.commitText(text, 1)
        lastCommit = null
        autoSpace = false
    }

    private fun insertEmoji(e: String) {
        commitPlain(e)
        prefs.recentEmoji = listOf(e) + prefs.recentEmoji.filter { it != e }
    }

    private fun startSearch(kind: Giphy.Kind) {
        search = kind to StringBuilder()
        page = Page.ALPHA
        showKeyboard()
        loadLayout()
        kbView.shiftState = 0
        showSearchBar()
    }

    private fun showSearchBar() {
        val s = search ?: return
        bar.showSearch(s.second.toString(), if (s.first == Giphy.Kind.GIFS) "Search GIFs" else "Search stickers")
    }

    // ------------------------------------------------------------------ definition / thesaurus

    private fun wordForLookup(): String? {
        val ic = currentInputConnection ?: return null
        ic.getSelectedText(0)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length < 40 }?.let { return it }
        if (!composer.isEmpty) return composer.text.toString()
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(40, 0)?.toString() ?: ""
        val left = before.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
        val right = after.takeWhile { it.isLetter() || it == '\'' || it == '-' }
        val w = left + right
        if (w.isNotEmpty()) return w
        // Cursor after "word ": use the previous word.
        return before.trimEnd().takeLastWhile { it.isLetter() || it == '\'' }.ifEmpty { null }
    }

    private fun replaceLookupWord(original: String?, replacement: String) {
        val ic = currentInputConnection ?: return
        if (original == null) return
        val rep = if (original.firstOrNull()?.isUpperCase() == true) replacement.replaceFirstChar { it.uppercaseChar() } else replacement
        ic.beginBatchEdit()
        if (!composer.isEmpty) {
            ic.commitText(rep, 1)
            composer.clear()
        } else if (ic.getSelectedText(0)?.toString()?.trim() == original) {
            ic.commitText(rep, 1)
        } else {
            val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(64, 0)?.toString() ?: ""
            var left = before.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }.length
            val right = after.takeWhile { it.isLetter() || it == '\'' || it == '-' }.length
            var trailing = ""
            if (left == 0 && right == 0) {
                // Cursor sits after "word " – replace that word and keep the spacing.
                val trimmed = before.trimEnd()
                trailing = before.substring(trimmed.length)
                left = trimmed.takeLastWhile { it.isLetter() || it == '\'' }.length + trailing.length
            }
            ic.deleteSurroundingText(left, right)
            ic.commitText(rep + trailing, 1)
        }
        ic.endBatchEdit()
        showKeyboard()
    }

    // ------------------------------------------------------------------ voice

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            launch(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_REQUEST_MIC, true))
            return
        }
        if (!VoiceInput.available(this)) {
            if (!switchToVoiceIme()) Toast.makeText(this, "No speech recognition service on this device", Toast.LENGTH_LONG).show()
            return
        }
        if (!composer.isEmpty) commitWord(" ")
        showPanel(Panel.VOICE)
        val ic = currentInputConnection
        voice = VoiceInput(this, Languages.get(lang).locale,
            onPartial = { t -> voicePanel?.setTranscript(t); ic?.setComposingText(voiceText(t), 1) },
            onFinal = { t -> ic?.commitText(voiceText(t), 1); voicePanel?.setTranscript("") },
            onLevel = { voicePanel?.setLevel(it) },
            onStatus = { voicePanel?.setStatus(it) },
            onEnd = { ic?.finishComposingText(); voicePanel?.postDelayed({ if (panel == Panel.VOICE) showKeyboard() }, 900) },
        ).also { it.start() }
    }

    /** Spacing and capitalisation for dictated text relative to what's before the cursor. */
    private fun voiceText(t: String): String {
        val ic = currentInputConnection ?: return t
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        val info = editor
        val caps = info != null && ic.getCursorCapsMode(info.inputType) != 0
        val body = if (caps) t.replaceFirstChar { it.uppercaseChar() } else t
        return if (before.isNotEmpty() && !before.last().isWhitespace()) " $body" else body
    }

    private fun switchToVoiceIme(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java)
        for (imi in imm.enabledInputMethodList) {
            for (i in 0 until imi.subtypeCount) {
                val st = imi.getSubtypeAt(i)
                if (st.mode == "voice") {
                    switchInputMethod(imi.id, st)
                    return true
                }
            }
        }
        return false
    }

    // ------------------------------------------------------------------ GIFs & stickers

    private fun insertMedia(item: Giphy.Item, kind: Giphy.Kind) {
        val info = editor ?: return
        val mimes = info.contentMimeTypes ?: emptyArray()
        val acceptsGif = mimes.any { ClipDescription.compareMimeTypes("image/gif", it) }
        val acceptsPng = mimes.any { ClipDescription.compareMimeTypes("image/png", it) }
        if (!acceptsGif && !acceptsPng) {
            commitPlain(item.pageUrl.ifEmpty { item.sendUrl })
            Toast.makeText(this, "This app doesn't accept ${if (kind == Giphy.Kind.GIFS) "GIFs" else "stickers"}, so a link was inserted", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = MediaProvider.dir(this)
        Net.async({
            val gif = Net.download(item.sendUrl, File(dir, "${item.id}.gif"))
            if (acceptsGif) gif else {
                // Still image fallback: first frame as PNG.
                val png = File(dir, "${item.id}.png")
                if (!png.exists()) {
                    val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(gif)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                    png.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
                png
            }
        }) { r ->
            val f = r.getOrNull()
            val ic = currentInputConnection
            if (f == null || ic == null) {
                Toast.makeText(this, "Couldn't download that one", Toast.LENGTH_SHORT).show()
                return@async
            }
            val mime = MediaProvider.mimeFor(f.name)
            val content = InputContentInfo(MediaProvider.uriFor(f), ClipDescription(item.title, arrayOf(mime)),
                item.pageUrl.takeIf { it.startsWith("https://") }?.let { Uri.parse(it) })
            val ok = ic.commitContent(content, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            if (!ok) Toast.makeText(this, "The app didn't accept it", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        val MORE_ACTIONS = listOf(
            Action.EMOJI, Action.GIF, Action.STICKER, Action.CLIPBOARD,
            Action.DEFINE, Action.THESAURUS, Action.VOICE, Action.ONE_HANDED,
            Action.FLOATING, Action.CALIBRATE, Action.THEME, Action.SETTINGS,
        )
    }
}
