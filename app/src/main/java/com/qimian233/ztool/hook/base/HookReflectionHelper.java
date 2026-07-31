package com.qimian233.ztool.hook.base;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hook 反射工具。
 * <p>
 * 从 {@link BaseHookModule} 拆分出来的反射辅助方法，均为静态工具方法。
 * 提供 XposedHelpers 风格的 {@link #findField} / {@link #findMethod}，
 * 沿继承链向上递归查找。
 * </p>
 */
public final class HookReflectionHelper {

    private HookReflectionHelper() {}

    /**
     * 在 {@code startClass} 及其父类中递归查找指定名称的字段。
     * <p>找到的字段会自动 {@link Field#setAccessible setAccessible(true)}。</p>
     *
     * @param startClass 起始类
     * @param name       字段名
     * @return 可访问的 {@link Field}
     * @throws NoSuchFieldException 如果在整个继承链中都未找到
     */
    public static Field findField(Class<?> startClass, String name)
            throws NoSuchFieldException {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " in " + startClass);
    }

    /**
     * 在 {@code startClass} 及其父类中递归查找指定签名的方法。
     * <p>找到的方法会自动 {@link Method#setAccessible setAccessible(true)}。</p>
     *
     * @param startClass     起始类
     * @param name           方法名
     * @param parameterTypes 参数类型（变长）
     * @return 可访问的 {@link Method}
     * @throws NoSuchMethodException 如果在整个继承链中都未找到
     */
    public static Method findMethod(Class<?> startClass, String name,
                                    Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = startClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name + " in " + startClass);
    }
}
