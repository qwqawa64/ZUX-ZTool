package com.qimian233.ztool

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.HookedTarget

/**
 * libxposed service bridge.
 * <p>
 * Holds the current [XposedService] instance (updated by [ModuleActivationProbe] in binder callbacks)
 * and delegates all service methods to that instance. If the service is not connected,
 * the getters return null/zero values and mutations are no-ops.
 * </p>
 */
object XposedServiceBridge {

    /** Current service instance, non-null only when the module is activated */
    @Volatile
    var currentService: XposedService? = null
        internal set

    // ---- Basic queries ----

    /** Get the raw service instance, or null when not activated */
    fun getService(): XposedService? = currentService

    /** Get the service API version, or 0 when not activated */
    fun getApiVersion(): Int = currentService?.apiVersion ?: 0

    /** Get the framework name, or null when not activated */
    fun getFrameworkName(): String? = currentService?.frameworkName

    /** Get the framework version string, or null when not activated */
    fun getFrameworkVersion(): String? = currentService?.frameworkVersion

    /** Get the framework version code, or 0 when not activated */
    fun getFrameworkVersionCode(): Long = currentService?.frameworkVersionCode ?: 0L

    /** Get the framework property flags, or 0 when not activated */
    fun getFrameworkProperties(): Long = currentService?.frameworkProperties ?: 0L

    // ---- Scope ----

    /** Get the current scope package name list, or an empty list when not activated */
    fun getScope(): List<String> = currentService?.scope ?: emptyList()

    /** Request scope, no-op when not activated */
    fun requestScope(
        packages: List<String>,
        listener: XposedService.OnScopeEventListener
    ) {
        currentService?.requestScope(packages, listener)
    }

    /** Remove scope, no-op when not activated */
    fun removeScope(packages: List<String>) {
        currentService?.removeScope(packages)
    }

    // ---- Running targets ----

    /** Get the list of currently running hook targets, or an empty list when not activated */
    fun getRunningTargets(): List<HookedTarget> {
        if (getApiVersion() >= 102) {
            return currentService?.runningTargets ?: emptyList()
        }
        return emptyList()
    }

    // ---- Hot reload ----

    /** Hot-reload the module, no-op when not activated */
    fun hotReloadModule(
        target: HookedTarget,
        extras: Bundle,
        callback: XposedService.HotReloadCallback
    ) {
        if (getApiVersion() >= 102) {
            currentService?.hotReloadModule(target, extras, callback)
        }
    }

    // ---- Remote files/preferences ----

    /** Get remote SharedPreferences, or null when not activated */
    fun getRemotePreferences(name: String): SharedPreferences? =
        currentService?.getRemotePreferences(name)

    /** Delete remote SharedPreferences, no-op when not activated */
    fun deleteRemotePreferences(name: String) {
        currentService?.deleteRemotePreferences(name)
    }

    /** List remote files, or an empty array when not activated */
    fun listRemoteFiles(): Array<String> = currentService?.listRemoteFiles() ?: emptyArray()

    /** Open a remote file, or null when not activated */
    fun openRemoteFile(path: String): ParcelFileDescriptor? =
        currentService?.openRemoteFile(path)

    /** Delete a remote file, or false when not activated */
    fun deleteRemoteFile(path: String): Boolean = currentService?.deleteRemoteFile(path) ?: false
}
