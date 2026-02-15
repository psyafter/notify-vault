# NotifyVault: Weekend Inbox

NotifyVault captures notifications only when user rules are active, then stores them locally for later review.

## Tech Stack
- Kotlin + Gradle (Android app)
- Jetpack Compose UI
- Room for local persistence (`notify_vault.db`)
- WorkManager periodic health check for notification access state
- Fully offline, no network permissions

## Build & Run
1. Open project in Android Studio Iguana+.
2. Sync Gradle.
3. Run `app` on Android 8.0+ (API 26+).
4. On first launch, open Notification Access settings and enable NotifyVault.

## Where data is stored
- Room database file: `notify_vault.db`
- Table `captured_notifications`: vault records
- Table `capture_rules`: date range + repeating weekend rules
- SharedPreferences (`notifyvault_prefs`): onboarding/pro/access health flags

## Capture flow
1. `VaultNotificationListenerService.onNotificationPosted` receives posted notifications.
2. Notification payload is normalized and hashed.
3. `NotificationRepository.tryCapture` checks:
   - 14-day paywall gate unless `isPro=true`
   - any active rule matches current time + package filter
   - de-dup against latest captured notification hash/package
4. If eligible, entity is saved in Room.

## Rules
- `DATE_RANGE`: start and end timestamps in local time (stored as epoch millis)
- `WEEKEND_REPEAT`: repeat on configured day numbers (`1..7`, ISO day-of-week)
- Per rule:
  - `isActive`
  - `appFilterMode`: `ALL_EXCEPT` or `ONLY_SELECTED`
  - `selectedPackagesCsv`

## Health reliability
- `AccessHealthWorker` runs every 6 hours.
- If notification access is disabled:
  - writes disabled state to prefs
  - emits a simple local reminder notification

## OEM battery restrictions and setup
- Open **Fix setup** in the app to run a local/offline health check.
- Verify these statuses are green:
  - Notification Access is enabled for NotifyVault
  - Battery optimization exemption is enabled (`Don't optimize` / unrestricted)
  - App notifications permission is granted on Android 13+
- Use the built-in buttons to open the nearest settings page:
  - Notification listener settings
  - Request battery optimization exemption
  - Battery optimization settings
  - App info fallback

### Manufacturer tips (best effort)
- **Xiaomi / Redmi / POCO (MIUI/HyperOS)**
  - Enable **Autostart** for NotifyVault
  - Set battery mode to **No restrictions**
- **Samsung (One UI)**
  - Set battery usage to **Unrestricted**
  - Disable **Put unused apps to sleep** for NotifyVault
- **Huawei / Honor**
  - Allow **Auto-launch**
  - Disable automatic battery management for NotifyVault
- **OnePlus / Oppo / Realme / Vivo**
  - Allow auto-start/background activity
  - Disable battery optimization

NotifyVault only opens settings screens and never sends data over network for this flow.

## Tests
- Unit tests cover `RuleEngine` for:
  - date range inclusion
  - weekend day matching
  - timezone behavior
  - package filter mode behavior


## Vault UX improvements
- **Swipe action setting** in Settings:
  - `SWIPE_IMMEDIATE_DELETE` (default): swipe left deletes immediately and shows Undo snackbar.
  - `SWIPE_REVEAL_DELETE`: swipe left reveals a Delete action button; delete occurs only on tap.
- **Optional system dismissal** setting:
  - "Also dismiss system notification when deleting" (default off).
  - Best effort via Notification Listener key cancel; Vault deletion still succeeds if cancel is unavailable.
- **Row expansion**:
  - Tap any vault row to expand/collapse full title/text and metadata.
  - Single-expanded-row behavior: opening one row collapses the previously expanded row.

## Health / Fix setup checks
The **Fix setup** tab now includes actionable cards for:
- Notification Listener enabled state + deep link to listener settings.
- Battery optimization exemption state + request exemption + optimization list.
- Android 13+ notifications runtime permission + app notification settings link.
- Always-available App info fallback.
- OEM-specific setup tips with **Copy instructions** and **Try open OEM settings** (safe fallback to App info).
- **Share diagnostics** text report with listener/battery/permission/device/rule/app-selection fields.
