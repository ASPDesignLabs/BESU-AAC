package com.example.besu

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

class RadialMenuLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val itemButtons = mutableListOf<ItemButton>()
    private var activePrimaryButton: ItemButton? = null

    var onItemSelected: ((CommItem) -> Unit)? = null
    var onItemConfirmed: ((CommItem) -> Unit)? = null

    // --- 1. STANDARD RING MODE (Emotions) ---
    fun setItems(items: List<CommItem>) {
        removeAllViews(); itemButtons.clear(); activePrimaryButton = null
        val radius = 300f; val angleStep = 360f / items.size

        items.forEachIndexed { index, item ->
            val angle = Math.toRadians((angleStep * index).toDouble())
            val button = createItemButton(item)
            button.alpha = 0f; button.scaleX = 0f; button.scaleY = 0f

            addView(button)
            itemButtons.add(button)

            button.post {
                val centerX = width / 2f; val centerY = height / 2f
                val x = centerX + (radius * cos(angle)).toFloat() - button.width / 2f
                val y = centerY + (radius * sin(angle)).toFloat() - button.height / 2f
                button.x = centerX - button.width / 2f; button.y = centerY - button.height / 2f
                animateButtonIn(button, x, y, index * 50L)
            }

            // Click Handler
            button.setOnClickListener { handlePrimaryClick(button, item) }

            // Long Press (For Inflections/Details)
            button.setOnLongClickListener {
                if (item.subItems.isNotEmpty()) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    activePrimaryButton = button
                    expandItem(item, button)
                    onItemSelected?.invoke(item)
                }
                true
            }
        }
    }

    // --- 2. SUB-MENU MODE (Parent in Center) ---
    fun setSubMenu(parentItem: CommItem) {
        removeAllViews(); itemButtons.clear(); activePrimaryButton = null

        // A. Center Button (Parent)
        val centerButton = createItemButton(parentItem)
        centerButton.showConfirmationState()
        addView(centerButton)

        centerButton.post {
            centerButton.x = width / 2f - centerButton.width / 2f
            centerButton.y = height / 2f - centerButton.height / 2f
            centerButton.scaleX = 0f; centerButton.scaleY = 0f
            centerButton.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).start()
        }
        // Clicking center confirms the parent
        centerButton.setOnClickListener { onItemConfirmed?.invoke(parentItem) }

        // B. Ring Buttons (Children)
        val subItems = parentItem.subItems
        if (subItems.isNotEmpty()) {
            val radius = 280f
            val angleStep = 360f / subItems.size

            subItems.forEachIndexed { index, item ->
                val angle = Math.toRadians((angleStep * index - 90).toDouble())
                val button = createItemButton(item, isSubItem = true)

                button.alpha = 0f
                addView(button)
                itemButtons.add(button) // Track for fading

                button.post {
                    val targetX = width / 2f + (radius * cos(angle)).toFloat() - button.width / 2f
                    val targetY = height / 2f + (radius * sin(angle)).toFloat() - button.height / 2f
                    button.x = width / 2f - button.width / 2f; button.y = height / 2f - button.height / 2f
                    animateButtonIn(button, targetX, targetY, index * 30L)
                }

                // FIX: Use handlePrimaryClick to allow infinite drilling (e.g. Car -> Drive)
                button.setOnClickListener { handlePrimaryClick(button, item) }

                button.setOnLongClickListener {
                    if (item.subItems.isNotEmpty()) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        activePrimaryButton = button
                        expandItem(item, button)
                        onItemSelected?.invoke(item)
                    }
                    true
                }
            }
        }
    }

    private fun handlePrimaryClick(button: ItemButton, item: CommItem) {
        // Case 1: Already Center (Confirm)
        if (activePrimaryButton == button) {
            onItemConfirmed?.invoke(item)
            return
        }

        // Case 2: Has Sub-Items (Drill Down)
        // Note: expandItem handles moving it to center and showing children
        if (item.subItems.isNotEmpty()) {
            activePrimaryButton = button
            expandItem(item, button)
            onItemSelected?.invoke(item)
            return
        }

        // Case 3: Leaf Node (Select/Confirm)
        // We still move it to center briefly for visual feedback before confirming?
        // Or just confirm. Let's move to center for consistency.
        activePrimaryButton = button
        expandItem(item, button)
        onItemSelected?.invoke(item)
    }

    private fun expandItem(item: CommItem, selectedButton: ItemButton) {
        // Fade out peers
        itemButtons.forEach { if (it != selectedButton) it.animate().alpha(0f).duration = 200 }

        // Move selected to Center
        val centerX = width / 2f - selectedButton.width / 2f
        val centerY = height / 2f - selectedButton.height / 2f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(selectedButton, "x", centerX),
                ObjectAnimator.ofFloat(selectedButton, "y", centerY),
                ObjectAnimator.ofFloat(selectedButton, "scaleX", 1.2f),
                ObjectAnimator.ofFloat(selectedButton, "scaleY", 1.2f)
            )
            duration = 300
            start()
        }

        selectedButton.showConfirmationState()

        // Spawn Sub-Items
        if (item.subItems.isNotEmpty()) {
            val subRadius = 280f
            val angleStep = 360f / item.subItems.size
            item.subItems.forEachIndexed { index, subItem ->
                val angle = Math.toRadians((angleStep * index - 90).toDouble())
                val button = createItemButton(subItem, isSubItem = true)
                button.alpha = 0f; addView(button)
                button.post {
                    val targetX = width / 2f + (subRadius * cos(angle)).toFloat() - button.width / 2f
                    val targetY = height / 2f + (subRadius * sin(angle)).toFloat() - button.height / 2f
                    button.x = width / 2f - button.width / 2f; button.y = height / 2f - button.height / 2f
                    animateButtonIn(button, targetX, targetY, index * 30L)
                }
                // Recursion!
                button.setOnClickListener { handlePrimaryClick(button, subItem) }
            }
        } else {
            // Leaf Node: Confirm immediately after animation?
            onItemConfirmed?.invoke(item)
        }
    }

    private fun createItemButton(item: CommItem, isSubItem: Boolean = false): ItemButton {
        val size = if (isSubItem) 200 else 220
        return ItemButton(context).apply {
            this.item = item
            layoutParams = LayoutParams(size, size)
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL

            val emojiView = TextView(context).apply {
                text = item.emoji
                textSize = if (isSubItem) 32f else 40f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)

                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(item.getColorInt())
                bg.setStroke(4, Color.WHITE)
                background = bg

                val circleSize = if (isSubItem) 140 else 160
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize)
            }

            val labelView = TextView(context).apply {
                text = item.label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            }

            addView(emojiView)
            addView(labelView)
            this.emojiView = emojiView
            this.labelView = labelView
        }
    }

    private fun animateButtonIn(button: View, targetX: Float, targetY: Float, delay: Long) {
        val animX = ObjectAnimator.ofFloat(button, "x", targetX)
        val animY = ObjectAnimator.ofFloat(button, "y", targetY)
        val animAlpha = ObjectAnimator.ofFloat(button, "alpha", 1f)
        val animScaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f)
        val animScaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f)
        AnimatorSet().apply {
            playTogether(animX, animY, animAlpha, animScaleX, animScaleY)
            duration = 300; startDelay = delay; start()
        }
    }

    inner class ItemButton(context: Context) : LinearLayout(context) {
        var item: CommItem? = null
        var emojiView: TextView? = null
        var labelView: TextView? = null
        fun showConfirmationState() {
            labelView?.text = "✅ ${item?.label}"
            labelView?.setTextColor(Color.GREEN)
        }
    }
}