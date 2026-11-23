package com.e4eheh.e4ehehworkout.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Chamfered 45-degree HUD polygon shape for Batcomputer panels and controls. */
class ChamferedCornerShape(private val chamferSize: Dp = 10.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cut = with(density) { chamferSize.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height - cut)
            lineTo(size.width - cut, size.height)
            lineTo(cut, size.height)
            lineTo(0f, size.height - cut)
            lineTo(0f, cut)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is ChamferedCornerShape && chamferSize == other.chamferSize
    }

    override fun hashCode(): Int = chamferSize.hashCode()
}

object ArkhamShapes {
    val panel: Shape = ChamferedCornerShape(10.dp)
    val chip: Shape = ChamferedCornerShape(6.dp)
    val fab: Shape = ChamferedCornerShape(14.dp)
}
