package com.qimian233.ztool.hook.base

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Hook 反射工具（Kotlin 版）。
 * <p>
 * 提供 XposedHelpers 风格的 [findField] / [findMethod]，沿继承链向上递归查找。
 * 由 [BaseHookModule] 的成员方法委托调用。
 * </p>
 */
object HookReflectionHelper {

    /**
     * 在 [startClass] 及其父类中递归查找指定名称的字段。
     * <p>找到的字段会自动 [Field.isAccessible] 置为 true。</p>
     *
     * @param startClass 起始类
     * @param name       字段名
     * @return 可访问的 [Field]
     * @throws NoSuchFieldException 如果在整个继承链中都未找到
     */
    @JvmStatic
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
     * 在 [startClass] 及其父类中递归查找指定签名的方法。
     * <p>找到的方法会自动 [Method.isAccessible] 置为 true。</p>
     *
     * @param startClass     起始类
     * @param name           方法名
     * @param parameterTypes 参数类型（变长）
     * @return 可访问的 [Method]
     * @throws NoSuchMethodException 如果在整个继承链中都未找到
     */
    @JvmStatic
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
