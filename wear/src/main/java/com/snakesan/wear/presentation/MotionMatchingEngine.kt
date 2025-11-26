package com.example.besu.wear

import kotlin.math.min

object MotionMatchingEngine {

    // Fast DTW implementation
    fun calculateDTW(candidate: List<MotionSample>, template: List<MotionSample>): Float {
        val n = candidate.size
        val m = template.size

        // Window constraint to prevent pathologically long warping (optimization)
        val w = 10.coerceAtLeast(kotlin.math.abs(n - m))

        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            for (j in 1.coerceAtLeast(i - w)..min(m, i + w)) {
                val cost = candidate[i - 1].distanceTo(template[j - 1])
                dtw[i][j] = cost + min(
                    dtw[i - 1][j],    // Insertion
                    min(
                        dtw[i][j - 1],    // Deletion
                        dtw[i - 1][j - 1] // Match
                    )
                )
            }
        }

        return dtw[n][m] / (n + m) // Normalized distance
    }

    // Returns the ID of the best match if it's below the threshold, else null
    fun match(buffer: List<MotionSample>, profile: MotionProfile): String? {
        if (profile.exemplars.isEmpty()) return null

        var bestDist = Float.MAX_VALUE
        var bestId: String? = null

        for (exemplar in profile.exemplars) {
            // Optimization: Skip if length difference is too massive (>50%)
            val lenDiff = kotlin.math.abs(buffer.size - exemplar.samples.size)
            if (lenDiff.toFloat() / exemplar.samples.size > 0.5f) continue

            val dist = calculateDTW(buffer, exemplar.samples)

            // "Reward" matches that are distinct.
            // If dist is low, it's a match.
            if (dist < bestDist) {
                bestDist = dist
                bestId = exemplar.id
            }
        }

        return if (bestDist < profile.sensitivity) bestId else null
    }
}