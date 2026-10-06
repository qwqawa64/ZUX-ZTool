package com.qimian233.ztool.data.home

import android.content.Context
import androidx.annotation.StringRes
import com.qimian233.ztool.R
import com.qimian233.ztool.data.hotreload.HotReloadNotice
import com.qimian233.ztool.data.hotreload.HotReloadNoticeBus
import com.qimian233.ztool.data.hotreload.HotReloadNoticeRepository
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * The line the home tip card shows.
 *
 * [hotReloadWarning] is the card's only styling input: an ordinary tip is advice, while the hot
 * reload line is a warning about the code the running hooks are executing right now, and the two
 * should not be mistaken for each other.
 */
data class HomeTip(
    @param:StringRes val messageRes: Int,
    val hotReloadWarning: Boolean
)

/**
 * Decides which tip the home card shows, and when it shows none.
 *
 * The catalogue is short, so the card is rare by design: [TIP_SHOW_PERCENT] of launches show a tip
 * at all. A hot reload overrides that and shows its warning unconditionally, because it is the one
 * case where the card carries information about the current session rather than flavour text.
 */
class HomeTipRepository(
    context: Context,
    private val random: Random = Random.Default
) {

    private val noticeRepository = HotReloadNoticeRepository(context)

    /**
     * Hot reload notices published in this process. The home card observes this so a reload
     * triggered from the Advanced options screen reaches the card immediately, instead of waiting
     * for a launch that [resolveLaunchTip] already answered.
     */
    val pendingHotReload: StateFlow<HotReloadNotice?> = HotReloadNoticeBus.pending

    /**
     * The tip for this app launch, or null when this launch shows none.
     *
     * Called once per launch by the home ViewModel. A reload outranks the dice roll, because the
     * launch check exists precisely to tell the user that the hooks running in other processes are
     * on old code.
     */
    fun resolveLaunchTip(): HomeTip? {
        noticeRepository.publishModuleApkUpdateIfReplaced()
        if (noticeRepository.pendingNotice() != null) {
            return takeHotReloadTip()
        }
        return randomTip()
    }

    /**
     * The forced warning shown after any hot reload, consuming the notice that asked for it.
     */
    fun takeHotReloadTip(): HomeTip {
        noticeRepository.consumePendingNotice()
        return hotReloadWarningTip()
    }

    /** The hot reload warning on its own, leaving any pending notice alone. */
    private fun hotReloadWarningTip(): HomeTip = HomeTip(
        messageRes = R.string.page_home_tip_hot_reload_warning,
        hotReloadWarning = true
    )

    /** A random tip, or null on the launches that show none. */
    private fun randomTip(): HomeTip? {
        if (random.nextInt(TIP_ROLL_SPACE) >= TIP_SHOW_PERCENT) return null
        return HomeTip(
            messageRes = TIP_MESSAGES.random(random),
            hotReloadWarning = false
        )
    }

    private companion object {
        /** Percentage of app launches that show a tip card at all. */
        const val TIP_SHOW_PERCENT = 5

        /** Exclusive upper bound of the launch roll; keep it a plain percentage. */
        const val TIP_ROLL_SPACE = 100

        /**
         * The random pool, in no particular order.
         *
         * `page_home_tip_hot_reload_warning` is deliberately absent: it asserts that a reload just
         * happened, which is only ever true when one did, so it is reachable through
         * [hotReloadWarningTip] alone and a dice roll can never produce a false claim.
         */
        val TIP_MESSAGES = listOf(
            R.string.page_home_tip_extreme_refresh_rate,
            R.string.page_home_tip_misans_font,
            R.string.page_home_tip_feature_count_power,
            R.string.page_home_tip_boundary_values,
            R.string.page_home_tip_notification_permission,
            R.string.page_home_tip_set_tag,
            R.string.page_home_tip_report_issue,
            R.string.page_home_tip_freeform_priority,
            R.string.page_home_tip_nightly_build,
            R.string.page_home_tip_debug_leak_canary,
            R.string.page_home_tip_release_performance,
            R.string.page_home_tip_pull_request,
            R.string.page_home_tip_crash_log,
            R.string.page_home_tip_not_zuxceiler
        )
    }
}
