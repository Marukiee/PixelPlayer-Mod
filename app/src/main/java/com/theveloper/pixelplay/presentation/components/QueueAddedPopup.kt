package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Styled, auto-dismissing confirmation shown when the user adds a song to the queue.
 * A small elevated pill slides up from the bottom with the album art, an "Added to queue"
 * (or "Playing next") label and the song title. Renders nothing while idle.
 */
@Composable
fun QueueAddedPopup(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    /** When true the popup stays hidden (e.g. the queue remove-undo bar is showing) so the two
     * never stack at different heights. */
    suppressed: Boolean = false
) {
    var event by remember { mutableStateOf<com.theveloper.pixelplay.presentation.viewmodel.QueueAddEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    var nonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        playerViewModel.queueAddedEvents.collect { e ->
            event = e
            visible = true
            nonce++
        }
    }
    LaunchedEffect(nonce) {
        if (nonce > 0) {
            delay(2200)
            visible = false
        }
    }

    val current = event
    AnimatedVisibility(
        visible = visible && current != null && !suppressed,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        if (current != null) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmartImage(
                        model = current.song.albumArtUriString,
                        contentDescription = null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = stringResource(
                                if (current.playNext) R.string.queue_added_next_title
                                else R.string.queue_added_popup_title
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = current.song.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        painter = painterResource(
                            if (current.playNext) R.drawable.rounded_skip_next_24
                            else R.drawable.rounded_queue_music_24
                        ),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

private data class QueueRemovedProjection(
    val visible: Boolean = false,
    val title: String = "",
    val art: String? = null
)

/**
 * Confirmation shown when the user removes a song from the queue, with an Undo action. Styled
 * identically to [QueueAddedPopup] and rendered at the same anchor so the two never differ in
 * position or look. Reads its state from the player's queue undo-bar state.
 */
@Composable
fun QueueRemovedPopup(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val proj by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map {
                QueueRemovedProjection(
                    visible = it.showQueueItemUndoBar,
                    title = it.lastRemovedQueueSong?.title.orEmpty(),
                    art = it.lastRemovedQueueSong?.albumArtUriString
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = QueueRemovedProjection())

    AnimatedVisibility(
        visible = proj.visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmartImage(
                    model = proj.art,
                    contentDescription = null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp)
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = stringResource(R.string.queue_removed_popup_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = proj.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { playerViewModel.undoRemoveSongFromQueue() }) {
                    Text(
                        text = stringResource(R.string.common_undo),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
