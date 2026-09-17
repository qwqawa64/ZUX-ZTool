package com.qimian233.ztool.dexindex.base

import com.qimian233.ztool.dexindex.indexer.LauncherDexIndexer
import com.qimian233.ztool.dexindex.indexer.MobileDesktopDexIndexer
import com.qimian233.ztool.dexindex.indexer.SystemUiDexIndexer

/**
 * Registry of all offline indexers. Register a new DexKit-based scope here.
 */
object DexIndexRegistry {

    val indexers: List<DexIndexer> = listOf(
        LauncherDexIndexer(),
        SystemUiDexIndexer(),
        MobileDesktopDexIndexer(),
    )
}
