package com.notifyvault.weekendinbox.data

enum class SwipeActionMode {
    SWIPE_IMMEDIATE_DELETE,
    SWIPE_REVEAL_DELETE;

    companion object {
        fun fromStorage(raw: String?): SwipeActionMode {
            return entries.firstOrNull { it.name == raw } ?: SWIPE_REVEAL_DELETE
        }
    }
}
