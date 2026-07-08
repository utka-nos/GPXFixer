package com.gpxeditor.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.domain.imported.ImportedTrack

@Composable
fun EmptyHistory() {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "No GPX tracks imported yet.",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun ImportedTrackRow(
    track: ImportedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.displayName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = track.originalFileName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${track.trackCount} tracks / ${track.pointCount} points",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Imported at ${track.importedAt}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${track.displayName}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
