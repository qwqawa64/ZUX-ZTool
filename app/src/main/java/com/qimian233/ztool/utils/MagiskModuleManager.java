package com.qimian233.ztool.utils;

import android.content.Context;
import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.R;

import java.io.File;

public class MagiskModuleManager {
    private static final String MODULE_ID = "zuxos_embedding";
    private static final String MODULE_PATH = "/data/adb/modules/" + MODULE_ID;
    private static final String MODULE_CONFIG_PATH = "/data/system/zui/embedding/embedding_config.json";

    // 检查模块是否启用
    public boolean isModuleEnabled() {
        EnhancedShellExecutor.ShellResult result = EnhancedShellExecutor.getInstance()
                .executeRootCommand("ls /data/adb/modules", 2);
        return result.isSuccess() && result.output.contains(MODULE_ID);
    }

    // 移除模块
    public String removeModule(Context context) {
        String tempDirPath = context.getFilesDir().getAbsolutePath() + "/" + MODULE_ID + "_temp";
        FileUtils.deleteRecursive(new File(tempDirPath));

        EnhancedShellExecutor executor = EnhancedShellExecutor.getInstance();

        // 执行卸载脚本
        EnhancedShellExecutor.ShellResult res0 = executor.executeRootCommand("sh " + MODULE_PATH + "/uninstall.sh");
        if (!res0.isSuccess()) return context.getString(R.string.error_shell_command_failed, "Failed to execute uninstall.sh");

        // 删除模块目录
        EnhancedShellExecutor.ShellResult res1 = executor.executeRootCommand("rm -rf " + MODULE_PATH);
        if (!res1.isSuccess()) return context.getString(R.string.error_shell_command_failed, "Failed to remove module dir");

        // 删除系统配置残留 (可选，视需求而定)
        EnhancedShellExecutor.ShellResult res2 = executor.executeRootCommand("rm -f " + MODULE_CONFIG_PATH);
        if (!res2.isSuccess()) return context.getString(R.string.error_shell_command_failed, "Failed to remove config");

        return "success";
    }

    // 安装/复制模块
    public String installModule(Context context) {
        String sourceAssetsPath = "embedding/" + MODULE_ID;
        String tempDirPath = context.getFilesDir().getAbsolutePath() + "/" + MODULE_ID + "_temp";
        File tempDir = new File(tempDirPath);

        // 1. 清理并准备临时目录
        FileUtils.deleteRecursive(tempDir);
        if (!tempDir.mkdirs()) return context.getString(R.string.error_create_temp_dir);

        // 2. 从 Assets 复制到私有目录
        if (FileUtils.copyAssetsToDirectory(context, sourceAssetsPath, tempDir)) {
            return context.getString(R.string.error_copy_assets);
        }

        String MODULE_INSTALL_PATH = "/data/adb/modules_update/" + MODULE_ID;

        // 3. 使用 Root 移动到目标目录并设置权限
        String cmd = "mkdir -p " + MODULE_INSTALL_PATH + " && " +
                "cp -r " + tempDirPath + "/* " + MODULE_INSTALL_PATH + "/ && " +
                "chmod -R 755 " + MODULE_INSTALL_PATH + " && " +
                "chown -R 0:0 " + MODULE_INSTALL_PATH + " && " +
                "chcon -R u:object_r:system_file:s0 " + MODULE_INSTALL_PATH + " && " +
                "find " + MODULE_INSTALL_PATH + " -type d -exec chmod 755 {} \\; && " + // 目录权限 755
                "find " + MODULE_INSTALL_PATH + " -type f -exec chmod 644 {} \\; && " + // 普通文件权限 644
                "find " + MODULE_INSTALL_PATH + " -name '*.sh' -exec chmod 755 {} \\;" +
                " && sh " + MODULE_INSTALL_PATH + "/customize.sh --install";

        EnhancedShellExecutor.ShellResult result = EnhancedShellExecutor.getInstance().executeRootCommand(cmd, 10);

        // 清理临时文件
        FileUtils.deleteRecursive(tempDir);

        if (!result.isSuccess()) {
            return context.getString(R.string.error_shell_command_failed, result.error);
        }
        return "success";
    }
}
