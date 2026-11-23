package com.e4eheh.e4ehehworkout.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Lightweight custom tactical HUD vector icons (no material-icons-extended dependency). */
object ArkhamIcons {
    val Home: ImageVector by lazy {
        tacticalIcon("ArkhamHome") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(3f, 11f)
                lineTo(12f, 3.5f)
                lineTo(21f, 11f)
                moveTo(5.5f, 9.5f)
                lineTo(5.5f, 20f)
                lineTo(18.5f, 20f)
                lineTo(18.5f, 9.5f)
                moveTo(10f, 20f)
                lineTo(10f, 14f)
                lineTo(14f, 14f)
                lineTo(14f, 20f)
            }
        }
    }

    val Workout: ImageVector by lazy {
        tacticalIcon("ArkhamWorkout") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(3f, 9f)
                lineTo(6f, 9f)
                lineTo(6f, 15f)
                lineTo(3f, 15f)
                close()
                moveTo(18f, 9f)
                lineTo(21f, 9f)
                lineTo(21f, 15f)
                lineTo(18f, 15f)
                close()
                moveTo(6f, 7f)
                lineTo(8.5f, 7f)
                lineTo(8.5f, 17f)
                lineTo(6f, 17f)
                close()
                moveTo(15.5f, 7f)
                lineTo(18f, 7f)
                lineTo(18f, 17f)
                lineTo(15.5f, 17f)
                close()
                moveTo(8.5f, 12f)
                lineTo(15.5f, 12f)
            }
        }
    }

    val Progress: ImageVector by lazy {
        tacticalIcon("ArkhamProgress") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(4f, 4f)
                lineTo(4f, 19.5f)
                lineTo(20f, 19.5f)
                moveTo(7f, 15f)
                lineTo(10.5f, 11f)
                lineTo(14f, 13.5f)
                lineTo(19f, 7f)
            }
        }
    }

    val Profile: ImageVector by lazy {
        tacticalIcon("ArkhamProfile") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(12f, 4f)
                lineTo(15.5f, 6.5f)
                lineTo(15.5f, 10f)
                lineTo(12f, 12.5f)
                lineTo(8.5f, 10f)
                lineTo(8.5f, 6.5f)
                close()
                moveTo(4.5f, 20f)
                lineTo(6.5f, 15.5f)
                lineTo(17.5f, 15.5f)
                lineTo(19.5f, 20f)
            }
        }
    }

    val Add: ImageVector by lazy {
        tacticalIcon("ArkhamAdd") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Square,
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Edit: ImageVector by lazy {
        tacticalIcon("ArkhamEdit") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(4f, 20f)
                lineTo(8.5f, 19f)
                lineTo(19f, 8.5f)
                lineTo(15.5f, 5f)
                lineTo(5f, 15.5f)
                close()
                moveTo(13.5f, 7f)
                lineTo(17f, 10.5f)
            }
        }
    }

    val Delete: ImageVector by lazy {
        tacticalIcon("ArkhamDelete") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(4.5f, 7f)
                lineTo(19.5f, 7f)
                moveTo(9f, 7f)
                lineTo(9f, 4.5f)
                lineTo(15f, 4.5f)
                lineTo(15f, 7f)
                moveTo(6.5f, 7f)
                lineTo(7.5f, 19.5f)
                lineTo(16.5f, 19.5f)
                lineTo(17.5f, 7f)
                moveTo(10f, 10.5f)
                lineTo(10f, 16.5f)
                moveTo(14f, 10.5f)
                lineTo(14f, 16.5f)
            }
        }
    }

    private fun tacticalIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()
}
