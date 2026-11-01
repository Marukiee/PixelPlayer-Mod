package com.theveloper.pixelplay.data.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.theveloper.pixelplay.utils.KEY_USER_QUEUE_ITEM
import com.theveloper.pixelplay.utils.isUserQueueItem

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
            // The resolver rebuilds the item from its id and drops caller extras, so the
            // Spotify-style user-queue flag would be lost. Re-attach it from the request
            // so the FIFO insertion scan and the "up next" badge keep working.
            val preserved = trustedItem.preservingUserQueueFlag(requestedItem)
            resolvedItems += preserved
            trustedArtworkGrantItems += preserved
        } else {
            // Caller-supplied metadata is untrusted and must never drive provider grants.
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
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply {
        putBoolean(KEY_USER_QUEUE_ITEM, true)
    }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}
