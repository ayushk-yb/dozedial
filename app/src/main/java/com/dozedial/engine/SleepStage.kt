package com.dozedial.engine

/**
 * Autonomic sleep stage classifications derived from continuous 1 Hz heart rate dynamics.
 *
 * Supported stages align with standard polysomnography (PSG) sleep staging:
 * - [WAKE]: Conscious wakefulness or nocturnal autonomic arousal spike.
 * - [REM]: Rapid Eye Movement sleep; elevated sympathetic tone with rapid autonomic oscillations.
 * - [LIGHT]: N1/N2 light transitional sleep; moderate autonomic variability.
 * - [DEEP]: N3 Slow-Wave Sleep; parasympathetic nadir with minimal rolling variance.
 */
enum class SleepStage(
    val displayName: String,
    val shortName: String,
    val description: String,
    val colorHex: Long
) {
    WAKE(
        displayName = "Wake / Arousal",
        shortName = "WAKE",
        description = "Elevated tone or sudden arousal spike",
        colorHex = 0xFFF43F5E // Sunrise Coral
    ),
    REM(
        displayName = "REM Sleep",
        shortName = "REM",
        description = "Dream state with elevated oscillatory dynamics",
        colorHex = 0xFFA855F7 // Radiant Violet
    ),
    LIGHT(
        displayName = "Light Sleep",
        shortName = "LIGHT",
        description = "Optimal wake-up stage; moderate baseline variance",
        colorHex = 0xFF38BDF8 // Celestial Sky Cyan
    ),
    DEEP(
        displayName = "Deep Sleep",
        shortName = "DEEP",
        description = "Slow-wave restorative sleep; parasympathetic nadir",
        colorHex = 0xFF6366F1 // Deep Neon Indigo
    );

    val isOptimalForWaking: Boolean
        get() = this == LIGHT || this == WAKE
}
