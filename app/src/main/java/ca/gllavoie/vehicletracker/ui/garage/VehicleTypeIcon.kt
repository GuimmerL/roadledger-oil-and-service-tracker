package ca.gllavoie.vehicletracker.ui.garage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gllavoie.vehicletracker.domain.vehicle.VehicleType

@Composable
fun VehicleTypeIcon(type: VehicleType, modifier: Modifier = Modifier, iconSize: Dp = 56.dp) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(iconSize).semantics { contentDescription = "${type.label} vehicle" }) {
        val unit = minOf(size.width, size.height)
        val stroke = Stroke(width = unit * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun outline(points: List<Pair<Float, Float>>, closed: Boolean = false) {
            val first = point(points.first().first, points.first().second)
            val path = Path().apply {
                moveTo(first.x, first.y)
                points.drop(1).forEach { (x, y) -> point(x, y).also { lineTo(it.x, it.y) } }
                if (closed) close()
            }
            drawPath(path, color, style = stroke)
        }
        fun wheel(x: Float, y: Float, radius: Float = 0.105f) {
            drawCircle(color, unit * radius, point(x, y), style = stroke)
        }

        when (type) {
            VehicleType.MOTORCYCLE -> {
                wheel(0.22f, 0.75f, 0.12f)
                wheel(0.78f, 0.75f, 0.12f)
                outline(listOf(0.22f to 0.75f, 0.40f to 0.53f, 0.60f to 0.75f, 0.78f to 0.75f))
                outline(listOf(0.40f to 0.53f, 0.58f to 0.50f, 0.69f to 0.58f))
                outline(listOf(0.58f to 0.50f, 0.66f to 0.36f, 0.78f to 0.36f))
                outline(listOf(0.39f to 0.52f, 0.33f to 0.43f, 0.25f to 0.43f))
            }
            VehicleType.CONVERTIBLE -> {
                outline(listOf(
                    0.09f to 0.68f, 0.15f to 0.62f, 0.28f to 0.59f, 0.38f to 0.60f,
                    0.45f to 0.63f, 0.69f to 0.63f, 0.78f to 0.59f, 0.88f to 0.62f,
                    0.92f to 0.69f, 0.89f to 0.78f, 0.12f to 0.78f, 0.08f to 0.72f
                ), closed = true)
                // Low windshield and seat backs; the empty space above them is the open cabin.
                outline(listOf(0.37f to 0.60f, 0.41f to 0.43f, 0.45f to 0.42f))
                outline(listOf(0.54f to 0.62f, 0.54f to 0.50f, 0.60f to 0.50f, 0.62f to 0.62f))
                outline(listOf(0.63f to 0.62f, 0.63f to 0.50f, 0.69f to 0.50f, 0.71f to 0.62f))
                outline(listOf(0.45f to 0.43f, 0.51f to 0.60f))
                wheel(0.27f, 0.77f)
                wheel(0.74f, 0.77f)
            }
            VehicleType.PICKUP -> {
                outline(listOf(
                    0.08f to 0.65f, 0.29f to 0.61f, 0.36f to 0.56f, 0.68f to 0.56f,
                    0.69f to 0.62f, 0.87f to 0.62f, 0.92f to 0.69f, 0.89f to 0.78f,
                    0.12f to 0.78f, 0.08f to 0.71f
                ), closed = true)
                outline(listOf(0.34f to 0.56f, 0.40f to 0.34f, 0.61f to 0.34f, 0.69f to 0.56f))
                outline(listOf(0.43f to 0.53f, 0.47f to 0.38f, 0.52f to 0.38f, 0.52f to 0.53f), closed = true)
                outline(listOf(0.54f to 0.38f, 0.60f to 0.38f, 0.66f to 0.53f, 0.54f to 0.53f), closed = true)
                outline(listOf(0.69f to 0.57f, 0.70f to 0.61f, 0.87f to 0.61f))
                wheel(0.27f, 0.77f)
                wheel(0.74f, 0.77f)
            }
            VehicleType.HATCHBACK -> {
                outline(listOf(
                    0.08f to 0.67f, 0.13f to 0.61f, 0.24f to 0.57f, 0.78f to 0.57f,
                    0.84f to 0.60f, 0.90f to 0.65f, 0.90f to 0.77f, 0.86f to 0.79f,
                    0.12f to 0.79f, 0.08f to 0.72f
                ), closed = true)
                // The sloped hatch ends in a distinctly vertical, flat rear edge.
                outline(listOf(0.25f to 0.57f, 0.34f to 0.34f, 0.58f to 0.34f, 0.80f to 0.48f, 0.89f to 0.56f, 0.89f to 0.64f))
                outline(listOf(0.39f to 0.53f, 0.43f to 0.38f, 0.51f to 0.38f, 0.51f to 0.53f), closed = true)
                outline(listOf(0.54f to 0.38f, 0.58f to 0.38f, 0.80f to 0.50f, 0.84f to 0.54f, 0.54f to 0.53f), closed = true)
                outline(listOf(0.89f to 0.56f, 0.89f to 0.64f))
                wheel(0.27f, 0.78f)
                wheel(0.73f, 0.78f)
            }
            VehicleType.WAGON -> {
                outline(listOf(
                    0.06f to 0.66f, 0.11f to 0.60f, 0.22f to 0.56f, 0.84f to 0.56f,
                    0.91f to 0.60f, 0.95f to 0.66f, 0.95f to 0.77f, 0.91f to 0.79f,
                    0.10f to 0.79f, 0.06f to 0.71f
                ), closed = true)
                // Shares the hatchback's long roof, but the cargo area is extended before a flat tailgate.
                outline(listOf(0.22f to 0.56f, 0.28f to 0.31f, 0.72f to 0.31f, 0.84f to 0.43f, 0.84f to 0.57f))
                outline(listOf(0.33f to 0.52f, 0.37f to 0.35f, 0.48f to 0.35f, 0.48f to 0.52f), closed = true)
                outline(listOf(0.51f to 0.35f, 0.62f to 0.35f, 0.62f to 0.52f, 0.51f to 0.52f), closed = true)
                outline(listOf(0.65f to 0.35f, 0.70f to 0.35f, 0.80f to 0.45f, 0.80f to 0.52f, 0.65f to 0.52f), closed = true)
                outline(listOf(0.84f to 0.44f, 0.84f to 0.64f))
                wheel(0.25f, 0.78f)
                wheel(0.76f, 0.78f)
            }
            VehicleType.SUV -> {
                outline(listOf(
                    0.06f to 0.65f, 0.10f to 0.57f, 0.23f to 0.53f, 0.83f to 0.53f,
                    0.91f to 0.58f, 0.95f to 0.66f, 0.93f to 0.78f, 0.10f to 0.78f,
                    0.06f to 0.71f
                ), closed = true)
                // Taller, boxier cabin and higher ground clearance distinguish this from the wagon.
                outline(listOf(0.23f to 0.53f, 0.26f to 0.22f, 0.71f to 0.22f, 0.83f to 0.39f, 0.83f to 0.54f))
                outline(listOf(0.31f to 0.49f, 0.34f to 0.27f, 0.49f to 0.27f, 0.49f to 0.49f), closed = true)
                outline(listOf(0.52f to 0.27f, 0.66f to 0.27f, 0.79f to 0.41f, 0.79f to 0.49f, 0.52f to 0.49f), closed = true)
                outline(listOf(0.83f to 0.40f, 0.83f to 0.64f))
                wheel(0.25f, 0.77f, 0.115f)
                wheel(0.77f, 0.77f, 0.115f)
            }
            VehicleType.VAN -> {
                outline(listOf(
                    0.07f to 0.66f, 0.10f to 0.56f, 0.19f to 0.53f, 0.82f to 0.53f,
                    0.90f to 0.58f, 0.94f to 0.67f, 0.92f to 0.78f, 0.10f to 0.78f,
                    0.07f to 0.71f
                ), closed = true)
                outline(listOf(0.19f to 0.53f, 0.20f to 0.24f, 0.72f to 0.24f, 0.82f to 0.53f))
                outline(listOf(0.25f to 0.49f, 0.26f to 0.29f, 0.43f to 0.29f, 0.43f to 0.49f), closed = true)
                outline(listOf(0.46f to 0.29f, 0.62f to 0.29f, 0.70f to 0.49f, 0.46f to 0.49f), closed = true)
                outline(listOf(0.45f to 0.29f, 0.45f to 0.52f))
                wheel(0.25f, 0.78f)
                wheel(0.76f, 0.78f)
            }
            VehicleType.COUPE -> {
                outline(listOf(
                    0.09f to 0.68f, 0.15f to 0.61f, 0.28f to 0.57f, 0.77f to 0.57f,
                    0.86f to 0.61f, 0.91f to 0.69f, 0.88f to 0.78f, 0.12f to 0.78f,
                    0.08f to 0.72f
                ), closed = true)
                outline(listOf(0.28f to 0.57f, 0.40f to 0.37f, 0.65f to 0.37f, 0.77f to 0.57f))
                outline(listOf(0.38f to 0.53f, 0.44f to 0.41f, 0.52f to 0.41f, 0.52f to 0.53f), closed = true)
                outline(listOf(0.55f to 0.41f, 0.64f to 0.41f, 0.73f to 0.53f, 0.55f to 0.53f), closed = true)
                wheel(0.27f, 0.78f)
                wheel(0.73f, 0.78f)
            }
            else -> {
                outline(listOf(
                    0.09f to 0.67f, 0.15f to 0.61f, 0.25f to 0.57f, 0.76f to 0.57f,
                    0.86f to 0.61f, 0.91f to 0.70f, 0.88f to 0.78f, 0.12f to 0.78f,
                    0.08f to 0.72f
                ), closed = true)
                outline(listOf(0.25f to 0.57f, 0.36f to 0.36f, 0.63f to 0.36f, 0.76f to 0.57f))
                outline(listOf(0.38f to 0.53f, 0.43f to 0.40f, 0.50f to 0.40f, 0.50f to 0.53f), closed = true)
                outline(listOf(0.53f to 0.40f, 0.62f to 0.40f, 0.71f to 0.53f, 0.53f to 0.53f), closed = true)
                wheel(0.27f, 0.78f)
                wheel(0.73f, 0.78f)
            }
        }
    }
}
