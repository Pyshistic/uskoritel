package app.uskoritel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Good = Color(0xFF34E0A1)
val Warn = Color(0xFFFFC857)
val Bad = Color(0xFFFF6B6B)

private val Scheme = darkColorScheme(
    primary = Good,
    onPrimary = Color(0xFF00281A),
    secondary = Color(0xFF5AB8FF),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF1A1F27),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainer = Color(0xFF151A21),
    secondaryContainer = Color(0xFF1F3A30),
    onSecondaryContainer = Good,
    error = Bad,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, content = content)

/** Зелёный — хорошо, жёлтый — терпимо, красный — плохо (для «занятости»). */
fun loadColor(percent: Int): Color = when {
    percent < 60 -> Good
    percent < 85 -> Warn
    else -> Bad
}

/** Для заряда батареи наоборот: много — хорошо. */
fun chargeColor(percent: Int): Color = when {
    percent > 50 -> Good
    percent > 20 -> Warn
    else -> Bad
}

@Composable
fun ScreenTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
    }
}

@Composable
fun Gauge(
    percent: Int,
    title: String,
    subtitle: String,
    spinning: Boolean,
    color: Color = loadColor(percent),
    modifier: Modifier = Modifier.size(230.dp),
) {
    val animated by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        animationSpec = tween(800),
        label = "gauge",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val spin = if (spinning) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "angle",
        ).value
    } else {
        0f
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val half = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(half, half), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, 135f, 270f * animated, false, Offset(half, half), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (spinning) {
                val inner = stroke * 2f
                drawArc(
                    color.copy(alpha = 0.5f), spin, 70f, false,
                    Offset(inner, inner),
                    Size(size.width - inner * 2, size.height - inner * 2),
                    style = Stroke(stroke / 3, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
