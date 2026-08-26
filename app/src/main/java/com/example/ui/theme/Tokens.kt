package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Spacing scale (4dp grid)
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// Corner radius scale
object Radii {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(50)
}

// Minimum accessible touch target per Material guidelines
val MinTouchTarget = 48.dp

// Minimum readable label size
val MinFontSize = 12.sp
