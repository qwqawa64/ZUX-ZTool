package com.qimian233.ztool.dexindex.base

import com.qimian233.ztool.dexindex.indexer.LauncherDexIndexer
import com.qimian233.ztool.dexindex.indexer.MobileDesktopDexIndexer
import com.qimian233.ztool.dexindex.indexer.SystemUiDexIndexer

/**
 * 全部离线索引器的注册表。新增使用 DexKit 的作用域时在此登记。
 */
object DexIndexRegistry {

    val indexers: List<DexIndexer> = listOf(
        LauncherDexIndexer(),
        SystemUiDexIndexer(),
        MobileDesktopDexIndexer(),
    )
}
