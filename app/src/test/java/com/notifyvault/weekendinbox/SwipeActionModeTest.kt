package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.data.SwipeActionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeActionModeTest {

    @Test
    fun fromStorageDefaultsToImmediateDelete() {
        assertEquals(SwipeActionMode.SWIPE_IMMEDIATE_DELETE, SwipeActionMode.fromStorage(null))
        assertEquals(SwipeActionMode.SWIPE_IMMEDIATE_DELETE, SwipeActionMode.fromStorage("unknown"))
    }

    @Test
    fun fromStorageRestoresPersistedMode() {
        assertEquals(
            SwipeActionMode.SWIPE_REVEAL_DELETE,
            SwipeActionMode.fromStorage(SwipeActionMode.SWIPE_REVEAL_DELETE.name)
        )
    }

    @Test
    fun persistsUsingEnumNameContract() {
        val storedValue = SwipeActionMode.SWIPE_IMMEDIATE_DELETE.name
        assertEquals(SwipeActionMode.SWIPE_IMMEDIATE_DELETE, SwipeActionMode.fromStorage(storedValue))
    }
}
