package com.notifyvault.weekendinbox

import com.notifyvault.weekendinbox.domain.OpenNotificationResolver
import com.notifyvault.weekendinbox.domain.OpenPath
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenNotificationResolverTest {
    private val resolver = OpenNotificationResolver()

    @Test
    fun prefers_active_notification_intent() {
        assertEquals(OpenPath.ACTIVE_NOTIFICATION, resolver.decide(hasActiveIntent = true, hasCachedIntent = true))
    }

    @Test
    fun uses_cached_when_active_missing() {
        assertEquals(OpenPath.CACHED_INTENT, resolver.decide(hasActiveIntent = false, hasCachedIntent = true))
    }

    @Test
    fun falls_back_to_app_launch_when_no_intent_exists() {
        assertEquals(OpenPath.APP_LAUNCH_FALLBACK, resolver.decide(hasActiveIntent = false, hasCachedIntent = false))
    }
}
