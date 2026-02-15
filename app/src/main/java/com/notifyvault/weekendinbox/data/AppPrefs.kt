package com.notifyvault.weekendinbox.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notifyvault.weekendinbox.domain.TrialPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "notifyvault_prefs")

class AppPrefs(private val context: Context) {
    suspend fun ensureFirstLaunch(): Long {
        val existing = firstLaunchEpochMillis()
        if (existing > 0L) return existing
        val now = System.currentTimeMillis()
        context.dataStore.edit { it[KEY_FIRST_LAUNCH] = now }
        return now
    }

    suspend fun firstLaunchEpochMillis(): Long =
        context.dataStore.data.map { it[KEY_FIRST_LAUNCH] ?: 0L }.first()

    suspend fun isPro(): Boolean = context.dataStore.data.map { it[KEY_PRO] ?: false }.first()

    suspend fun setPro(value: Boolean) {
        context.dataStore.edit { it[KEY_PRO] = value }
    }

    suspend fun setAccessDisabled(disabled: Boolean) {
        context.dataStore.edit { it[KEY_ACCESS_DISABLED] = disabled }
    }

    suspend fun isAccessDisabled(): Boolean = context.dataStore.data.map { it[KEY_ACCESS_DISABLED] ?: false }.first()

    suspend fun hasCompletedOnboarding(): Boolean = context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }.first()

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_ONBOARDED] = true }
    }

    suspend fun shouldShowSetupTip(): Boolean = context.dataStore.data.map { !(it[KEY_SETUP_TIP_SHOWN] ?: false) }.first()

    suspend fun markSetupTipShown() {
        context.dataStore.edit { it[KEY_SETUP_TIP_SHOWN] = true }
    }

    suspend fun captureMode(): CaptureMode {
        val raw = context.dataStore.data.map { it[KEY_CAPTURE_MODE] ?: CaptureMode.ONLY_SELECTED_APPS.name }.first()
        return CaptureMode.entries.firstOrNull { it.name == raw } ?: CaptureMode.ONLY_SELECTED_APPS
    }

    suspend fun setCaptureMode(mode: CaptureMode) {
        context.dataStore.edit { it[KEY_CAPTURE_MODE] = mode.name }
    }

    suspend fun canCaptureNewNotifications(now: Long = System.currentTimeMillis()): Boolean {
        val pro = isPro()
        val first = ensureFirstLaunch()
        return TrialPolicy.canCaptureNewNotifications(pro, first, now)
    }

    suspend fun trialDaysLeft(now: Long = System.currentTimeMillis()): Long {
        val first = ensureFirstLaunch()
        return TrialPolicy.daysLeft(first, now)
    }

    suspend fun swipeActionMode(): SwipeActionMode {
        val raw = context.dataStore.data.map { it[KEY_SWIPE_ACTION_MODE] ?: SwipeActionMode.SWIPE_IMMEDIATE_DELETE.name }.first()
        return SwipeActionMode.fromStorage(raw)
    }

    suspend fun setSwipeActionMode(mode: SwipeActionMode) {
        context.dataStore.edit { it[KEY_SWIPE_ACTION_MODE] = mode.name }
    }

    fun blockingHasCompletedOnboarding(): Boolean = runBlocking { hasCompletedOnboarding() }
    fun blockingCanCaptureNewNotifications(): Boolean = runBlocking { canCaptureNewNotifications() }
    fun blockingCaptureMode(): CaptureMode = runBlocking { captureMode() }
    fun blockingSwipeActionMode(): SwipeActionMode = runBlocking { swipeActionMode() }

    companion object {
        private val KEY_FIRST_LAUNCH = longPreferencesKey("first_launch")
        private val KEY_PRO = booleanPreferencesKey("is_pro")
        private val KEY_ACCESS_DISABLED = booleanPreferencesKey("access_disabled")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_CAPTURE_MODE = stringPreferencesKey("capture_mode")
        private val KEY_SWIPE_ACTION_MODE = stringPreferencesKey("swipe_action_mode")
        private val KEY_SETUP_TIP_SHOWN = booleanPreferencesKey("setup_tip_shown")
    }
}
