package com.qimian233.ztool.data.pen

import androidx.annotation.StringRes
import com.qimian233.ztool.R

/** Waveform family; ids are disjoint between the two families. */
enum class PenHapticWaveKind { CONTINUOUS, IMPACT }

/** A waveform slot the pen can play, with the label shown in the mapping editor. */
data class PenHapticWaveform(
    val id: Int,
    val kind: PenHapticWaveKind,
    @param:StringRes val nameRes: Int,
    val silent: Boolean = false
)

/** One `source -> target` rewrite rule. */
data class PenHapticRedirectRule(
    val sourceId: Int,
    val targetId: Int
)

/**
 * Pen haptic waveform ids and the rule-list codec shared by the app and the hook.
 * Layout: continuous 32..41 (five audible + five silent), impact 1..7.
 */
object PenHapticWaveformIds {
    const val BALL_PEN = 32
    const val PENCIL = 33
    const val CHISEL_MARKER = 34
    const val ERASER = 35
    const val FOUNTAIN_PEN = 36
    const val BALL_PEN_SILENT = 37
    const val PENCIL_SILENT = 38
    const val CHISEL_MARKER_SILENT = 39
    const val ERASER_SILENT = 40
    const val FOUNTAIN_PEN_SILENT = 41

    const val IMPACT_CLICK = 1
    const val IMPACT_CONNECTED = 2
    const val IMPACT_FEEDBACK_ON = 3
    const val IMPACT_BRUSH_CHANGE = 4
    const val IMPACT_TEXT_FOCUS = 5
    const val IMPACT_RECOGNITION = 6
    const val IMPACT_PRESS = 7

    fun kindOf(id: Int): PenHapticWaveKind? = when (id) {
        in BALL_PEN..FOUNTAIN_PEN_SILENT -> PenHapticWaveKind.CONTINUOUS
        in IMPACT_CLICK..IMPACT_PRESS -> PenHapticWaveKind.IMPACT
        else -> null
    }

    /** Parses `"36:33,34:33"`, dropping unknown ids, cross-family pairs, self-maps and repeat sources. */
    fun parseRules(raw: String): LinkedHashMap<Int, Int> {
        val parsed = LinkedHashMap<Int, Int>()
        raw.split(',').forEach { entry ->
            val parts = entry.trim().split(':')
            if (parts.size != 2) return@forEach
            val source = parts[0].trim().toIntOrNull() ?: return@forEach
            val target = parts[1].trim().toIntOrNull() ?: return@forEach
            parsed.putIfAbsent(source, target)
        }
        return sanitizeRules(parsed)
    }

    /** Drops self-maps, unknown ids, cross-family pairs and duplicate sources, keeping order. */
    fun sanitizeRules(rules: Map<Int, Int>): LinkedHashMap<Int, Int> {
        val sanitized = LinkedHashMap<Int, Int>()
        rules.forEach { (source, target) ->
            if (source == target) return@forEach
            val kind = kindOf(source) ?: return@forEach
            if (kind != kindOf(target)) return@forEach
            sanitized.putIfAbsent(source, target)
        }
        return sanitized
    }

    fun formatRules(rules: Map<Int, Int>): String =
        rules.entries.joinToString(",") { "${it.key}:${it.value}" }
}

/** Waveform labels and lookup helpers used by the mapping editor. */
object PenHapticWaveformCatalog {
    val all: List<PenHapticWaveform> = listOf(
        PenHapticWaveform(PenHapticWaveformIds.BALL_PEN, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_ball_pen_label),
        PenHapticWaveform(PenHapticWaveformIds.PENCIL, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_pencil_label),
        PenHapticWaveform(PenHapticWaveformIds.CHISEL_MARKER, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_chisel_marker_label),
        PenHapticWaveform(PenHapticWaveformIds.FOUNTAIN_PEN, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_fountain_pen_label),
        PenHapticWaveform(PenHapticWaveformIds.ERASER, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_eraser_label),
        PenHapticWaveform(PenHapticWaveformIds.BALL_PEN_SILENT, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_ball_pen_label, silent = true),
        PenHapticWaveform(PenHapticWaveformIds.PENCIL_SILENT, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_pencil_label, silent = true),
        PenHapticWaveform(PenHapticWaveformIds.CHISEL_MARKER_SILENT, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_chisel_marker_label, silent = true),
        PenHapticWaveform(PenHapticWaveformIds.FOUNTAIN_PEN_SILENT, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_fountain_pen_label, silent = true),
        PenHapticWaveform(PenHapticWaveformIds.ERASER_SILENT, PenHapticWaveKind.CONTINUOUS, R.string.system_framework_pen_wave_eraser_label, silent = true),
        PenHapticWaveform(PenHapticWaveformIds.IMPACT_CLICK, PenHapticWaveKind.IMPACT, R.string.system_framework_pen_wave_impact_click_label),
        PenHapticWaveform(PenHapticWaveformIds.IMPACT_CONNECTED, PenHapticWaveKind.IMPACT, R.string.system_framework_pen_wave_impact_connected_label),
        PenHapticWaveform(PenHapticWaveformIds.IMPACT_FEEDBACK_ON, PenHapticWaveKind.IMPACT, R.string.system_framework_pen_wave_impact_feedback_on_label),
        PenHapticWaveform(PenHapticWaveformIds.IMPACT_BRUSH_CHANGE, PenHapticWaveKind.IMPACT, R.string.system_framework_pen_wave_impact_brush_change_label),
        PenHapticWaveform(PenHapticWaveformIds.IMPACT_TEXT_FOCUS, PenHapticWaveKind.IMPACT, R.string.system_framework_pen_wave_impact_text_focus_label),
        PenHapticWaveform(PenHapticWaveformIds.IMPACT_RECOGNITION, PenHapticWaveKind.IMPACT, R.string.system_framework_pen_wave_impact_recognition_label),
        PenHapticWaveform(PenHapticWaveformIds.IMPACT_PRESS, PenHapticWaveKind.IMPACT, R.string.system_framework_pen_wave_impact_press_label),
    )

    fun byId(id: Int): PenHapticWaveform? = all.firstOrNull { it.id == id }

    fun ofKind(kind: PenHapticWaveKind): List<PenHapticWaveform> = all.filter { it.kind == kind }

    /** Preferred target for a new rule: the pencil for writing, click for impact. */
    fun defaultTarget(sourceId: Int): Int? {
        val kind = PenHapticWaveformIds.kindOf(sourceId) ?: return null
        val preferred = if (kind == PenHapticWaveKind.CONTINUOUS) {
            PenHapticWaveformIds.PENCIL
        } else {
            PenHapticWaveformIds.IMPACT_CLICK
        }
        return if (preferred != sourceId) preferred
        else ofKind(kind).firstOrNull { it.id != sourceId }?.id
    }
}
