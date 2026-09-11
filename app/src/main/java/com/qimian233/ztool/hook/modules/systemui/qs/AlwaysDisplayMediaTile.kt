package com.qimian233.ztool.hook.modules.systemui.qs

import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections

/**
 * 测试类：控制中心媒体磁贴常驻 = 判定 hook（pin）+ 数据层占位注入。
 *
 * 1. pin：强制 MediaCarouselInteractor 与 LegacyMediaDataManagerImpl 的
 *    hasXxxOrRecommendation() 无参方法返回 true。366 上 MediaDataManager 接口的
 *    活性实现是 Legacy 版（判定读 LegacyMediaDataFilterImpl.userEntries），
 *    Interactor 集群疑似遗留，两个实现类都 hook 以覆盖任意 Dagger 绑定关系；
 *    QQS/锁屏宿主（showsOnlyActiveMedia=true）只有强制 true 才会常驻。
 *    锁屏 NPE 崩溃已由 KeyguardMediaController.reattachHostView 的 LayoutParams
 *    防护根治（无媒体设备上 hostView 走 0 宽跳过挂载分支、LayoutParams 为 null）。
 * 2. 数据层占位：向数据总线 LegacyMediaDataManagerImpl.onMediaDataLoaded(String,String,MediaData)
 *    （两版同签名）注入合成 MediaData，解决"判定通过但 carousel 无条目 →
 *    MeasurementOutput=0 → hostView 0 高塌缩"（media-tile/媒体磁贴分析报告.md 三、四轮）。
 *    总线开头有 mediaEntries.containsKey 门控，新 key 必须先反射 seed 进 mediaEntries。
 *    占位条目 active=true：锁屏宿主只认活跃媒体（KeyguardMediaController 构造时
 *    setShowsOnlyActiveMedia(true)），active=false 时锁屏不会出现占位卡；
 *    代价是 addOrUpdatePlayer 会立即 reorderAllPlayers（把占位卡排到最前），
 *    单卡场景无感知。
 * 3. 冷启动引导：恢复出厂/从未播放媒体的设备上总线永远无事件，hook 三个 Dagger 单例
 *    构造器捕获实例，并由 MediaHost.updateViewVisibility() 兜底触发"空则注入"
 *    （宿主 attach 时 MediaCarouselController 已构造并注册建卡 listener，注入必达）。
 * 4. 366 联想将内部监听链拆为 LegacyMediaDataFilterImpl（String 键 userEntries /
 *    allEntries），占位撤除与移除观察优先走它；354 回退 MediaDataFilterImpl +
 *    MediaFilterRepository._selectedUserEntries（AOSP 集群在 354 是活链路）。
 * 5. 崩溃防护（真机 366 实证）：无媒体设备上锁屏宿主的 hostView 因
 *    UniqueObjectHostView.addView 的"0 宽跳过挂载"分支拿不到 LayoutParams，
 *    pin 使 KeyguardMediaController 在 state.visible=true 时写
 *    layoutParams.height 直接 NPE 崩溃循环。hook KeyguardMediaController.reattachHostView
 *    补齐 LayoutParams 根治。
 */
class AlwaysDisplayMediaTile : AppHookModule() {

    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        // 构建标识：确认宿主进程实际加载的代码版本（排查"更新后行为未变"类问题）
        logger.info("AlwaysDisplayMediaTile loaded, build=$BUILD_TAG")
        installKeyguardLayoutParamsGuard(param.defaultClassLoader)
        installPinHooks(param.defaultClassLoader)
        installDataLayerHooks(param)
    }

    /**
     * 崩溃防护：KeyguardMediaController.reattachHostView 走 UniqueObjectHostView.addView
     * 的跳过分支时 hostView 不会被真正挂载、LayoutParams 保持 null，随后
     * attachSinglePaneContainer / 可见性监听器在 state.visible=true 时写该字段即 NPE。
     * after-hook 里补一份默认 LayoutParams（宽 MATCH_PARENT / 高 WRAP_CONTENT，
     * 与崩溃点想写的值一致），对未 attach 的 View 仅赋值无副作用。
     */
    private fun installKeyguardLayoutParamsGuard(loader: ClassLoader) {
        try {
            val controllerClass = loader.loadClass(KEYGUARD_MEDIA_CONTROLLER)
            val reattach = controllerClass.getDeclaredMethod("reattachHostView")
            hookWithId(reattach, "media_keyguard_layout_guard") { chain ->
                chain.proceed()
                try {
                    chain.thisObject?.let { controller ->
                        val host = runCatching {
                            findField(controller.javaClass, MEDIA_HOST_FIELD).get(controller)
                        }.getOrNull() ?: return@let
                        val hostView = runCatching {
                            findField(host.javaClass, HOST_VIEW_FIELD).get(host) as? ViewGroup
                        }.getOrNull() ?: return@let
                        if (hostView.layoutParams == null) {
                            hostView.layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            logger.info("Keyguard media hostView layoutParams healed")
                        }
                    }
                } catch (e: Throwable) {
                    logger.error("Keyguard layout guard failed", e)
                }
            }
        } catch (e: ClassNotFoundException) {
            logger.info("KeyguardMediaController not present on this firmware, guard skipped")
        } catch (e: Throwable) {
            logger.error("Failed to install keyguard layout guard", e)
        }
    }

    private fun installPinHooks(loader: ClassLoader) {
        val pinTargets = listOf(
            "interactor" to MEDIA_CAROUSEL_INTERACTOR,
            "legacy" to LEGACY_MEDIA_DATA_MANAGER_IMPL
        )
        for ((alias, className) in pinTargets) {
            val clazz = try {
                loader.loadClass(className)
            } catch (e: ClassNotFoundException) {
                logger.warn("$className not found, pin hook skipped")
                continue
            }
            for (methodName in ALWAYS_VISIBLE_METHODS) {
                var hooked = false
                for (method in clazz.declaredMethods) {
                    if (method.name == methodName && method.parameterTypes.isEmpty()) {
                        try {
                            hookWithId(method, "pin_media_tile_${alias}_$methodName") { _ -> true }
                            hooked = true
                            logger.info("Media tile pinned via $alias.$methodName")
                        } catch (e: Throwable) {
                            logger.error("Failed to pin media tile via $alias.$methodName", e)
                        }
                    }
                }
                if (!hooked) {
                    logger.info("$alias.$methodName not present on this firmware, skipped")
                }
            }
        }
    }

    private fun installDataLayerHooks(param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        installSingletonCaptures(loader)
        installBusWatch(loader)
        installRemovalWatch(loader, LEGACY_MEDIA_DATA_FILTER_IMPL, "media_removed_watch_legacy", isLegacy = true)
        installRemovalWatch(loader, MEDIA_DATA_FILTER_IMPL, "media_removed_watch_aosp", isLegacy = false)
        installHostBootstrap(loader)
    }

    /**
     * hook 三个 Dagger 单例的构造器捕获实例：恢复出厂设备上总线永远无事件，
     * 只靠总线 watch 永远拿不到 manager；filter 实例同理（占位撤除必需）。
     */
    private fun installSingletonCaptures(loader: ClassLoader) {
        captureConstructors(loader, LEGACY_MEDIA_DATA_MANAGER_IMPL, "media_capture_manager") {
            captureManager(it)
        }
        captureConstructors(loader, LEGACY_MEDIA_DATA_FILTER_IMPL, "media_capture_legacy_filter") {
            captureFilter(it, isLegacy = true)
        }
        captureConstructors(loader, MEDIA_DATA_FILTER_IMPL, "media_capture_aosp_filter") {
            captureFilter(it, isLegacy = false)
        }
    }

    private fun captureConstructors(
        loader: ClassLoader,
        className: String,
        idPrefix: String,
        onCaptured: (Any) -> Unit
    ) {
        val clazz = try {
            loader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            logger.warn("$className not found, ctor capture skipped")
            return
        }
        clazz.declaredConstructors.forEachIndexed { index, ctor ->
            try {
                hookWithId(ctor, "${idPrefix}_ctor_$index") { chain ->
                    chain.proceed()
                    try {
                        chain.thisObject?.let(onCaptured)
                    } catch (e: Throwable) {
                        logger.error("$idPrefix capture failed", e)
                    }
                }
            } catch (e: Throwable) {
                logger.error("Failed to hook $className.<init>", e)
            }
        }
    }

    private fun installBusWatch(loader: ClassLoader) {
        try {
            val managerClass = loader.loadClass(LEGACY_MEDIA_DATA_MANAGER_IMPL)
            val mediaDataClass = loader.loadClass(MEDIA_DATA_MODEL)
            val busLoad = managerClass.getDeclaredMethod(
                "onMediaDataLoaded",
                String::class.java,
                String::class.java,
                mediaDataClass
            )
            managerLoadMethod = busLoad
            hookWithId(busLoad, "media_bus_loaded") { chain ->
                val result = chain.proceed()
                try {
                    chain.thisObject?.let { captureManager(it) }
                    val key = chain.args.firstOrNull() as? String
                    if (key != null && key != PLACEHOLDER_KEY) {
                        // 占位条目必须用真实 userId 才能通过 isCurrentProfile 过滤，抢先记录
                        chain.args.getOrNull(2)?.let { data ->
                            runCatching {
                                lastKnownUserId = findField(data.javaClass, USER_ID_FIELD).getInt(data)
                            }
                        }
                        if (placeholderInjected) {
                            postMain { removePlaceholder() }
                        }
                    }
                } catch (e: Throwable) {
                    logger.error("Media bus watch failed", e)
                }
                result
            }
        } catch (e: Throwable) {
            logger.error("Failed to install media bus watch", e)
        }
    }

    private fun installRemovalWatch(
        loader: ClassLoader,
        className: String,
        hookId: String,
        isLegacy: Boolean
    ) {
        try {
            val clazz = loader.loadClass(className)
            val remove = clazz.getDeclaredMethod(
                "onMediaDataRemoved",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            hookWithId(remove, hookId) { chain ->
                val result = chain.proceed()
                try {
                    chain.thisObject?.let { captureFilter(it, isLegacy) }
                    postMain { ensurePlaceholder() }
                } catch (e: Throwable) {
                    logger.error("Media removal watch failed", e)
                }
                result
            }
        } catch (e: ClassNotFoundException) {
            logger.info("$className not present on this firmware, removal watch skipped")
        } catch (e: Throwable) {
            logger.error("Failed to install removal watch on $className", e)
        }
    }

    /**
     * 宿主引导：updateViewVisibility after 触发"空则注入"（此时 carousel 建卡
     * listener 已注册，注入必达建卡链）。
     */
    private fun installHostBootstrap(loader: ClassLoader) {
        try {
            val hostClass = loader.loadClass(MEDIA_HOST)
            for (method in hostClass.declaredMethods) {
                if (method.name == "updateViewVisibility" && method.parameterTypes.isEmpty()) {
                    hookWithId(method, "media_host_visibility_bootstrap") { chain ->
                        chain.proceed()
                        try {
                            postMain { ensurePlaceholder() }
                        } catch (e: Throwable) {
                            logger.error("Media bootstrap failed", e)
                        }
                    }
                }
            }
        } catch (e: ClassNotFoundException) {
            logger.warn("MediaHost not found, bootstrap skipped")
        } catch (e: Throwable) {
            logger.error("Failed to install host bootstrap", e)
        }
    }

    private fun captureManager(manager: Any) {
        if (managerInstance == null) {
            managerInstance = manager
            ensureAudioWatch(manager)
        }
    }

    private fun captureFilter(filter: Any, isLegacy: Boolean) {
        if (isLegacy) {
            if (legacyFilterInstance == null) {
                legacyFilterInstance = filter
                legacyFilterRemoveMethod = resolveRemoveMethod(filter)
            }
        } else if (aospFilterInstance == null) {
            aospFilterInstance = filter
            aospFilterRemoveMethod = resolveRemoveMethod(filter)
        }
    }

    private fun resolveRemoveMethod(filter: Any): Method? = runCatching {
        filter.javaClass.getDeclaredMethod(
            "onMediaDataRemoved",
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
    }.onFailure { logger.error("Failed to resolve onMediaDataRemoved", it) }.getOrNull()

    private fun ensurePlaceholder() {
        if (placeholderInjected || injectFailures >= MAX_INJECT_FAILURES) {
            return
        }
        val manager = managerInstance ?: return
        val entries = primaryEntries()
        val hasRealData = when {
            entries != null -> entries.isNotEmpty()
            // filter 未捕获（或字段读取失败）时回退读 manager 缓存；读取失败按"有数据"
            // 处理，避免盲目注入造成双卡
            else -> runCatching {
                val cached = findField(manager.javaClass, ENTRIES_FIELD).get(manager) as? Map<*, *>
                    ?: return@runCatching true
                cached.keys.any { it != PLACEHOLDER_KEY }
            }.getOrDefault(true)
        }
        if (hasRealData) {
            return
        }
        injectPlaceholder()
    }

    private fun injectPlaceholder() {
        val manager = managerInstance ?: return
        val context = runCatching {
            findField(manager.javaClass, CONTEXT_FIELD).get(manager) as? Context
        }.getOrNull() ?: run {
            injectFailures++
            logger.warn("Media manager context unavailable, placeholder skipped")
            return
        }
        val data = buildPlaceholderMediaData(context) ?: run {
            injectFailures++
            return
        }
        // 总线 onMediaDataLoaded 开头有 mediaEntries.containsKey 门控（两版同款），
        // 新 key 直接调用会被无视——必须先把条目写进 mediaEntries 再走通知
        val entries = runCatching {
            findField(manager.javaClass, ENTRIES_FIELD).get(manager) as? MutableMap<Any?, Any?>
        }.getOrNull() ?: run {
            injectFailures++
            logger.warn("mediaEntries unavailable, placeholder skipped")
            return
        }
        runCatching { entries[PLACEHOLDER_KEY] = data }.onFailure {
            injectFailures++
            logger.error("Failed to seed mediaEntries with placeholder", it)
            return
        }
        placeholderInjected = true
        runCatching { managerLoadMethod?.invoke(manager, PLACEHOLDER_KEY, null, data) }
            .onFailure {
                placeholderInjected = false
                injectFailures++
                logger.error("Failed to notify placeholder via bus", it)
                return
            }
        logger.info("Media placeholder injected (userId=$lastKnownUserId, app=${latestAudioApp?.label ?: "none"})")
    }

    private fun removePlaceholder() {
        val filter = legacyFilterInstance ?: aospFilterInstance ?: run {
            logger.warn("No filter captured, placeholder removal skipped")
            return
        }
        val method = legacyFilterRemoveMethod ?: aospFilterRemoveMethod ?: return
        placeholderInjected = false
        runCatching { method.invoke(filter, PLACEHOLDER_KEY, false) }
            .onFailure { logger.error("Failed to remove placeholder via filter", it) }
        managerInstance?.let { manager ->
            runCatching {
                (findField(manager.javaClass, ENTRIES_FIELD).get(manager) as? MutableMap<*, *>)
                    ?.remove(PLACEHOLDER_KEY)
            }
        }
        logger.info("Media placeholder removed for real media")
    }

    /** 占位已显示时分应用音量发生变化：同 key 覆盖更新占位内容。 */
    private fun refreshPlaceholder() {
        if (!placeholderInjected) {
            return
        }
        val manager = managerInstance ?: return
        val entries = primaryEntries()
        // 仅当条目集合里只有占位（无真实媒体）时才刷新
        if (entries == null || entries.size > 1) {
            return
        }
        val context = runCatching {
            findField(manager.javaClass, CONTEXT_FIELD).get(manager) as? Context
        }.getOrNull() ?: return
        val data = buildPlaceholderMediaData(context) ?: return
        runCatching { managerLoadMethod?.invoke(manager, PLACEHOLDER_KEY, PLACEHOLDER_KEY, data) }
            .onFailure { logger.error("Failed to refresh placeholder", it) }
    }

    /**
     * 空态真值源：366 读 LegacyMediaDataFilterImpl.userEntries（Legacy hasAnyMedia
     * 与建卡链共用的同一张表）；354 回退 MediaDataFilterImpl →
     * MediaFilterRepository._selectedUserEntries（AOSP 集群在 354 是活链路）。
     */
    private fun primaryEntries(): Map<*, *>? {
        legacyFilterInstance?.let { filter ->
            return runCatching {
                findField(filter.javaClass, USER_ENTRIES_FIELD).get(filter) as? Map<*, *>
            }.onFailure { logger.error("Failed to read userEntries", it) }.getOrNull()
        }
        aospFilterInstance?.let { filter ->
            return runCatching {
                val repository = findField(filter.javaClass, FILTER_REPOSITORY_FIELD).get(filter)
                val flow = findField(repository.javaClass, SELECTED_ENTRIES_FIELD).get(repository)
                val getValue = flow.javaClass.methods.firstOrNull { it.name == "getValue" }
                getValue?.isAccessible = true
                getValue?.invoke(flow) as? Map<*, *>
            }.onFailure { logger.error("Failed to read selectedUserEntries", it) }.getOrNull()
        }
        return null
    }

    /**
     * 构造占位 MediaData。两版均有 28 参全参构造器（分析报告的"30 参"是 smali
     * 寄存器计数）与 28 参掩码版，唯一可靠区分是末参类型：全参版是装箱
     * java.lang.Double，掩码版是原始 int（Kotlin 的 Double::class.java 是原始
     * double.class，必须写 java.lang.Double::class.java）。掩码版末参位置实际是
     * 恢复进度 Double + int 掩码，共 29 寄存器位——真机日志确认两构造器均为 28 参。
     * 字段取值对齐系统 LOADING/smartspace 恢复卡先例（全 null 组合为常态渲染路径）。
     */
    private fun buildPlaceholderMediaData(context: Context): Any? {
        return try {
            val modelClass = context.classLoader.loadClass(MEDIA_DATA_MODEL)
            val ctor = modelClass.constructors.firstOrNull {
                it.parameterTypes.size == 28 &&
                    it.parameterTypes.last() == java.lang.Double::class.java
            } ?: run {
                val shapes = modelClass.constructors.joinToString { c ->
                    "${c.parameterTypes.size}:last=${c.parameterTypes.last().name}"
                }
                logger.warn("MediaData full constructor not found (available: $shapes)")
                return null
            }
            val emptyList = Collections.emptyList<Any>()
            val now = System.currentTimeMillis()
            val audio = latestAudioApp
            val zh = context.resources.configuration.locales[0].language == "zh"
            ctor.newInstance(
                lastKnownUserId,                                  // 1 userId
                true,                                             // 2 initialized
                audio?.label ?: if (zh) "媒体控制" else "Media",   // 3 app
                null,                                             // 4 appIcon
                null,                                             // 5 artist
                if (audio != null) {
                    if (zh) "正在播放" else "Playing"
                } else {
                    if (zh) "未在播放" else "No media playing"
                },                                                // 6 song
                null,                                             // 7 artwork
                emptyList,                                        // 8 actions
                emptyList,                                        // 9 actionsToShowInCompact
                null,                                             // 10 semanticActions
                audio?.pkg ?: "INVALID",                          // 11 packageName
                null,                                             // 12 token
                audio?.launchIntent,                              // 13 clickIntent
                null,                                             // 14 device
                true,                                             // 15 active（锁屏宿主只认活跃媒体）
                null,                                             // 16 resumeAction
                0,                                                // 17 playbackLocation
                true,                                             // 18 resumption
                PLACEHOLDER_KEY,                                  // 19 notificationKey
                true,                                             // 20 hasCheckedForResume
                null,                                             // 21 isPlaying
                false,                                            // 22 isClearable
                now,                                              // 23 lastActive
                now,                                              // 24 createdTimestampMillis
                fakeInstanceId(),                                 // 25 instanceId
                audio?.uid ?: Process.INVALID_UID,                // 26 appUid
                false,                                            // 27 isExplicit
                0.0                                               // 28 resumeProgress
            )
        } catch (e: Throwable) {
            logger.error("Failed to build placeholder MediaData", e)
            null
        }
    }

    private fun fakeInstanceId(): Any? = runCatching {
        Class.forName(INSTANCE_ID_CLASS)
            .getDeclaredMethod("fakeInstanceId", Int::class.javaPrimitiveType)
            .invoke(null, -1)
    }.onFailure { logger.error("fakeInstanceId unavailable", it) }.getOrNull()

    /**
     * 监听系统音频流（分应用音量的数据源）：有 MEDIA/GAME 用途流的应用视为正在
     * 播放，取最近一条作为占位展示对象。无声保活应用同样会被捕获，按用户约定
     * 属可接受行为。回调已指定主线程 Handler。
     */
    private fun ensureAudioWatch(manager: Any) {
        if (audioWatchInstalled) {
            return
        }
        val context = runCatching {
            findField(manager.javaClass, CONTEXT_FIELD).get(manager) as? Context
        }.getOrNull() ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            audioWatchInstalled = true
            return
        }
        audioWatchInstalled = true
        try {
            audioManager.registerAudioPlaybackCallback(
                object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                        try {
                            updateLatestAudioApp(context, configs)
                        } catch (e: Throwable) {
                            logger.warn("Audio config update failed: ${e.message}")
                        }
                    }
                },
                Handler(Looper.getMainLooper())
            )
            logger.info("Audio playback watch installed")
        } catch (e: Throwable) {
            logger.warn("Audio playback watch unavailable: ${e.message}")
        }
    }

    private fun updateLatestAudioApp(context: Context, configs: MutableList<AudioPlaybackConfiguration>) {
        // "谁最近开始播放"：照抄官方 AudioPlayerStateMonitor 思路——每个流由非 active
        // 翻转为 active 时记录时间戳，取时间最大者。回调 List 顺序≈播放器创建顺序，
        // 与"最近开始播放"无关，不能直接用列表位置。
        var latestEventTime = -1L
        var latestEventUid = -1
        val livePiids = mutableSetOf<Int>()
        for (config in configs) {
            val piid = intGetter(config, "getPlayerInterfaceId") ?: continue
            livePiids.add(piid)
            val active = isStreamActive(config)
            val previous = piidActiveStates.put(piid, active)
            if (active && previous != true) {
                val now = SystemClock.elapsedRealtime()
                if (now >= latestEventTime) {
                    latestEventUid = clientUid(config) ?: continue
                    latestEventTime = now
                }
            }
        }
        piidActiveStates.keys.retainAll(livePiids)

        val activeUids = configs.filter { isStreamActive(it) }
            .mapNotNull { clientUid(it) }
            .toSet()
        latestAudioEventTimeByUid.keys.retainAll(activeUids)
        if (latestEventUid >= 0) {
            latestAudioEventTimeByUid[latestEventUid] = latestEventTime
        }
        val latestUid = activeUids.maxByOrNull { latestAudioEventTimeByUid[it] ?: 0L }
        val app = latestUid?.let { buildAudioApp(context, it) }
        val changed = app?.pkg != latestAudioApp?.pkg
        latestAudioApp = app
        if (changed && placeholderInjected) {
            // onPlaybackConfigChanged 已在主线程（注册时指定 main Handler）
            refreshPlaceholder()
        }
    }

    private fun buildAudioApp(context: Context, uid: Int): AudioApp? {
        val pkg = context.packageManager.getPackagesForUid(uid)?.firstOrNull()
            ?: return null
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrNull() ?: pkg
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        val clickIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                pkg.hashCode(),
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return AudioApp(label, pkg, uid, clickIntent)
    }

    /**
     * "正在出声"判定：MEDIA/GAME 用途 → 排除 SoundPool 音效流（PLAYER_TYPE=3，
     * MediaSessionService 官方做法）→ isActive()（A15 起含 0 音量/VolumeShaper/
     * AppOps 静音判定，无声保活流不算 active），isActive 缺失时回退
     * getPlayerState()==STARTED(2)。全零 PCM 型保活流 framework 层无法识别，属
     * 已知限制。
     */
    private fun isStreamActive(config: AudioPlaybackConfiguration): Boolean {
        val usageOk = runCatching {
            val usage = config.audioAttributes.usage
            usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME
        }.getOrDefault(false)
        if (!usageOk) {
            return false
        }
        if (intGetter(config, "getPlayerType") == PLAYER_TYPE_SOUNDPOOL) {
            return false
        }
        val isActive = config.javaClass.methods.firstOrNull { it.name == "isActive" }
        if (isActive != null) {
            isActive.isAccessible = true
            return runCatching { isActive.invoke(config) as Boolean }.getOrDefault(false)
        }
        return intGetter(config, "getPlayerState") == PLAYER_STATE_STARTED
    }

    private fun intGetter(config: AudioPlaybackConfiguration, methodName: String): Int? = runCatching {
        val method = config.javaClass.methods.firstOrNull { it.name == methodName }
            ?: return@runCatching null
        method.isAccessible = true
        method.invoke(config) as Int
    }.getOrNull()

    private fun clientUid(config: AudioPlaybackConfiguration): Int? =
        intGetter(config, "getClientUid")?.takeIf { it >= 0 }

    private fun postMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /** 正在播放音频的应用快照（占位条目的展示与点击目标）。 */
    private class AudioApp(
        val label: String,
        val pkg: String,
        val uid: Int,
        val launchIntent: PendingIntent?
    )

    private companion object {
        // 构建标识：用于在宿主日志中确认实际加载的代码版本
        const val BUILD_TAG = "keyguardpin-20260911"
        const val MEDIA_CAROUSEL_INTERACTOR =
            "com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor"
        const val LEGACY_MEDIA_DATA_MANAGER_IMPL =
            "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl"
        const val LEGACY_MEDIA_DATA_FILTER_IMPL =
            "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataFilterImpl"
        const val MEDIA_DATA_FILTER_IMPL =
            "com.android.systemui.media.controls.domain.pipeline.MediaDataFilterImpl"
        const val MEDIA_DATA_MODEL =
            "com.android.systemui.media.controls.shared.model.MediaData"
        const val MEDIA_HOST =
            "com.android.systemui.media.controls.ui.view.MediaHost"
        const val KEYGUARD_MEDIA_CONTROLLER =
            "com.android.systemui.media.controls.ui.controller.KeyguardMediaController"
        const val INSTANCE_ID_CLASS = "com.android.internal.logging.InstanceId"
        const val PLACEHOLDER_KEY = "ztool_media_placeholder"
        const val CONTEXT_FIELD = "context"
        const val ENTRIES_FIELD = "mediaEntries"
        const val USER_ENTRIES_FIELD = "userEntries"
        const val FILTER_REPOSITORY_FIELD = "mediaFilterRepository"
        const val SELECTED_ENTRIES_FIELD = "_selectedUserEntries"
        const val USER_ID_FIELD = "userId"
        const val MEDIA_HOST_FIELD = "mediaHost"
        const val HOST_VIEW_FIELD = "hostView"

        val ALWAYS_VISIBLE_METHODS = arrayOf(
            "hasActiveMediaOrRecommendation",
            "hasAnyMediaOrRecommendation"
        )
        const val PLAYER_TYPE_SOUNDPOOL = 3
        const val PLAYER_STATE_STARTED = 2
        const val MAX_INJECT_FAILURES = 5

        // mediaFrame 数据链全进程单例，以下状态按进程缓存
        var managerInstance: Any? = null
        var legacyFilterInstance: Any? = null
        var aospFilterInstance: Any? = null
        var placeholderInjected = false
        var lastKnownUserId = 0
        var injectFailures = 0
        var latestAudioApp: AudioApp? = null
        var audioWatchInstalled = false
        val mainHandler = Handler(Looper.getMainLooper())
        val piidActiveStates = mutableMapOf<Int, Boolean>()
        val latestAudioEventTimeByUid = mutableMapOf<Int, Long>()

        // 注入/撤除方法在 hooker 回调外缓存（安装时解析）
        var managerLoadMethod: Method? = null
        var legacyFilterRemoveMethod: Method? = null
        var aospFilterRemoveMethod: Method? = null
    }
}
