package com.example.besu

data class Emotion(
    val id: String,
    val emoji: String,
    val label: String,
    val phrase: String,
    val subEmotions: List<Emotion> = emptyList()
)

object EmotionData {
    // Helper for gestures
    fun getPhraseForEmoji(emoji: String): String {
        val found = findEmotionByEmoji(primaryEmotions, emoji)
        if (found != null) return found.phrase

        return when (emoji) {
            "👋" -> "Hello there."
            "✋" -> "Please stop."
            "🚫" -> "No. I don't want that."
            "👍" -> "That is good."
            else -> ""
        }
    }

    private fun findEmotionByEmoji(list: List<Emotion>, emoji: String): Emotion? {
        for (e in list) {
            if (e.emoji == emoji) return e
            val sub = findEmotionByEmoji(e.subEmotions, emoji)
            if (sub != null) return sub
        }
        return null
    }

    val primaryEmotions = listOf(
        // 1. HAPPY (Expanded)
        Emotion(
            id = "happy",
            emoji = "😊",
            label = "Happy",
            phrase = "I am feeling happy today.",
            subEmotions = listOf(
                Emotion("excited", "🤩", "Excited", "I am so excited!"),
                Emotion("proud", "🦁", "Proud", "I am proud of myself."),
                Emotion("grateful", "🙏", "Grateful", "I am very grateful."),
                Emotion("laughing", "😂", "Funny", "That is very funny."),
                Emotion("love", "❤️", "Love", "I love this.")
            )
        ),
        // 2. CALM (New Category)
        Emotion(
            id = "calm",
            emoji = "😌",
            label = "Calm",
            phrase = "I feel calm and relaxed.",
            subEmotions = listOf(
                Emotion("safe", "🛡️", "Safe", "I feel safe here."),
                Emotion("comfortable", "🛋️", "Comfy", "I am comfortable."),
                Emotion("peaceful", "🕊️", "Peaceful", "It is peaceful.")
            )
        ),
        // 3. SURPRISED
        Emotion(
            id = "surprised",
            emoji = "😮",
            label = "Surprised",
            phrase = "Wow, I am surprised.",
            subEmotions = listOf(
                Emotion("amazed", "✨", "Amazed", "This is amazing!"),
                Emotion("startled", "🫣", "Startled", "You startled me.")
            )
        ),
        // 4. NEEDS (Functional / Neutral Positive)
        Emotion(
            id = "needs",
            emoji = "🙋",
            label = "Needs",
            phrase = "I need something.",
            subEmotions = listOf(
                Emotion("hungry", "🍎", "Hungry", "I am hungry."),
                Emotion("thirsty", "💧", "Thirsty", "I am thirsty."),
                Emotion("rest", "🛏️", "Rest", "I need to rest.")
            )
        ),
        // 5. ANXIOUS (Keeping existing utilitarian ones)
        Emotion(
            id = "anxious",
            emoji = "😟",
            label = "Anxious",
            phrase = "I am feeling anxious.",
            subEmotions = listOf(
                Emotion("loud", "🔊", "Loud", "It is too loud."),
                Emotion("bright", "💡", "Bright", "It is too bright."),
                Emotion("space", "✋", "Space", "I need some space.")
            )
        ),
        // 6. CONFUSED/NO
        Emotion(
            id = "confused",
            emoji = "😕",
            label = "Confused",
            phrase = "I am confused.",
            subEmotions = listOf(
                Emotion("help", "❓", "Help", "Please help me."),
                Emotion("no", "🚫", "No", "No, thank you.")
            )
        )
    )
}