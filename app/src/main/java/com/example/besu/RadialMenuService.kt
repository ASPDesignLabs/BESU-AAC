package com.example.besu

import android.app.Service
import android.graphics.PixelFormat
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding

class RadialMenuService : Service() {
    private var windowManager: WindowManager? = null
    private var radialMenuView: View? = null
    private var radialContainer: RadialMenuLayout? = null

    private var isSentenceMode = false
    private val sentenceBuffer = mutableListOf<String>()
    private val sentenceEmojis = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        showRadialMenu()
        return START_NOT_STICKY
    }

    private fun showRadialMenu() {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        removeRadialMenu()

        val themeContext = android.view.ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat)
        radialMenuView = LayoutInflater.from(themeContext).inflate(R.layout.radial_menu, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(radialMenuView, params)

        setupInterface(radialMenuView!!)
    }

    private fun setupInterface(view: View) {
        radialContainer = view.findViewById(R.id.radialContainer)
        val vocab = CommunicationData.load(this)

        resetToHome()
        radialContainer?.onItemSelected = { /* Visual Expand */ }
        radialContainer?.onItemConfirmed = { item -> handleSelection(item) }

        // --- ROW REFERENCES ---
        val rowTop1 = view.findViewById<LinearLayout>(R.id.rowTop1)
        val rowTop2 = view.findViewById<LinearLayout>(R.id.rowTop2)
        val rowBottom1 = view.findViewById<LinearLayout>(R.id.rowBottom1)
        val rowBottom2 = view.findViewById<LinearLayout>(R.id.rowBottom2)

        // --- POPULATE ROWS (PERMANENT LAYOUT) ---

        // TOP 1: History & Smart (Endless, Gold Border)
        val endlessItems = HistoryManager.getMergedHistoryAndSmart(this, vocab)
        populateLinearRow(rowTop1, endlessItems, isSpecial = true)

        // TOP 2: Who + Where + Time (Subject/Context)
        populateLinearRow(rowTop2, vocab.who + vocab.where + vocab.time)

        // BOTTOM 1: What + Needs + Descriptions (Verbs/Objects)
        // "Go" and "Want" live here.
        populateLinearRow(rowBottom1, vocab.what + vocab.needs + vocab.descriptions)

        // BOTTOM 2: Grammar + Linkers + Questions (The Glue)
        // "To", "The", "It", "And" live here. ALWAYS VISIBLE.
        // Positioned next to Home/Star buttons.
        val grammarRow = vocab.grammar + vocab.linkers + vocab.questions
        populateLinearRow(rowBottom2, grammarRow)

        // --- BUTTONS ---
        view.findViewById<TextView>(R.id.btnHome)?.setOnClickListener { resetToHome() }
        view.findViewById<TextView>(R.id.btnSavedPhrases)?.setOnClickListener { showSavedPhrases() }
        view.findViewById<TextView>(R.id.btnPanic)?.setOnClickListener { triggerPanic() }

        // --- SENTENCE MODE LOGIC ---
        setupSentenceControls(view)

        // Close on background tap
        view.setOnClickListener { removeRadialMenu() }
    }

    private fun setupSentenceControls(view: View) {
        val sentenceBar = view.findViewById<LinearLayout>(R.id.sentenceBar)
        val btnPlay = view.findViewById<ImageButton>(R.id.btnPlaySentence)
        val btnClear = view.findViewById<ImageButton>(R.id.btnClearSentence)
        val btnToggle = view.findViewById<Button>(R.id.btnToggleMode)

        // TOGGLE: Only hides/shows the top bar now. Does not mess with the rows.
        if (btnToggle != null) {
            btnToggle.setOnClickListener {
                isSentenceMode = !isSentenceMode
                if (isSentenceMode) {
                    btnToggle.text = "Mode: Builder"
                    sentenceBar.visibility = View.VISIBLE
                } else {
                    btnToggle.text = "Mode: Instant"
                    sentenceBar.visibility = View.GONE

                    // Optional: Clear buffer when exiting?
                    sentenceBuffer.clear()
                    sentenceEmojis.clear()
                    updateSentenceDisplay()
                }
            }
        }

        // PLAY
        btnPlay?.setOnClickListener {
            if (sentenceBuffer.isNotEmpty()) {
                val fullSentence = sentenceBuffer.joinToString(" ")
                val fullEmojis = sentenceEmojis.joinToString(" ")

                HistoryManager.addPhraseToHistory(this, fullEmojis, fullSentence)
                triggerAction(fullEmojis, fullSentence)

                sentenceBuffer.clear()
                sentenceEmojis.clear()
                updateSentenceDisplay()
            }
        }

        // CLEAR
        btnClear?.setOnClickListener {
            if (sentenceBuffer.isNotEmpty()) {
                sentenceBuffer.removeAt(sentenceBuffer.size - 1)
                sentenceEmojis.removeAt(sentenceEmojis.size - 1)
                updateSentenceDisplay()
            }
        }
    }

    private fun showSavedPhrases() {
        val recentPhrases = HistoryManager.getLastPhrases(this)
        if (recentPhrases.isEmpty()) {
            val empty = CommItem("empty", "📭", "No History", "No saved phrases.")
            radialContainer?.setItems(listOf(empty))
            return
        }
        val phraseItems = recentPhrases.mapIndexed { index, pair ->
            val (emojis, text) = pair
            val shortLabel = if (text.length > 12) text.take(10) + "..." else text
            CommItem("phrase_$index", emojis.take(4), shortLabel, text, "#FFD700")
        }
        radialContainer?.setItems(phraseItems)
    }

    private fun handleSelection(item: CommItem) {
        if (item.id.startsWith("phrase_")) {
            loadPhraseToBuffer(item)
            return
        }

        HistoryManager.addToHistory(this, item)

        if (isSentenceMode) {
            sentenceBuffer.add(item.phrase)
            sentenceEmojis.add(item.emoji)
            updateSentenceDisplay()
        } else {
            triggerAction(item.emoji, item.phrase)
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("menu_sticky", false)) {
                removeRadialMenu()
            }
        }
    }

    private fun loadPhraseToBuffer(item: CommItem) {
        isSentenceMode = true
        val btnToggle = radialMenuView?.findViewById<Button>(R.id.btnToggleMode)
        val sentenceBar = radialMenuView?.findViewById<LinearLayout>(R.id.sentenceBar)

        btnToggle?.text = "Mode: Builder"
        sentenceBar?.visibility = View.VISIBLE

        sentenceBuffer.clear(); sentenceBuffer.add(item.phrase)
        sentenceEmojis.clear(); sentenceEmojis.add(item.emoji)
        updateSentenceDisplay()
        resetToHome()
    }

    private fun triggerPanic() {
        removeRadialMenu()
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", "🆘")
        intent.putExtra("phrase", "I need help immediately.")
        intent.putExtra("duration", 8000L)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startForegroundService(intent)
    }

    // --- Helpers ---
    private fun populateLinearRow(container: LinearLayout, items: List<CommItem>, isSpecial: Boolean = false) {
        container.removeAllViews()
        items.forEach { item ->
            val btn = TextView(this).apply {
                text = "${item.emoji}\n${item.label}"
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(24)

                val bg = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(item.getColorInt())
                    if (isSpecial) setStroke(6, Color.parseColor("#FFD700"))
                    else setStroke(2, Color.WHITE)
                }
                background = bg

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }

                setOnClickListener { handleContextClick(item) }
            }
            container.addView(btn)
        }
    }

    private fun handleContextClick(item: CommItem) {
        if (item.subItems.isNotEmpty()) {
            radialContainer?.setSubMenu(item)
        } else {
            handleSelection(item)
        }
    }

    private fun resetToHome() {
        val vocab = CommunicationData.load(this)
        radialContainer?.setItems(vocab.emotions)
    }

    private fun updateSentenceDisplay() {
        val tv = radialMenuView?.findViewById<TextView>(R.id.tvSentence)
        if (sentenceBuffer.isEmpty()) {
            tv?.text = ""
            tv?.hint = "Select words..."
        } else {
            tv?.text = sentenceBuffer.joinToString(" ")
        }
    }

    private fun triggerAction(emoji: String, phrase: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", emoji)
        intent.putExtra("phrase", phrase)
        intent.putExtra("duration", 4000L)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startForegroundService(intent)
    }

    private fun removeRadialMenu() {
        radialMenuView?.let {
            if (it.isAttachedToWindow) windowManager?.removeView(it)
            radialMenuView = null
        }
    }

    override fun onDestroy() {
        removeRadialMenu()
        super.onDestroy()
    }
}