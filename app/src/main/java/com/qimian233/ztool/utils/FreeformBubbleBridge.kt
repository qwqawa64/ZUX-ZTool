package com.qimian233.ztool.utils

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * App-side client for the "ztool.freeform_bubble" binder service published by
 * [com.qimian233.ztool.hook.modules.systemframework.FreeformEdgeBubbleHook] inside
 * system_server. android.os.ServiceManager is hidden from the SDK, so it is reached
 * via reflection + [HiddenApiBypass]; the parcel protocol is hand-rolled (no AIDL)
 * and must stay in sync with the hook-side [FreeformEdgeBubbleHook.BridgeBinder].
 */
object FreeformBubbleBridge {

    private const val TAG = "FreeformBubbleBridge"
    private const val SERVICE_NAME = "ztool.freeform_bubble"
    const val DESCRIPTOR = "com.qimian233.ztool.hook.IFreeformBubbleBridge"

    // Must mirror the hook-side codes.
    private const val CODE_MINIMIZE_TASK = 1
    private const val CODE_RESTORE_TASK = 2
    private const val CODE_LIST_MINIMIZED = 3
    private const val CODE_GET_PACKAGE = 4
    private const val CODE_REGISTER_CALLBACK = 5
    const val CB_BUBBLE_ADDED = 11
    const val CB_BUBBLE_REMOVED = 12

    /** A minimized freeform task as reported by the hook. */
    data class MinimizedTask(val taskId: Int, val side: Int, val packageName: String?)

    fun isServiceAvailable(): Boolean = obtainBinder() != null

    private fun obtainBinder(): IBinder? = try {
        val smClass = Class.forName("android.os.ServiceManager")
        HiddenApiBypass.invoke(
            smClass, null, "getService", String::class.java, SERVICE_NAME
        ) as? IBinder
    } catch (t: Throwable) {
        Log.w(TAG, "getService failed: ${t.message}")
        null
    }

    private fun <T> transact(code: Int, writeArgs: (Parcel) -> Unit, readResult: (Parcel) -> T): T? {
        val remote = obtainBinder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            writeArgs(data)
            remote.transact(code, data, reply, 0)
            reply.readException()
            readResult(reply)
        } catch (t: Throwable) {
            Log.w(TAG, "transact code=$code failed: ${t.message}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Docks the freeform task fully offscreen; `side` is 0 (left) or 1 (right). */
    fun minimizeTask(taskId: Int, side: Int): Boolean =
        transact(CODE_MINIMIZE_TASK, { it.writeInt(taskId); it.writeInt(side) }, { it.readInt() == 1 })
            ?: false

    /** Brings the task back to its pre-dock bounds and to the front. */
    fun restoreTask(taskId: Int): Boolean =
        transact(CODE_RESTORE_TASK, { it.writeInt(taskId) }, { it.readInt() == 1 }) ?: false

    fun listMinimized(): List<MinimizedTask> =
        transact(CODE_LIST_MINIMIZED, { }, { reply ->
            val n = reply.readInt()
            val out = ArrayList<MinimizedTask>(n / 2)
            var taskId = -1
            var side = 0
            for (i in 0 until n) {
                val v = reply.readInt()
                if (i % 2 == 0) taskId = v else {
                    side = v
                    out.add(MinimizedTask(taskId, side, getPackageForTask(taskId)))
                }
            }
            out
        }) ?: emptyList()

    fun getPackageForTask(taskId: Int): String? =
        transact(CODE_GET_PACKAGE, { it.writeInt(taskId) }, { it.readString() })

    /**
     * Registers a callback binder receiving hook-side bubble events
     * ([CB_BUBBLE_ADDED] / [CB_BUBBLE_REMOVED], one-way).
     */
    fun registerCallback(callback: Binder) {
        transact(CODE_REGISTER_CALLBACK, { it.writeStrongBinder(callback) }, { }) ?: run {
            Log.w(TAG, "registerCallback: service unreachable")
        }
    }
}
