package com.theveloper.pixelplay.utils

import androidx.media3.common.MediaItem

/**
 * Marks a [MediaItem] that the user manually enqueued (Spotify-style "user queue").
 *
 * Manually added songs form a contiguous FIFO block right after the currently playing
 * item and are consumed before the surrounding context queue (library/album/etc.) resumes.
 * The flag is stored in the item's [androidx.media3.common.MediaMetadata] extras so it
 * travels with the timeline and can be read back both by the playback core (to compute
 * the insertion point) and by the queue UI (to render the "up next" marker).
 */
const val KEY_USER_QUEUE_ITEM = "pixelplay.user_queue_item"

/** True when this timeline item was manually enqueued by the user. */
fun MediaItem.isUserQueueItem(): Boolean =
    mediaMetadata.extras?.getBoolean(KEY_USER_QUEUE_ITEM, false) == true
