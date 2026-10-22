package com.tasktracker.pro.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground polling for sheet → app updates.
 *
 * How sheet-to-app sync really works: Google Apps Script web apps cannot push
 * spreadsheet edits to installed apps, so while the app is open and sync is
 * enabled this polls the backend for a fresh snapshot every [intervalMs]
 * (default 15 seconds). App → sheet is instant: every local task change
 * triggers a debounced sync ~2 seconds later via [SyncEngine.notifyLocalChange].
 * The app also refreshes on resume and after every manual "Sync Now".
 * This interval is documented in the UI and README — we never claim
 * true instant push from the sheet side.
 */
class SyncPoller(
    private val engine: SyncEngine,
    private val intervalMs: Long = 15_000L
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                try {
                    if (engine.isEnabled) engine.syncNow()
                } catch (e: Exception) {
                    // Polling must never crash the app; the next tick retries.
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
