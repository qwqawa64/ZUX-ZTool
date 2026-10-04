package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Fine volume steps hook module.
 *
 * Raises the media stream (STREAM_MUSIC) volume scale from the stock step count
 * (e.g. 15) to 38 steps. The count is co-designed with VolumeKeyNonlinearRamp:
 * the native key-repeat loop ticks at most ~20 steps/s, and 38 steps keep the
 * ramp's full-speed animation equivalent to the previous 150-step scale driven
 * at 80 steps/s (same ~53% of range per second) while the ramp applies at most
 * one adjust per tick - no per-tick multi-step amplification, no animation
 * jitter. It is no longer Xiaomi/HyperOS step-count aligned.
 *
 * Mechanics (see com.android.server.audio.AudioService in services.jar):
 * - [android.media.AudioService.VolumeStreamState] derives its index range from the
 *   static `MAX_STREAM_VOLUME[stream] * 10` in its constructor, and propagates the
 *   range to the native audioserver via `AudioSystem.initStreamVolume()` inside
 *   `updateIndexFactors()`.
 * - Hooking `createStreamStates()` *before* it runs and raising the array element
 *   therefore covers the whole chain: in-memory index range, native range, binder
 *   answers to `AudioManager.getStreamMaxVolume()`, and thus every client slider
 *   (SystemUI volume panel included).
 *
 * Migration: persisted `volume_music*` Settings.System values (and the
 * `AudioSystem.DEFAULT_STREAM_VOLUME` fallback) are rescaled once by
 * TARGET/stockMax so the current loudness is preserved when the feature is first
 * enabled. A Settings.Global marker prevents repeated rescaling across reboots.
 * Disabling the module restores the stock 15-step scale; the persisted index is
 * then clamped to the old maximum, so the volume should be re-adjusted manually
 * after turning the feature off.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class FineVolumeSteps : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FINE_VOLUME_STEPS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val audioServiceClass = classLoader.loadClass("com.android.server.audio.AudioService")
            val createStreamStates = findMethod(audioServiceClass, "createStreamStates")
            hookWithId(createStreamStates, HOOK_ID) { chain ->
                try {
                    applyFineVolumeSteps(audioServiceClass, chain.thisObject)
                } catch (t: Throwable) {
                    logger.error("Failed to apply fine volume steps, falling back to stock", t)
                }
                chain.proceed()
            }
            logger.info("Successfully hooked AudioService.createStreamStates")
        } catch (t: Throwable) {
            logger.error("Failed to hook AudioService.createStreamStates", t)
        }
    }

    /**
     * Raises MAX_STREAM_VOLUME[STREAM_MUSIC] to [TARGET_STEPS] and performs the
     * one-time persisted-value migration. Only array *elements* are mutated, so
     * no static-final field writes are involved.
     */
    private fun applyFineVolumeSteps(audioServiceClass: Class<*>, audioService: Any?) {
        val maxField = findField(audioServiceClass, "MAX_STREAM_VOLUME")
        maxField.isAccessible = true
        val maxVolumes = maxField.get(null) as? IntArray ?: run {
            logger.error("MAX_STREAM_VOLUME is not an IntArray, aborting")
            return
        }
        val stockMax = maxVolumes[STREAM_MUSIC]
        if (stockMax <= 0 || stockMax >= TARGET_STEPS) {
            logger.info("Stock music steps already >= $TARGET_STEPS (=$stockMax), nothing to do")
            return
        }

        val contentResolver = audioService?.let {
            val crField = findField(audioServiceClass, "mContentResolver")
            crField.isAccessible = true
            crField.get(it) as? ContentResolver
        }

        if (contentResolver != null &&
            Settings.Global.getInt(contentResolver, MIGRATION_MARKER_V2, 0) == 0
        ) {
            // Values previously migrated to the 150-step scale (v1 marker set)
            // must be rescaled 150 -> TARGET; fresh installs rescale from the
            // stock scale detected via the v1 marker being absent.
            val fromSteps = if (Settings.Global.getInt(contentResolver, MIGRATION_MARKER, 0) == 1) {
                LEGACY_TARGET_STEPS
            } else {
                stockMax
            }
            migratePersistedMusicVolume(contentResolver, fromSteps)
            Settings.Global.putInt(contentResolver, MIGRATION_MARKER_V2, 1)
        }

        maxVolumes[STREAM_MUSIC] = TARGET_STEPS

        // Keep the fallback loudness identical when no persisted value exists.
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val defField = findField(audioSystemClass, "DEFAULT_STREAM_VOLUME")
            defField.isAccessible = true
            (defField.get(null) as? IntArray)?.let { defaults ->
                defaults[STREAM_MUSIC] = defaults[STREAM_MUSIC] * TARGET_STEPS / stockMax
            }
        } catch (t: Throwable) {
            logger.warn("Failed to rescale DEFAULT_STREAM_VOLUME fallback: $t")
        }

        logger.info(
            "Fine volume steps applied: music stream max $stockMax -> $TARGET_STEPS " +
                "(migration ${if (contentResolver != null) "checked" else "skipped, no resolver"})"
        )
    }

    /**
     * Rescales all persisted Settings.System rows named `volume_music*` from the
     * stock scale to the 150-step scale. Values are stored in index tenths.
     */
    private fun migratePersistedMusicVolume(contentResolver: ContentResolver, stockMax: Int) {
        try {
            contentResolver.query(
                Settings.System.CONTENT_URI,
                arrayOf(NAME_COLUMN, VALUE_COLUMN),
                "$NAME_COLUMN LIKE '$MUSIC_SETTING_PREFIX%'",
                null,
                null
            )?.use { cursor ->
                val updates = ArrayList<Pair<String, Int>>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val rawValue = cursor.getString(1)?.toIntOrNull() ?: continue
                    val scaled = rawValue * TARGET_STEPS / stockMax
                    updates.add(name to scaled.coerceIn(0, TARGET_STEPS * 10))
                }
                for ((name, value) in updates) {
                    Settings.System.putInt(contentResolver, name, value)
                }
                if (updates.isNotEmpty()) {
                    logger.info("Migrated ${updates.size} persisted music volume rows to 150-step scale")
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to migrate persisted music volume values", t)
        }
    }

    companion object {
        private const val HOOK_ID = "fine_volume_steps_create_stream_states"

        /** android.media.AudioManager.STREAM_MUSIC */
        private const val STREAM_MUSIC = 3

        /** Target step count; 38 keeps the volume-key ramp's full speed at
         *  ~53% of range per second under the native ~20 ticks/s loop limit. */
        private const val TARGET_STEPS = 38

        /** Step count of the previous scale (v1 migration). */
        private const val LEGACY_TARGET_STEPS = 150

        private const val MUSIC_SETTING_PREFIX = "volume_music"
        private const val NAME_COLUMN = "name"
        private const val VALUE_COLUMN = "value"
        private const val MIGRATION_MARKER = "ztool_fine_volume_steps_migrated"
        private const val MIGRATION_MARKER_V2 = "ztool_fine_volume_steps_migrated_v2"
    }
}
