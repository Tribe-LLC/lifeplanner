// Generated from the approved v4 canvas (Illo.dc.html) by illo2kt.py. Edit the canvas drawing and
// regenerate rather than hand-editing paths here.
package az.tribe.lifeplanner.ui.v4.illustration

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea

/** The colours an area illustration is drawn with; everything else in the drawing is fixed. */
data class IlloPalette(
    /** The soft disc behind the drawing, the area's tint. */
    val blob: Color,
    /** Outlines. */
    val ink: Color,
    /** Paper-coloured fills (cards, pages, clouds). */
    val paper: Color,
    /** Light strokes drawn over a coloured shape (the suitcase straps). */
    val highlight: Color,
)

internal fun buildAreaIllustration(area: PlanArea, palette: IlloPalette): ImageVector = when (area) {
        PlanArea.HABITS -> habits(palette)
        PlanArea.FITNESS -> fitness(palette)
        PlanArea.MONEY -> money(palette)
        PlanArea.TRAVEL -> travel(palette)
        PlanArea.STUDY -> study(palette)
        PlanArea.MEALS -> meals(palette)
        PlanArea.MIND -> mind(palette)
        PlanArea.CAREER -> career(palette)
}

private fun nodes(d: String): List<PathNode> = PathParser().parsePathString(d).toNodes()

private fun habits(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_habits", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        addPath(pathData = nodes("M31 24H65A9 9 0 0 1 74 33V67A9 9 0 0 1 65 76H31A9 9 0 0 1 22 67V33A9 9 0 0 1 31 24Z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M22 37h52"), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M35 18v11M61 18v11"), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round)
        addPath(pathData = nodes("M31 50A5 5 0 1 0 41 50A5 5 0 1 0 31 50Z"), fill = SolidColor(Color(0xFF3B5BE5)))
        addPath(pathData = nodes("M43 50A5 5 0 1 0 53 50A5 5 0 1 0 43 50Z"), fill = SolidColor(Color(0xFF3B5BE5)))
        addPath(pathData = nodes("M55 50A5 5 0 1 0 65 50A5 5 0 1 0 55 50Z"), fill = SolidColor(Color(0xFF3B5BE5)))
        addPath(pathData = nodes("M31 64A5 5 0 1 0 41 64A5 5 0 1 0 31 64Z"), fill = SolidColor(Color(0xFF3B5BE5)))
        addPath(pathData = nodes("M43 64A5 5 0 1 0 53 64A5 5 0 1 0 43 64Z"), fill = SolidColor(Color(0xFF3B5BE5)))
        addPath(pathData = nodes("M55.5 64A4.5 4.5 0 1 0 64.5 64A4.5 4.5 0 1 0 55.5 64Z"), stroke = SolidColor(p.ink), strokeLineWidth = 2.5f)
    }.build()

private fun fitness(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_fitness", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        group(rotate = -30f, pivotX = 48f, pivotY = 50f) {
            addPath(pathData = nodes("M21 38H25A3 3 0 0 1 28 41V59A3 3 0 0 1 25 62H21A3 3 0 0 1 18 59V41A3 3 0 0 1 21 38Z"), fill = SolidColor(Color(0xFFEA580C)), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
            addPath(pathData = nodes("M71 38H75A3 3 0 0 1 78 41V59A3 3 0 0 1 75 62H71A3 3 0 0 1 68 59V41A3 3 0 0 1 71 38Z"), fill = SolidColor(Color(0xFFEA580C)), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
            addPath(pathData = nodes("M13 43H16A2 2 0 0 1 18 45V55A2 2 0 0 1 16 57H13A2 2 0 0 1 11 55V45A2 2 0 0 1 13 43Z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
            addPath(pathData = nodes("M80 43H83A2 2 0 0 1 85 45V55A2 2 0 0 1 83 57H80A2 2 0 0 1 78 55V45A2 2 0 0 1 80 43Z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
            addPath(pathData = nodes("M28 50h40"), stroke = SolidColor(p.ink), strokeLineWidth = 4f, strokeLineCap = StrokeCap.Round)
        }
        addPath(pathData = nodes("M72 18l2-6M80 24l6-3"), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round)
    }.build()

private fun money(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_money", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        addPath(pathData = nodes("M27 34H63A9 9 0 0 1 72 43V65A9 9 0 0 1 63 74H27A9 9 0 0 1 18 65V43A9 9 0 0 1 27 34Z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M18 44h54"), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M59 50H69A7 7 0 0 1 76 57V57A7 7 0 0 1 69 64H59A7 7 0 0 1 52 57V57A7 7 0 0 1 59 50Z"), fill = SolidColor(Color(0xFF16A34A)), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M58.5 57A2.5 2.5 0 1 0 63.5 57A2.5 2.5 0 1 0 58.5 57Z"), fill = SolidColor(p.paper))
        addPath(pathData = nodes("M59 25A11 11 0 1 0 81 25A11 11 0 1 0 59 25Z"), fill = SolidColor(Color(0xFFFACC15)), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M70 20v10"), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round)
    }.build()

private fun travel(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_travel", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        addPath(pathData = nodes("M37 38v-7a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v7"), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M32 38H58A8 8 0 0 1 66 46V66A8 8 0 0 1 58 74H32A8 8 0 0 1 24 66V46A8 8 0 0 1 32 38Z"), fill = SolidColor(Color(0xFF0891B2)), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M35 47v18M55 47v18"), stroke = SolidColor(p.highlight), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round)
        addPath(pathData = nodes("M30 79A3 3 0 1 0 36 79A3 3 0 1 0 30 79Z"), fill = SolidColor(p.ink))
        addPath(pathData = nodes("M54 79A3 3 0 1 0 60 79A3 3 0 1 0 54 79Z"), fill = SolidColor(p.ink))
        addPath(pathData = nodes("M62 27l22-10-8 20-5-7z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 2.5f, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = nodes("M71 30l-4 6"), stroke = SolidColor(p.ink), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round)
    }.build()

private fun study(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_study", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        addPath(pathData = nodes("M48 34c-8-6-18-7-28-5v38c10-2 20-1 28 5z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = nodes("M48 34c8-6 18-7 28-5v38c-10-2-20-1-28 5z"), fill = SolidColor(Color(0xFF7C3AED)), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = nodes("M27 42h13M27 50h13M27 58h9"), stroke = SolidColor(p.ink), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round)
        addPath(pathData = nodes("M48 34v38"), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
    }.build()

private fun meals(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_meals", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        addPath(pathData = nodes("M38 40c-3-4 3-6 0-10M50 40c-3-4 3-6 0-10M62 40c-3-4 3-6 0-10"), stroke = SolidColor(p.ink), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round)
        addPath(pathData = nodes("M18 50h60a30 30 0 0 1-60 0z"), fill = SolidColor(Color(0xFFF59E0B)), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = nodes("M14 50h68"), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round)
        addPath(pathData = nodes("M58 60c7 0 11-4 11-10-7 0-11 4-11 10z"), fill = SolidColor(Color(0xFF22C55E)), stroke = SolidColor(p.ink), strokeLineWidth = 2.5f, strokeLineJoin = StrokeJoin.Round)
    }.build()

private fun mind(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_mind", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        addPath(pathData = nodes("M60 20a24 24 0 1 0 17 38 20 20 0 0 1-17-38z"), fill = SolidColor(Color(0xFFC026D3)), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = nodes("M28 26l2 5 5 2-5 2-2 5-2-5-5-2 5-2z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = nodes("M22 74h36a8 8 0 0 0 0-16 12 12 0 0 0-22-2 8 8 0 0 0-14 8 5 5 0 0 0 0 10z"), fill = SolidColor(p.paper), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineJoin = StrokeJoin.Round)
    }.build()

private fun career(p: IlloPalette): ImageVector =
    ImageVector.Builder(name = "illo_career", defaultWidth = 96.dp, defaultHeight = 96.dp, viewportWidth = 96f, viewportHeight = 96f).apply {
        addPath(pathData = nodes("M8 50A40 40 0 1 0 88 50A40 40 0 1 0 8 50Z"), fill = SolidColor(p.blob))
        addPath(pathData = nodes("M36 42v-6a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v6"), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M27 42H63A7 7 0 0 1 70 49V69A7 7 0 0 1 63 76H27A7 7 0 0 1 20 69V49A7 7 0 0 1 27 42Z"), fill = SolidColor(Color(0xFF475569)), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M20 56h50"), stroke = SolidColor(p.ink), strokeLineWidth = 3f)
        addPath(pathData = nodes("M42 52H48A2 2 0 0 1 50 54V58A2 2 0 0 1 48 60H42A2 2 0 0 1 40 58V54A2 2 0 0 1 42 52Z"), fill = SolidColor(Color(0xFFFACC15)), stroke = SolidColor(p.ink), strokeLineWidth = 2.5f)
        addPath(pathData = nodes("M62 30l13-13M67 17h8v8"), stroke = SolidColor(p.ink), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
