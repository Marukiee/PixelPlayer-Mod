package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.remote.RemoteSessionState

/**
 * The card on top of Controls while the music plays on another phone: the same card as a single Sonos room, with
 * the other phone's name, how it stands, the button back to this phone and its volume.
 */
@Composable
internal fun RemotePlayerActiveCard(
    state: RemoteSessionState,
    onDisconnect: () -> Unit,
    onVolume: (Int) -> Unit
) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val busy = state.isConnecting || state.isReconnecting
    Card(
        shape = DeviceCardShape,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DeviceCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeviceCardHeader(
                icon = {
                    AnimatedContent(
                        targetState = busy,
                        transitionSpec = {
                            (fadeIn(sonosMotion.defaultEffectsSpec()) + scaleIn(sonosMotion.defaultSpatialSpec(), initialScale = 0.5f)) togetherWith
                                (fadeOut(sonosMotion.fastEffectsSpec()) + scaleOut(sonosMotion.fastSpatialSpec(), targetScale = 0.5f))
                        },
                        label = "remoteCardIcon"
                    ) { waiting ->
                        if (waiting) {
                            DeviceCardSpinner()
                        } else {
                            Icon(imageVector = Icons.Rounded.PhoneAndroid, contentDescription = null, tint = onContainer)
                        }
                    }
                },
                title = {
                    Text(
                        text = state.device.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                status = {
                    val status = when {
                        state.isConnecting -> stringResource(R.string.sonos_connecting)
                        state.isReconnecting -> stringResource(R.string.remote_reconnecting)
                        else -> stringResource(R.string.remote_section_title) + " • " +
                            stringResource(if (state.isPlaying) R.string.sonos_playing else R.string.sonos_paused)
                    }
                    AnimatedContent(
                        targetState = status,
                        transitionSpec = { fadeIn(sonosMotion.defaultEffectsSpec()) togetherWith fadeOut(sonosMotion.fastEffectsSpec()) },
                        label = "remoteCardStatus"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    DeviceCardDisconnectButton(onClick = onDisconnect, enabled = !state.isConnecting)
                }
            )
            VolumeRow(
                value = state.volume?.takeIf { !state.isConnecting },
                description = stringResource(R.string.sonos_volume),
                onValueChange = onVolume,
                prominent = true
            )
        }
    }
}
