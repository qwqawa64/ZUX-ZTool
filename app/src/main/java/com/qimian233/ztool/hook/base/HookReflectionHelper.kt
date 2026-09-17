package com.qimian233.ztool.hook.base

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Hook reflection utilities (Kotlin version).
 * <p>
 * Provides XposedHelpers-style [findField] / [findMethod], recursively walking
 * up the inheritance chain. Invoked via delegation from [BaseHookModule] members.
 * </p>
 */
object HookReflectionHelper {

    /**
     * Recursively finds the field of the given name in [startClass] and its superclasses.
     * <p>The found field is automatically set accessible via [Field.isAccessible].</p>
     *
     * @param startClass starting class
     * @param name       field name
     * @return an accessible [Field]
     * @throws NoSuchFieldException if not found anywhere in the inheritance chain
     */
    @Throws(NoSuchFieldException::class)
    fun findField(startClass: Class<*>?, name: String): Field {
        var current: Class<*>? = startClass
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("$name in $startClass")
    }

    /**
     * Recursively finds the method of the given signature in [startClass] and its superclasses.
     * <p>The found method is automatically set accessible via [Method.isAccessible].</p>
     *
     * @param startClass     starting class
     * @param name           method name
     * @param parameterTypes parameter types (varargs)
     * @return an accessible [Method]
     * @throws NoSuchMethodException if not found anywhere in the inheritance chain
     */
    @Throws(NoSuchMethodException::class)
    fun findMethod(startClass: Class<*>?, name: String, vararg parameterTypes: Class<*>?): Method {
        var current: Class<*>? = startClass
        while (current != null) {
            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        throw NoSuchMethodException("$name in $startClass")
    }
}
