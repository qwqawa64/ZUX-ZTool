package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Raises the media stream (STREAM_MUSIC) volume scale from the stock step count
 * (e.g. 15) to [TARGET_STEPS] steps, HyperOS-style.
 *
 * [android.media.AudioService.VolumeStreamState] derives its index range from the
 * static `MAX_STREAM_VOLUME[stream] * 10` in its constructor and propagates it to
 * audioserver via `AudioSystem.initStreamVolume()` inside `updateIndexFactors()`,
 * so raising the array element before `createStreamStates()` runs covers the whole
 * chain: in-memory range, native range, `AudioManager.getStreamMaxVolume()` and
 * therefore every client slider.
 *
 * Persisted `volume_music*` Settings.System values are rescaled once from the
 * stock scale, guarded by a Settings.Global marker. Turning the feature off
 * restores 15 steps and clamps the persisted index, so the volume may need
 * re-adjusting.
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
            Settings.Global.getInt(contentResolver, MIGRATION_MARKER, 0) == 0
        ) {
            migratePersistedMusicVolume(contentResolver, stockMax)
            Settings.Global.putInt(contentResolver, MIGRATION_MARKER, 1)
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

        /** Target step count (fine scale, pairs with VolumeKeyNonlinearRamp). */
        private const val TARGET_STEPS = 150

        private const val MUSIC_SETTING_PREFIX = "volume_music"
        private const val NAME_COLUMN = "name"
        private const val VALUE_COLUMN = "value"
        private const val MIGRATION_MARKER = "ztool_fine_volume_steps_migrated"
    }
}
