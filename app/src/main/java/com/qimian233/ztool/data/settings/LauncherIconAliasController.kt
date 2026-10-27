package com.qimian233.ztool.data.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pushes launcher-alias changes only when no activity is started.
 *
 * Enabling or disabling a launcher activity-alias while its task is visible makes the
 * system finish that task even with [android.content.pm.PackageManager.DONT_KILL_APP],
 * so the write must wait until the app is backgrounded. See
 * docs/research/launcher-alias-icon-switch.md.
 */
object LauncherIconAliasController {
    private val pending = AtomicBoolean(false)
    private val startedActivities = AtomicInteger(0)

    /** Starts the activity counter; call once from the application's `onCreate()`. */
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) {
                    startedActivities.incrementAndGet()
                }

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) {
                    if (startedActivities.decrementAndGet() <= 0) {
                        applyPending(activity.applicationContext)
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    /** Marks the persisted icon choice as not yet reflected on the launcher. */
    fun requestApply(context: Context) {
        pending.set(true)
        // A caller outside the UI (no started activity) can apply without risking the task.
        if (startedActivities.get() == 0) {
            applyPending(context.applicationContext)
        }
    }

    private fun applyPending(context: Context) {
        if (!pending.compareAndSet(true, false)) return
        SettingsRepository(context.applicationContext).applyLauncherIconAliasState()
    }
}
