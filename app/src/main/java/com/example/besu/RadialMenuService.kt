package com.example.besu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.view.setPadding

class RadialMenuService : Service() {
    private var windowManager: WindowManager? = null
    private var radialMenuView: View? = null
    private var radialContainer: RadialMenuLayout? = null

    // Sidebar Expansion
    private var expandedItem: CommItem? = null
    private var expansionScroll: ScrollView? = null
    private var expansionContainer: LinearLayout? = null

    // Sentence State
    private var isSentenceMode = false
    private val sentenceBuffer = mutableListOf<String>()
    private val sentenceEmojis = mutableListOf<String>()
    private val sentenceIds = mutableListOf<String>() // NEW: Tracks IDs for AI Learning

    private var isPickerMode = false

    // AI Context
    private var lastAddedId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // --- FIX: START FOREGROUND IMMEDIATELY ---
        // This prevents the OS from killing the service when launched via Accessibility Button
        createNotificationChannel()
        startForeground(420, createNotification())
        // -----------------------------------------

        isPickerMode = intent?.getBooleanExtra("IS_PICKER_MODE", false) ?: false

        // Initialize the Brain
        RecommendationEngine.init(this)

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

        if (isPickerMode) {
            val title = radialMenuView?.findViewById<TextView>(R.id.tvSentence)
            title?.hint = "SELECT COMMAND..."
            radialMenuView?.setBackgroundColor(Color.parseColor("#DD001133"))
        }
    }

    private fun setupInterface(view: View) {
        radialContainer = view.findViewById(R.id.radialContainer)
        expansionScroll = view.findViewById(R.id.grammarExpansionScroll)
        expansionContainer = view.findViewById(R.id.grammarExpansionContainer)

        val vocab = CommunicationData.load(this)

        resetToHome()
        radialContainer?.onItemSelected = { /* Visual Expand */ }
        radialContainer?.onItemConfirmed = { item -> handleSelection(item) }

        val rowTop1 = view.findViewById<LinearLayout>(R.id.rowTop1)
        val rowTop2 = view.findViewById<LinearLayout>(R.id.rowTop2)
        val rowBottom1 = view.findViewById<LinearLayout>(R.id.rowBottom1)
        val rowBottom2 = view.findViewById<LinearLayout>(R.id.rowBottom2)
        val sidebarRight = view.findViewById<LinearLayout>(R.id.sidebarRight)

        // --- ROW 1: AI PREDICTIONS (Replaces Static History) ---
        // Loads initial "Cold Start" suggestions based on Time of Day
        updateSuggestionsRow(rowTop1)

        // --- ROW 2: CORE BUILDERS (Who + What) ---
        populateLinearRow(rowTop2, vocab.who + vocab.what)

        // --- ROW 3: CONTEXT (Where + Needs) ---
        populateLinearRow(rowBottom1, vocab.where + vocab.needs)

        // --- ROW 4: MODIFIERS (When + Desc + Questions) ---
        val bottomModifiers = vocab.time + vocab.descriptions + vocab.questions
        populateLinearRow(rowBottom2, bottomModifiers)

        // --- SIDEBAR: GRAMMAR + LINKERS ---
        populateVerticalSidebar(sidebarRight, vocab.grammar + vocab.linkers)

        // --- CONTROLS ---
        view.findViewById<TextView>(R.id.btnHome)?.setOnClickListener { resetToHome() }

        view.findViewById<TextView>(R.id.btnSavedPhrases)?.setOnClickListener {
            // Only show saved history if NOT in Picker Mode
            if(!isPickerMode) showSavedPhrases()
        }

        view.findViewById<TextView>(R.id.btnPanic)?.setOnClickListener {
            if(!isPickerMode) triggerPanic()
        }

        setupSentenceControls(view)

        view.setOnClickListener {
            if (expansionScroll?.visibility == View.VISIBLE) closeExpansion()
            else {
                removeRadialMenu()
                stopSelf() // Kills the service cleanly
            }
        }
    }

    // NEW: Helper to refresh Row 1 with AI Brain data
    private fun updateSuggestionsRow(container: LinearLayout) {
        container.removeAllViews() // Clear old suggestions

        val suggestions = RecommendationEngine.getSuggestions(this, lastAddedId)

        // Populate using special "AI Styling" (e.g. Purple Border)
        populateLinearRow(container, suggestions, isSpecial = true)
    }

    private fun handleSelection(item: CommItem) {
        if (isSentenceMode) {
            // 1. Add to Buffer
            sentenceBuffer.add(item.phrase)
            sentenceEmojis.add(item.emoji)
            sentenceIds.add(item.id) // Track ID for AI Learning

            // 2. Update Context for AI
            lastAddedId = item.id

            // 3. Refresh Suggestions
            val rowTop1 = radialMenuView?.findViewById<LinearLayout>(R.id.rowTop1)
            if (rowTop1 != null) updateSuggestionsRow(rowTop1)

            updateSentenceDisplay()
        } else {
            // Instant Mode
            if (isPickerMode) {
                returnPickerResult(item.emoji, item.phrase, "COMMAND")
                return
            }

            // Trigger & Learn (Single-shot learning)
            HistoryManager.addToHistory(this, item)
            RecommendationEngine.learnSentence(listOf(item.id)) // Teach that this word was used
            RecommendationEngine.persist(this)

            triggerAction(item.emoji, item.phrase)
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("menu_sticky", false)) {
                removeRadialMenu()
                stopSelf() // Close service after action
            }
        }
    }

    private fun setupSentenceControls(view: View) {
        val sentenceBar = view.findViewById<LinearLayout>(R.id.sentenceBar)
        val btnPlay = view.findViewById<ImageButton>(R.id.btnPlaySentence)
        val btnClear = view.findViewById<ImageButton>(R.id.btnClearSentence)
        val btnToggle = view.findViewById<Button>(R.id.btnToggleMode)

        btnToggle?.setOnClickListener {
            isSentenceMode = !isSentenceMode
            if (isSentenceMode) {
                btnToggle.text = "Mode: Builder"
                sentenceBar.visibility = View.VISIBLE

                // Reset AI context when entering builder mode
                lastAddedId = null
                val rowTop1 = radialMenuView?.findViewById<LinearLayout>(R.id.rowTop1)
                if (rowTop1 != null) updateSuggestionsRow(rowTop1)

            } else {
                btnToggle.text = "Mode: Instant"
                sentenceBar.visibility = View.GONE
                clearBuffers()
            }
        }

        btnPlay?.setOnClickListener {
            if (sentenceBuffer.isNotEmpty()) {
                val fullSentence = sentenceBuffer.joinToString(" ")
                val fullEmojis = sentenceEmojis.joinToString(" ")

                if (isPickerMode) {
                    returnPickerResult(fullEmojis, fullSentence, "SENTENCE")
                    return@setOnClickListener
                }

                // 1. Speak
                triggerAction(fullEmojis, fullSentence)

                // 2. Learn!
                if (sentenceIds.isNotEmpty()) {
                    RecommendationEngine.learnSentence(sentenceIds)
                    RecommendationEngine.persist(this)
                }

                // 3. Clear
                clearBuffers()
            }
        }

        btnClear?.setOnClickListener {
            if (sentenceBuffer.isNotEmpty()) {
                sentenceBuffer.removeAt(sentenceBuffer.size - 1)
                sentenceEmojis.removeAt(sentenceEmojis.size - 1)
                if (sentenceIds.isNotEmpty()) sentenceIds.removeAt(sentenceIds.size - 1)

                // Update AI Context (Step back one word)
                lastAddedId = sentenceIds.lastOrNull()

                val rowTop1 = radialMenuView?.findViewById<LinearLayout>(R.id.rowTop1)
                if (rowTop1 != null) updateSuggestionsRow(rowTop1)

                updateSentenceDisplay()
            }
        }
    }

    private fun clearBuffers() {
        sentenceBuffer.clear()
        sentenceEmojis.clear()
        sentenceIds.clear()
        lastAddedId = null
        updateSentenceDisplay()

        // Refresh suggestions to "Start" state
        val rowTop1 = radialMenuView?.findViewById<LinearLayout>(R.id.rowTop1)
        if (rowTop1 != null) updateSuggestionsRow(rowTop1)
    }

    // --- SIDEBAR & RENDERING HELPERS ---

    private fun populateVerticalSidebar(container: LinearLayout, items: List<CommItem>) {
        container.removeAllViews()
        items.forEach { item ->
            val btn = createRoundButton(item, isSmall = true)
            btn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 4, 0, 12) }
            btn.setOnClickListener {
                if (item.subItems.isNotEmpty()) toggleSidebarExpansion(item) else { closeExpansion(); handleSelection(item) }
            }
            container.addView(btn)
        }
    }

    private fun toggleSidebarExpansion(item: CommItem) {
        if (expandedItem == item && expansionScroll?.visibility == View.VISIBLE) {
            closeExpansion()
        } else {
            expandedItem = item
            populateExpansionPanel(item)
            expansionScroll?.visibility = View.VISIBLE
        }
    }

    private fun populateExpansionPanel(parent: CommItem) {
        expansionContainer?.removeAllViews()
        val rootBtn = createRoundButton(parent, isSmall = true, isHeader = true)
        rootBtn.setOnClickListener { closeExpansion(); handleSelection(parent) }
        expansionContainer?.addView(rootBtn)

        val div = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 2).apply { gravity = android.view.Gravity.CENTER }
            setBackgroundColor(Color.GRAY)
        }
        expansionContainer?.addView(div)

        parent.subItems.forEach { sub ->
            val subBtn = createRoundButton(sub, isSmall = true)
            subBtn.setOnClickListener { closeExpansion(); handleSelection(sub) }
            expansionContainer?.addView(subBtn)
        }
    }

    private fun closeExpansion() {
        expandedItem = null
        expansionScroll?.visibility = View.GONE
    }

    private fun createRoundButton(item: CommItem, isSmall: Boolean = false, isHeader: Boolean = false): TextView {
        return TextView(this).apply {
            text = "${item.emoji}\n${item.label}"
            textSize = if(isSmall) 10f else 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(16, 24, 16, 24)
            val bg = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(item.getColorInt())
                if (isHeader) setStroke(4, Color.parseColor("#FFD700")) else setStroke(1, Color.parseColor("#88FFFFFF"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) }
        }
    }

    private fun populateLinearRow(container: LinearLayout, items: List<CommItem>, isSpecial: Boolean = false) {
        container.removeAllViews()
        items.forEach { item ->
            val btn = createRoundButton(item).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 8, 0) }
            }
            if (isSpecial) {
                // Special Styling for AI Suggestions
                (btn.background as GradientDrawable).setStroke(4, Color.parseColor("#9C27B0")) // Purple Stroke
            }
            btn.setOnClickListener {
                closeExpansion()
                if (item.subItems.isNotEmpty()) radialContainer?.setSubMenu(item) else handleSelection(item)
            }
            container.addView(btn)
        }
    }

    private fun returnPickerResult(emoji: String, phrase: String, type: String) {
        val intent = Intent("BESU_PICKER_RESULT")
        intent.setPackage(packageName)
        intent.putExtra("emoji", emoji)
        intent.putExtra("label", phrase)
        intent.putExtra("path", phrase)
        intent.putExtra("type", type)
        sendBroadcast(intent)
        removeRadialMenu()
        stopSelf()
    }

    private fun updateSentenceDisplay() {
        val tv = radialMenuView?.findViewById<TextView>(R.id.tvSentence)
        if (sentenceBuffer.isEmpty()) {
            tv?.text = ""
            tv?.hint = if(isPickerMode) "Select..." else "Select words..."
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

    private fun triggerPanic() {
        removeRadialMenu()
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("emotion", "🆘")
        intent.putExtra("phrase", "I need help immediately.")
        intent.putExtra("duration", 8000L)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startForegroundService(intent)
    }

    private fun showSavedPhrases() { /* Load from HistoryManager and populate radialContainer */ }

    private fun resetToHome() {
        val vocab = CommunicationData.load(this)
        radialContainer?.setItems(vocab.emotions)
        closeExpansion()
        lastAddedId = null
        val rowTop1 = radialMenuView?.findViewById<LinearLayout>(R.id.rowTop1)
        if (rowTop1 != null) updateSuggestionsRow(rowTop1)
    }

    private fun removeRadialMenu() {
        radialMenuView?.let {
            if (it.isAttachedToWindow) windowManager?.removeView(it)
            radialMenuView = null
        }
        // --- FIX: STOP FOREGROUND ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "besu_menu_channel",
                "Besu Menu",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "besu_menu_channel")
            .setContentTitle("Besu Menu Active")
            .setContentText("Tap outside to close")
            .setSmallIcon(android.R.drawable.ic_dialog_dialer)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        removeRadialMenu()
        super.onDestroy()
    }
}