package com.qimian233.ztool.data.keys

/**
 * Hook module log levels (Log4j-style six levels), shared between the app UI
 * and the hook-side logger [com.qimian233.ztool.hook.base.ModuleLog].
 *
 * [priority] is the Android log priority the level maps to; it is also the
 * value persisted in the `log_level` preference key, so the hook side can map
 * a raw int back via [fromPriority] without relying on enum order.
 */
enum class LogLevel(val priority: Int) {
    TRACE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    FATAL(7);

    companion object {
        val DEFAULT = INFO

        fun fromPriority(priority: Int): LogLevel =
            entries.firstOrNull { it.priority == priority } ?: DEFAULT
    }
}
