package com.greenzean.animekmp.composeapp.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Search: ImageVector by lazy {
        svgIcon(
            "Search",
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16" +
                "c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5" +
                "S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
        )
    }

    val Star: ImageVector by lazy {
        svgIcon("Star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
    }

    val StarOutline: ImageVector by lazy {
        svgIcon(
            "StarOutline",
            "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24z" +
                "M12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38" +
                "-3.32,2.88 1,4.28L12,15.4z",
        )
    }

    val Settings: ImageVector by lazy {
        svgIcon(
            "Settings",
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
                "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
                "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
                "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33" +
                "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
                "C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
                "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
                "c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
                "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
                "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6" +
                "s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
        )
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            moveTo(19f, 13f)
            horizontalLineToRelative(-6f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-6f)
            horizontalLineTo(5f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(6f)
            verticalLineTo(5f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            close()
        }
    }

    val Remove: ImageVector by lazy {
        icon("Remove") {
            moveTo(19f, 13f)
            horizontalLineTo(5f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(14f)
            close()
        }
    }

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            verticalLineToRelative(-2f)
            close()
        }
    }

    val Person: ImageVector by lazy {
        icon("Person") {
            moveTo(12f, 12f)
            curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
            reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
            reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            close()
            moveTo(12f, 14f)
            curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-2f)
            curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
            close()
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            moveTo(1f, 21f)
            horizontalLineToRelative(22f)
            lineTo(12f, 2f)
            lineTo(1f, 21f)
            close()
            moveTo(13f, 18f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(13f, 14f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4f)
            close()
        }
    }

    val List: ImageVector by lazy {
        icon("List") {
            moveTo(4f, 6f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 11f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 16f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
        }
    }
}

private fun svgIcon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
        .build()

private fun icon(
    name: String,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)
        .build()
