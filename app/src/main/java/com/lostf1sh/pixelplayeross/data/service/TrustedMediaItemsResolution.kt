package com.lostf1sh.pixelplayeross.data.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.lostf1sh.pixelplayeross.utils.KEY_USER_QUEUE_ITEM
import com.lostf1sh.pixelplayeross.utils.isUserQueueItem

internal data class TrustedMediaItemsResolution(
    val mediaItems: MutableList<MediaItem>,
    val trustedArtworkGrantItems: List<MediaItem>,
)

internal fun resolveMediaItemsWithTrustedArtworkGrants(
    requestedItems: List<MediaItem>,
    trustedItemResolver: (String) -> MediaItem?
): TrustedMediaItemsResolution {
    val resolvedItems = ArrayList<MediaItem>(requestedItems.size)
    val trustedArtworkGrantItems = ArrayList<MediaItem>()

    requestedItems.forEach { requestedItem ->
        val trustedItem = trustedItemResolver(requestedItem.mediaId)
        if (trustedItem != null) {
            // Mod: the trusted item is rebuilt from its id and drops the caller's extras, so the mark for a song you
            // queued yourself is put back, or the queue would lose its order and its icon.
            val preserved = trustedItem.preservingUserQueueFlag(requestedItem)
            resolvedItems += preserved
            trustedArtworkGrantItems += preserved
        } else {
            resolvedItems += requestedItem
        }
    }

    return TrustedMediaItemsResolution(
        mediaItems = resolvedItems,
        trustedArtworkGrantItems = trustedArtworkGrantItems
    )
}

private fun MediaItem.preservingUserQueueFlag(source: MediaItem): MediaItem {
    if (!source.isUserQueueItem()) return this
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply { putBoolean(KEY_USER_QUEUE_ITEM, true) }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}
