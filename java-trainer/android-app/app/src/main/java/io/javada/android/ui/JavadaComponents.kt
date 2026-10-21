package io.javada.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.javada.android.data.ItemStatus
import io.javada.android.ui.theme.LocalJavadaColors

@Composable
fun JavadaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalJavadaColors.current
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(containerColor = containerColor ?: palette.surface)
    val border = BorderStroke(1.dp, palette.line)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 1.dp)
    if (onClick == null) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    } else {
        Card(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = LocalJavadaColors.current.greenDeep,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.45.sp,
        lineHeight = 14.sp
    )
}

@Composable
fun PageHeading(
    overline: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Eyebrow(overline)
        Text(
            title,
            color = LocalJavadaColors.current.text,
            fontSize = 27.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp
        )
        Text(
            description,
            color = LocalJavadaColors.current.mutedStrong,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
fun SectionHeading(
    overline: String,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow(overline)
            Text(title, color = LocalJavadaColors.current.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = LocalJavadaColors.current.mutedStrong, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun JavadaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
    trailing: String? = null,
    white: Boolean = false,
    minHeight: Dp = 47.dp
) {
    val palette = LocalJavadaColors.current
    val container = if (white) Color.White else palette.green
    val content = if (white) Color(0xFF31572A) else Color.White
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        enabled = enabled,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = palette.line,
            disabledContentColor = palette.muted
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
        border = if (!white) BorderStroke(0.dp, Color.Transparent) else null
    ) {
        if (leading != null) {
            Text(leading, fontSize = 17.sp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OutlineJavadaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
    trailing: String? = null
) {
    val palette = LocalJavadaColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 45.dp),
        enabled = enabled,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, palette.line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = palette.mutedStrong,
            disabledContentColor = palette.muted
        )
    ) {
        if (leading != null) {
            Text(leading, fontSize = 16.sp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.width(7.dp))
            Text(trailing, color = palette.greenDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProgressMeter(progress: Float, modifier: Modifier = Modifier, height: Dp = 7.dp) {
    val palette = LocalJavadaColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(palette.line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(CircleShape)
                .background(palette.green)
        )
    }
}

@Composable
fun StatusBadge(status: ItemStatus, lang: String, modifier: Modifier = Modifier) {
    val palette = LocalJavadaColors.current
    val (label, color, bg, glyph) = when (status) {
        ItemStatus.COMPLETED -> Quad(UiText.get(lang, "completed"), palette.greenDeep, palette.greenSoft, "✓")
        ItemStatus.AVAILABLE -> Quad(UiText.get(lang, "available"), palette.greenDeep, palette.greenSoft, "›")
        ItemStatus.LOCKED -> Quad(UiText.get(lang, "locked"), palette.muted, palette.surfaceSoft, "•")
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = color,
        border = BorderStroke(1.dp, if (status == ItemStatus.LOCKED) palette.line else color.copy(alpha = .16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(glyph, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

private data class Quad(val a: String, val b: Color, val c: Color, val d: String)

@Composable
fun ModuleSymbol(moduleId: Int, modifier: Modifier = Modifier) {
    val palette = LocalJavadaColors.current
    val symbol = when {
        moduleId == 1 -> "{}"
        moduleId == 2 -> "<>"
        moduleId == 3 -> "↻"
        moduleId >= 17 -> "✦"
        else -> "⌘"
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.greenSoft),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = palette.greenDeep, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun CompletionStatusIcon(status: ItemStatus, modifier: Modifier = Modifier) {
    val palette = LocalJavadaColors.current
    when (status) {
        ItemStatus.COMPLETED -> androidx.compose.material3.Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = modifier.size(20.dp), tint = palette.greenDeep)
        ItemStatus.AVAILABLE -> androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = modifier.size(20.dp), tint = palette.green)
        ItemStatus.LOCKED -> androidx.compose.material3.Icon(Icons.Filled.Lock, contentDescription = null, modifier = modifier.size(18.dp), tint = palette.muted)
    }
}

@Composable
fun RichTitle(
    prefix: String,
    accent: String,
    suffix: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 28
) {
    val palette = LocalJavadaColors.current
    Text(
        buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(color = palette.greenDeep, fontWeight = FontWeight.ExtraBold)) { append(accent) }
            append(suffix)
        },
        modifier = modifier,
        color = palette.text,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.23).sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.9).sp
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}
