package com.lostf1sh.pixelplayeross.utils

import androidx.media3.common.MediaItem

/**
 * Mod, from the old mod: marks a queue item you added yourself. Those songs form a block right after the playing song
 * and play before the rest of the list. The flag lives in the item's metadata extras, so it travels with the queue and
 * the queue list can show it.
 */
const val KEY_USER_QUEUE_ITEM = "pixelplay.user_queue_item"

/** True when you added this queue item yourself. */
fun MediaItem.isUserQueueItem(): Boolean =
    mediaMetadata.extras?.getBoolean(KEY_USER_QUEUE_ITEM, false) == true
