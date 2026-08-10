package com.qimian233.ztool.dexindex.base

import android.content.Context
import com.google.gson.JsonObject
import org.luckypray.dexkit.DexKitBridge

/**
 * 作用域级离线索引器。
 *
 * 每个目标作用域（包）一个实现，负责该包下所有使用 DexKit 的 Hook 模块的
 * 方法/字段名预计算。**不得依赖 libxposed**（在模块 app 进程内运行）。
 *
 * 实现约定：
 * - [index] 内每个模块的查询各自 try-catch，单个失败不影响其他；
 * - 输出按 `DexIndexConstants.ModuleKeys` 分组的 JsonObject，字段 key 用
 *   `DexIndexConstants.Keys`；
 * - **不写 fallback 值**：查询失败则不写该 key，由 Hook 侧回退硬编码。
 */
interface DexIndexer {

    /** 目标作用域包名（引用 `ScopeKeys.CONSTANT.packageName`）。 */
    val scopePackage: String

    /**
     * 对给定 bridge 执行该作用域的全部查询。
     * 返回形如 `{ "<moduleKey>": { "<fieldKey>": "<value>" } }` 的 JSON——
     * **只含 modules 映射本身**，root 包装（schemaVersion/apk 指纹等）由
     * [DexIndexManager] 负责，避免双重嵌套。
     */
    fun index(bridge: DexKitBridge, context: Context): JsonObject
}
