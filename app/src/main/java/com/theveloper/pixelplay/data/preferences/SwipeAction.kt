package com.theveloper.pixelplay.data.preferences

/**
 * Action performed when a song row is swiped left or right in the Library "Songs"
 * list and the Search results. Each swipe direction is configurable independently.
 */
enum class SwipeAction(val key: String) {
    /** Swiping in this direction does nothing (row just springs back). */
    NONE("none"),

    /** Append to the end of the user-queue block (Spotify-style FIFO priority). */
    ADD_TO_QUEUE("add_to_queue"),

    /** Insert right after the current song, ahead of anything already queued. */
    PLAY_NEXT("play_next");

    companion object {
        /** Default action for both directions, matching the original request. */
        val DEFAULT = ADD_TO_QUEUE

        fun fromKey(key: String?): SwipeAction =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
