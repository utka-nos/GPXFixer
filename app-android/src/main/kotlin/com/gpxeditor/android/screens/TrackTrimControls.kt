package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun TrackTrimControlsSection(
    originalPointCount: Int,
    lastPointIndex: Int,
    trimStartIndex: Int,
    trimEndIndex: Int,
    removedPointCount: Int,
    previewErrorMessage: String?,
    onTrimStartChange: (Int) -> Unit,
    onTrimEndChange: (Int) -> Unit,
    onResetTrim: () -> Unit,
) {
    DetailSection(title = "Trim") {
        if (originalPointCount == 0) {
            Text(
                text = "No track points found.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            TrimPointStepper(
                title = "Start point",
                value = trimStartIndex,
                minValue = 0,
                maxValue = trimEndIndex,
                onValueChange = onTrimStartChange,
            )
            TrimPointStepper(
                title = "End point",
                value = trimEndIndex,
                minValue = trimStartIndex,
                maxValue = lastPointIndex,
                onValueChange = onTrimEndChange,
            )

            if (lastPointIndex > 0) {
                RangeSlider(
                    value = trimStartIndex.toFloat()..trimEndIndex.toFloat(),
                    onValueChange = { range ->
                        val start = range.start
                            .roundToInt()
                            .coerceIn(0, lastPointIndex)
                        val end = range.endInclusive
                            .roundToInt()
                            .coerceIn(start, lastPointIndex)
                        onTrimStartChange(start)
                        onTrimEndChange(end)
                    },
                    valueRange = 0f..lastPointIndex.toFloat(),
                )
            }

            DetailRow(
                label = "Kept points",
                value = "${(trimEndIndex - trimStartIndex + 1).coerceAtLeast(0)} of $originalPointCount",
            )

            if (removedPointCount > 0) {
                DetailRow("Removed points", removedPointCount.toString())
            }

            Button(onClick = onResetTrim) {
                Text("Reset trim")
            }
        }

        previewErrorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TrimPointStepper(
    title: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            enabled = value > minValue,
            onClick = { onValueChange((value - 1).coerceAtLeast(minValue)) },
        ) {
            Text("-")
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = (value + 1).toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            enabled = value < maxValue,
            onClick = { onValueChange((value + 1).coerceAtMost(maxValue)) },
        ) {
            Text("+")
        }
    }
}
