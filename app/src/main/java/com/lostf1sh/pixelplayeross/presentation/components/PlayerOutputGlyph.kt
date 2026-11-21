package com.lostf1sh.pixelplayeross.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.lostf1sh.pixelplayeross.R

/** Mod: where the music comes out, with the same icon in the full player's button and the mini player. */
internal enum class PlayerOutputIcon(@DrawableRes val drawable: Int) {
    Speaker(R.drawable.ic_output_speaker),
    SpeakerGroup(R.drawable.ic_output_speaker_group),
    Bluetooth(R.drawable.rounded_bluetooth_24),
    // A plain speaker, like Spotify; the colour and the label say when it is not the phone.
    Phone(R.drawable.ic_output_speaker);

    val isPhone: Boolean get() = this == Phone

    companion object {
        fun of(sonosRooms: Int?, isBluetoothActive: Boolean): PlayerOutputIcon = when {
            sonosRooms != null && sonosRooms > 1 -> SpeakerGroup
            sonosRooms != null -> Speaker
            isBluetoothActive -> Bluetooth
            else -> Phone
        }
    }
}

@Composable
internal fun PlayerOutputGlyph(
    icon: PlayerOutputIcon,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(painterResource(icon.drawable), contentDescription, modifier, tint)
}
