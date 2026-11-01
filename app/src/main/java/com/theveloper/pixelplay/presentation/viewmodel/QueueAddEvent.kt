package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song

/** A song the user just enqueued; drives the styled "added to queue" confirmation popup. */
data class QueueAddEvent(
    val song: Song,
    val playNext: Boolean
)
