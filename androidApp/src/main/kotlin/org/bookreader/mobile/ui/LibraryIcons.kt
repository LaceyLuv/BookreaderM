package org.bookreader.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Dependency-free vector icons built with androidx.compose.ui.graphics.vector.ImageVector
 * so no extra icon artifact (such as material-icons-extended) is required.
 */
internal object LibraryIcons {
    private val strokeBrush = SolidColor(Color.Black)

    val Shelf: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryShelf",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 5.5f)
                curveTo(6.5f, 4.5f, 9.5f, 4.5f, 12f, 6f)
                curveTo(14.5f, 4.5f, 17.5f, 4.5f, 20f, 5.5f)
                verticalLineTo(18.5f)
                curveTo(17.5f, 17.5f, 14.5f, 17.5f, 12f, 19f)
                curveTo(9.5f, 17.5f, 6.5f, 17.5f, 4f, 18.5f)
                close()
                moveTo(12f, 6f)
                verticalLineTo(19f)
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibrarySearch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(17.5f, 11f)
                arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.5f, 11f)
                arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.5f, 11f)
                moveTo(15.8f, 15.8f)
                lineTo(20f, 20f)
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibrarySettings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 7f)
                horizontalLineTo(9f)
                moveTo(13f, 7f)
                horizontalLineTo(20f)
                moveTo(13f, 7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13f, 7f)

                moveTo(4f, 17f)
                horizontalLineTo(13f)
                moveTo(17f, 17f)
                horizontalLineTo(20f)
                moveTo(17f, 17f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13f, 17f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17f, 17f)
            }
        }.build()
    }

    val Add: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryAdd",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 20f,
            viewportHeight = 20f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(10f, 4.5f)
                verticalLineTo(15.5f)
                moveTo(4.5f, 10f)
                horizontalLineTo(15.5f)
            }
        }.build()
    }

    val GridView: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryGridView",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 20f,
            viewportHeight = 20f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.7f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 3.5f); horizontalLineTo(8.5f); verticalLineTo(8.5f); horizontalLineTo(3.5f); close()
                moveTo(11.5f, 3.5f); horizontalLineTo(16.5f); verticalLineTo(8.5f); horizontalLineTo(11.5f); close()
                moveTo(3.5f, 11.5f); horizontalLineTo(8.5f); verticalLineTo(16.5f); horizontalLineTo(3.5f); close()
                moveTo(11.5f, 11.5f); horizontalLineTo(16.5f); verticalLineTo(16.5f); horizontalLineTo(11.5f); close()
            }
        }.build()
    }

    val ListView: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryListView",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 20f,
            viewportHeight = 20f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 5.5f); horizontalLineTo(16f)
                moveTo(4f, 10f); horizontalLineTo(16f)
                moveTo(4f, 14.5f); horizontalLineTo(16f)
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryChevronRight",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 20f,
            viewportHeight = 20f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.5f, 5f)
                lineTo(12.5f, 10f)
                lineTo(7.5f, 15f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryClose",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 20f,
            viewportHeight = 20f,
        ).apply {
            path(
                stroke = strokeBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5.5f, 5.5f)
                lineTo(14.5f, 14.5f)
                moveTo(14.5f, 5.5f)
                lineTo(5.5f, 14.5f)
            }
        }.build()
    }
}
