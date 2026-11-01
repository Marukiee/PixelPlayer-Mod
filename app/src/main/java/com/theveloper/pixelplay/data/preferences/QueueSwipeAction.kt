package com.theveloper.pixelplay.data.preferences

/**
 * Action performed when a song is swiped left or right inside the playing queue sheet.
 * Each direction is configurable independently.
 */
enum class QueueSwipeAction(val key: String) {
    /** Swiping in this direction does nothing. */
    NONE("none"),

    /** Remove the song from the queue. */
    REMOVE("remove"),

    /** Move the song to the end of the manual user-queue block (plays sooner, but not first). */
    ADD_TO_QUEUE_END("add_to_queue_end");

    companion object {
        /** Default for both directions: remove (mirrors the original left-swipe-to-remove). */
        val DEFAULT = REMOVE

        fun fromKey(key: String?): QueueSwipeAction =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
