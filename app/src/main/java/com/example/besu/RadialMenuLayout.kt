package com.example.besu

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import kotlin.math.cos
import kotlin.math.sin

class RadialMenuLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val emotionButtons = mutableListOf<EmotionButton>()
    private var activePrimaryButton: EmotionButton? = null

    var onEmotionSelected: ((Emotion) -> Unit)? = null
    var onSubEmotionSelected: ((Emotion) -> Unit)? = null
    var onPrimaryConfirmed: ((Emotion) -> Unit)? = null // New Callback

    fun setEmotions(emotions: List<Emotion>) {
        removeAllViews()
        emotionButtons.clear()
        activePrimaryButton = null

        val radius = 300f
        val angleStep = 360f / emotions.size

        emotions.forEachIndexed { index, emotion ->
            val angle = Math.toRadians((angleStep * index).toDouble())

            val button = createEmotionButton(emotion)
            button.alpha = 0f
            button.scaleX = 0f
            button.scaleY = 0f

            addView(button)
            emotionButtons.add(button)

            button.post {
                val centerX = width / 2f
                val centerY = height / 2f

                val x = centerX + (radius * cos(angle)).toFloat() - button.width / 2f
                val y = centerY + (radius * sin(angle)).toFloat() - button.height / 2f

                button.x = centerX - button.width / 2f
                button.y = centerY - button.height / 2f

                animateButtonIn(button, x, y, index * 50L)
            }

            button.setOnClickListener {
                handlePrimaryClick(button, emotion)
            }
        }
    }

    private fun handlePrimaryClick(button: EmotionButton, emotion: Emotion) {
        // If this button is ALREADY active, the user is tapping it a second time.
        // This means they want to confirm the BASE emotion.
        if (activePrimaryButton == button) {
            onPrimaryConfirmed?.invoke(emotion)
            return
        }

        // Otherwise, expand it
        activePrimaryButton = button
        expandEmotion(emotion, button)
        onEmotionSelected?.invoke(emotion)
    }

    private fun expandEmotion(emotion: Emotion, selectedButton: EmotionButton) {
        // 1. Fade out non-selected primary buttons
        emotionButtons.forEach { button ->
            if (button != selectedButton) {
                ObjectAnimator.ofFloat(button, "alpha", 0f).apply {
                    duration = 200
                    start()
                }
            }
        }

        // 2. Move Selected Button to Center
        val centerX = width / 2f - selectedButton.width / 2f
        val centerY = height / 2f - selectedButton.height / 2f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(selectedButton, "x", centerX),
                ObjectAnimator.ofFloat(selectedButton, "y", centerY),
                ObjectAnimator.ofFloat(selectedButton, "scaleX", 1.2f), // Make it bigger
                ObjectAnimator.ofFloat(selectedButton, "scaleY", 1.2f)
            )
            duration = 300
            start()
        }

        // Change visuals to indicate "Tap again to confirm"
        selectedButton.text = "${emotion.emoji}\n✅"
        selectedButton.textSize = 24f

        // 3. Spawn Sub-Emotions around it
        if (emotion.subEmotions.isNotEmpty()) {
            val subRadius = 250f
            val angleStep = 360f / emotion.subEmotions.size

            emotion.subEmotions.forEachIndexed { index, subEmotion ->
                val angle = Math.toRadians((angleStep * index - 90).toDouble()) // Start from top

                val button = createEmotionButton(subEmotion, isSubEmotion = true)
                button.alpha = 0f
                addView(button)

                button.post {
                    // Start at center (behind the primary)
                    button.x = width / 2f - button.width / 2f
                    button.y = height / 2f - button.height / 2f

                    // Target position
                    val targetX = width / 2f + (subRadius * cos(angle)).toFloat() - button.width / 2f
                    val targetY = height / 2f + (subRadius * sin(angle)).toFloat() - button.height / 2f

                    animateButtonIn(button, targetX, targetY, index * 30L)
                }

                button.setOnClickListener {
                    onSubEmotionSelected?.invoke(subEmotion)
                }
            }
        } else {
            // If no sub-emotions, confirm immediately?
            // Or just let the center tap handle it.
            // Let's auto-confirm for leaf nodes to save a tap.
            onPrimaryConfirmed?.invoke(emotion)
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
            duration = 300
            startDelay = delay
            start()
        }
    }

    private fun createEmotionButton(emotion: Emotion, isSubEmotion: Boolean = false): EmotionButton {
        val size = if (isSubEmotion) 140 else 160 // Made buttons slightly larger

        return EmotionButton(context).apply {
            this.emotion = emotion
            layoutParams = LayoutParams(size, size)
            gravity = Gravity.CENTER
            text = emotion.emoji
            textSize = if (isSubEmotion) 32f else 40f
            setTypeface(null, Typeface.BOLD)

            // Visual distinction
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.OVAL
            bg.setColor(if (isSubEmotion) Color.parseColor("#FF9800") else Color.parseColor("#2196F3")) // Orange sub, Blue main
            bg.setStroke(4, Color.WHITE)
            background = bg

            setTextColor(Color.WHITE)
            elevation = 12f
        }
    }

    inner class EmotionButton(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {
        var emotion: Emotion? = null
    }
}