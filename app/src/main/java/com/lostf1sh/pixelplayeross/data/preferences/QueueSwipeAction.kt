package com.lostf1sh.pixelplayeross.data.preferences

/**
 * Mod: what swiping a song in the queue does, one setting per direction. The keys are the old mod's, so its settings
 * carry over.
 */
enum class QueueSwipeAction(val storageKey: String) {
    NONE("none"),

    /** Take the song out of the queue. */
    REMOVE("remove"),

    /** Move the song to the end of the songs you queued yourself, so it plays before the rest of the list. */
    ADD_TO_QUEUE_END("add_to_queue_end");

    companion object {
        val DEFAULT_LEFT = REMOVE
        val DEFAULT_RIGHT = ADD_TO_QUEUE_END

        fun fromStorageKey(value: String?, fallback: QueueSwipeAction): QueueSwipeAction =
            entries.firstOrNull { it.storageKey == value } ?: fallback
    }
}
