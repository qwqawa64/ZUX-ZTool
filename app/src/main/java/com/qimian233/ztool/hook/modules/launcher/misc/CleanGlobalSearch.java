package com.qimian233.ztool.hook.modules.launcher.misc;

import com.qimian233.ztool.hook.base.AppHookModule;
import com.qimian233.ztool.hook.base.DexKitHelper;

import io.github.libxposed.api.XposedModuleInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 清理全局搜索 — 移除热词视图和搜索推荐。
 * <p>
 * 使用 DEXKit 通过方法签名动态查找混淆后的方法名（K0/T0/E0 等），
 * 不再依赖硬编码名称。
 * </p>
 */
public class CleanGlobalSearch extends AppHookModule {

    private static final String TARGET_CLASS = "com.zui.launcher.GlobalSearchView";

    private boolean NO_SEARCH_BOX_RECOMMEND = false;
    private boolean NO_HOT_WORD_VIEW = false;

    public CleanGlobalSearch() {}

    @Override
    public String getModuleName() {
        return "clean_global_search";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.zui.launcher"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        getPreferenceSettings();

        DexKitBridge bridge = DexKitHelper.INSTANCE.getBridgeForClass(
                classLoader, TARGET_CLASS);

        // ── 移除热词视图 ──────────────────────────────────────
        if (this.NO_HOT_WORD_VIEW) {
            try {
                Class<?> globalSearchViewClass = classLoader.loadClass(TARGET_CLASS);

                // 通过 DEXKit 查找无参 void 方法（混淆后的 K0/T0）
                List<String> hotwordMethodNames = new java.util.ArrayList<>();
                if (bridge != null) {
                    try {
                        List<MethodData> methods = bridge.findMethod(FindMethod.create()
                                .searchPackages("com.zui.launcher")
                                .matcher(MethodMatcher.create()
                                        .paramTypes()  // 无参数
                                        .returnType("void")
                                        .declaredClass(TARGET_CLASS)
                                )
                        );
                        for (MethodData md : methods) {
                            hotwordMethodNames.add(md.getName());
                        }
                    } catch (Throwable ignored) {}
                }
                // 回退：保留原有的硬编码名称列表
                if (hotwordMethodNames.isEmpty()) {
                    hotwordMethodNames.add("K0");
                    hotwordMethodNames.add("T0");
                }

                boolean hooked = false;
                for (String methodName : hotwordMethodNames) {
                    try {
                        Method method = globalSearchViewClass.getDeclaredMethod(methodName);
                        hookWithId(method, "method", chain -> null);
                        logger.debug("Hooked hotword inflation method: " + methodName);
                        hooked = true;
                        break;
                    } catch (NoSuchMethodError | Exception e) {
                        logger.debug("Method " + methodName + " not found, trying next...");
                    }
                }
                if (!hooked) {
                    logger.error("Unable to find any hotword inflation method");
                }

                // 查找 E0(List) — 单参数 List，void 返回
                String e0Name = "E0";
                if (bridge != null) {
                    try {
                        List<MethodData> e0Methods = bridge.findMethod(FindMethod.create()
                                .searchPackages("com.zui.launcher")
                                .matcher(MethodMatcher.create()
                                        .paramTypes("java.util.List")
                                        .returnType("void")
                                        .declaredClass(TARGET_CLASS)
                                )
                        );
                        if (!e0Methods.isEmpty()) {
                            e0Name = e0Methods.get(0).getName();
                        }
                    } catch (Throwable ignored) {}
                }

                try {
                    Class<?> listClass = classLoader.loadClass("java.util.List");
                    Method e0Method = globalSearchViewClass.getDeclaredMethod(e0Name, listClass);
                    hookWithId(e0Method, "hook_115", chain -> null);
                    logger.info("Hooked hotword data method: " + e0Name);
                } catch (NoSuchMethodError | Exception ignored) {
                    logger.error("Unable to find GlobalSearchView hotword data method");
                }
            } catch (Throwable t) {
                logger.error("Failed to install hotword removal hooks", t);
            }
        }

        // ── 移除搜索框推荐 ──────────────────────────────────────
        if (this.NO_SEARCH_BOX_RECOMMEND) {
            try {
                Class<?> globalSearchViewClass = classLoader.loadClass(TARGET_CLASS);
                // setHotWordHint 不是混淆的，直接使用
                Method setHotWordHintMethod = globalSearchViewClass.getDeclaredMethod("setHotWordHint");
                hookWithId(setHotWordHintMethod, "set_hot_word_hint", chain -> null);
                logger.info("Hooked setHotWordHint");
            } catch (NoSuchMethodError | Exception ignored) {
                logger.error("Unable to find GlobalSearchView#setHotWordHint.");
            }
        }
    }

    private void getPreferenceSettings() {
        try {
            this.NO_HOT_WORD_VIEW = this.xposed.getRemotePreferences("xposed_module_config")
                    .getBoolean("remove_hot_word_view", false);
        } catch (Throwable t) {
            this.NO_HOT_WORD_VIEW = false;
        }
        try {
            this.NO_SEARCH_BOX_RECOMMEND = this.xposed.getRemotePreferences("xposed_module_config")
                    .getBoolean("remove_search_recommend", false);
        } catch (Throwable t) {
            this.NO_SEARCH_BOX_RECOMMEND = false;
        }
    }
}
