package com.qimian233.ztool.ui.firstrun

/**
 * Why the first-run flow is being shown:
 * - [FirstRun]: no content ever accepted — the complete onboarding.
 * - [UpdateOnly]: previously accepted, but some page's schema version is
 *   stale — the flow replays so the user sees what changed.
 */
enum class FirstrunDisplayMode {
    FirstRun,
    UpdateOnly
}
