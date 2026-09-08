package com.qimian233.ztool.utils

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import java.io.File

class MagiskModuleManager {

    // 检查模块是否启用
    val isModuleEnabled: Boolean
        get() {
            val result = EnhancedShellExecutor.getInstance()
                .executeRootCommand("ls /data/adb/modules", 2)
            return result.isSuccess && result.output.contains(MODULE_ID)
        }

    // 移除模块
    fun removeModule(context: Context): String {
        val tempDirPath = context.filesDir.absolutePath + "/" + MODULE_ID + "_temp"
        FileUtils.deleteRecursive(File(tempDirPath))

        val executor = EnhancedShellExecutor.getInstance()

        // 执行卸载脚本
        val res0 = executor.executeRootCommand("sh " + MODULE_PATH + "/uninstall.sh")
        if (!res0.isSuccess) return context.getString(R.string.common_error_shell_command_failed, "Failed to execute uninstall.sh")

        // 删除模块目录
        val res1 = executor.executeRootCommand("rm -rf " + MODULE_PATH)
        if (!res1.isSuccess) return context.getString(R.string.common_error_shell_command_failed, "Failed to remove module dir")

        // 删除系统配置残留 (可选，视需求而定)
        val res2 = executor.executeRootCommand("rm -f " + MODULE_CONFIG_PATH)
        if (!res2.isSuccess) return context.getString(R.string.common_error_shell_command_failed, "Failed to remove config")

        return "success"
    }

    // 安装/复制模块
    fun installModule(context: Context): String {
        val sourceAssetsPath = "embedding/" + MODULE_ID
        val tempDirPath = context.filesDir.absolutePath + "/" + MODULE_ID + "_temp"
        val tempDir = File(tempDirPath)

        // 1. 清理并准备临时目录
        FileUtils.deleteRecursive(tempDir)
        if (!tempDir.mkdirs()) return context.getString(R.string.common_error_create_temp_dir)

        // 2. 从 Assets 复制到私有目录
        if (FileUtils.copyAssetsToDirectory(context, sourceAssetsPath, tempDir)) {
            return context.getString(R.string.common_error_copy_assets)
        }

        val moduleInstallPath = "/data/adb/modules_update/" + MODULE_ID

        // 3. 使用 Root 移动到目标目录并设置权限
        val cmd = "mkdir -p " + moduleInstallPath + " && " +
                "cp -r " + tempDirPath + "/* " + moduleInstallPath + "/ && " +
                "chmod -R 755 " + moduleInstallPath + " && " +
                "chown -R 0:0 " + moduleInstallPath + " && " +
                "chcon -R u:object_r:system_file:s0 " + moduleInstallPath + " && " +
                "find " + moduleInstallPath + " -type d -exec chmod 755 {} \\; && " + // 目录权限 755
                "find " + moduleInstallPath + " -type f -exec chmod 644 {} \\; && " + // 普通文件权限 644
                "find " + moduleInstallPath + " -name '*.sh' -exec chmod 755 {} \\;" +
                " && sh " + moduleInstallPath + "/customize.sh --install"

        val result = EnhancedShellExecutor.getInstance().executeRootCommand(cmd, 10)

        // 清理临时文件
        FileUtils.deleteRecursive(tempDir)

        if (!result.isSuccess) {
            return context.getString(R.string.common_error_shell_command_failed, result.error)
        }
        return "success"
    }

    companion object {
        private const val MODULE_ID = "zuxos_embedding"
        private const val MODULE_PATH = "/data/adb/modules/" + MODULE_ID
        private const val MODULE_CONFIG_PATH = "/data/system/zui/embedding/embedding_config.json"
    }
}
