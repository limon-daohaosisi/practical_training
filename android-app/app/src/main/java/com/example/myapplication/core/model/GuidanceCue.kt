package com.example.myapplication.core.model

sealed interface GuidanceCue {
    data object Hidden : GuidanceCue

    data class TapTarget(
        val label: String,
        val bounds: NormalizedBounds,
    ) : GuidanceCue

    data object ScrollHint : GuidanceCue
}
