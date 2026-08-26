package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Radii
import com.example.ui.theme.Spacing
import com.example.utils.SrtParser

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (ctaLabel != null && onCtaClick != null) {
            Button(onClick = onCtaClick, modifier = Modifier.padding(top = Spacing.sm)) {
                Text(ctaLabel)
            }
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.lg,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

enum class BannerType { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun StatusBanner(
    text: String,
    type: BannerType,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val (container, content, icon) = when (type) {
        BannerType.INFO -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.Info
        )
        BannerType.SUCCESS -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Filled.CheckCircle
        )
        BannerType.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Filled.WarningAmber
        )
        BannerType.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.md,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = type.name, tint = content, modifier = Modifier.size(22.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                modifier = Modifier.weight(1f)
            )
            if (action != null) {
                action()
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = if (destructive) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TimingStepper(
    label: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    stepMs: Int = 100,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onDecrease,
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "$label -$stepMs ms", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("-${stepMs}ms", fontSize = 12.sp)
            }
            TextButton(
                onClick = onIncrease,
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text("+${stepMs}ms", fontSize = 12.sp)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.Add, contentDescription = "$label +$stepMs ms", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun TimingNudgeColumn(
    label: String,
    timeMs: Long,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "$label: ${SrtParser.formatTime(timeMs)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.heightIn(min = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedButton(
                onClick = onDecrease,
                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xs)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("-100ms", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onIncrease,
                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xs)
            ) {
                Text("+100ms", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun SubtitleOverlay(
    currentText: String?,
    modifier: Modifier = Modifier,
) {
    if (currentText.isNullOrBlank()) return
    Box(
        modifier = modifier
            .padding(bottom = Spacing.md, start = Spacing.lg, end = Spacing.lg)
            .background(Color.Black.copy(alpha = 0.75f), shape = Radii.sm)
            .padding(horizontal = Spacing.md, vertical = 6.dp)
    ) {
        Text(
            text = currentText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ResizableListHeader(
    title: String,
    timelinesWeightFraction: Float,
    onWeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), shape = Radii.sm)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onWeightChange((timelinesWeightFraction - dragAmount * 0.002f).coerceIn(0.2f, 1.8f))
                }
            }
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .padding(top = 2.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
