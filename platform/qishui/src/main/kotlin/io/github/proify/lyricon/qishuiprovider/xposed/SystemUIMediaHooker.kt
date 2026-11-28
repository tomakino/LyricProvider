/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuiprovider.xposed

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.qishuikit.api.QishuiLyricApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * systemui 侧媒体会话监听器。
 *
 * 监听汽水的播放会话，把曲目与歌词注册为 Lyricon provider。
 */
object SystemUIMediaHooker : YukiBaseHooker() {

    private const val TAG = "LunaSystemUI"
    private const val TARGET_PACKAGE = "com.luna.music"

    /** 已转换的歌词，按曲目 ID 缓存。 */
    private val songCache = ConcurrentHashMap<String, Song>()

    /** 已注册回调的媒体控制器，按会话 Token 缓存。 */
    private val controllers = ConcurrentHashMap<MediaSession.Token, MediaController>()

    /** 会话回调分发线程。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 网络与解析协程作用域。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 注册到 Lyricon 的 provider。 */
    private var provider: LyriconProvider? = null

    /** 歌词接口客户端。 */
    private var kit: QishuiLyricApi? = null

    /** 歌词磁盘缓存。 */
    private var cache: LyricCache? = null

    @Volatile
    private var pendingPauseJob: kotlinx.coroutines.Job? = null

    override fun onHook() {
        registerAppLifecycle(true) {
            onCreate {
                val context = hostApplication ?: return@onCreate
                init(context)
            }
        }
    }

    /**
     * 初始化歌词客户端、缓存、provider 与会话监听。
     * @param context 宿主应用上下文。
     */
    private fun init(context: Context) {
        kit = QishuiLyricApi()
        cache = LyricCache(context.applicationContext)
        initProvider(context)
        initSessionListener(context)
    }

    /**
     * 注册 Lyricon provider。
     * @param context 宿主应用上下文。
     */
    private fun initProvider(context: Context) {
        provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = TARGET_PACKAGE,
            logo = ProviderLogo.fromSvg(Constants.ICON),
            processName = processName
        ).apply {
            player.setDisplayTranslation(true)
            register()
        }
        YLog.info(tag = TAG, msg = "provider registered, process=$processName")
    }

    /**
     * 注册媒体会话监听。
     * @param context 宿主应用上下文。
     */
    private fun initSessionListener(context: Context) {
        val manager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (manager == null) {
            YLog.error(tag = TAG, msg = "MediaSessionManager unavailable")
            return
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
            syncControllers(list.orEmpty())
        }

        runCatching {
            manager.addOnActiveSessionsChangedListener(listener, null)
            syncControllers(manager.getActiveSessions(null).orEmpty())
        }.onFailure {
            YLog.error(tag = TAG, msg = "session listener failed: $it")
        }
    }

    /**
     * 同步活跃会话，注册或释放控制器回调。
     * @param list 当前活跃媒体控制器。
     */
    private fun syncControllers(list: List<MediaController>) {
        val targets = list.filter { it.packageName == TARGET_PACKAGE }
        val tokens = targets.map { it.sessionToken }.toSet()

        controllers.keys.filter { it !in tokens }.forEach { token ->
            controllers.remove(token)
        }

        targets.forEach { controller ->
            if (controllers.containsKey(controller.sessionToken)) return@forEach
            controllers[controller.sessionToken] = controller

            controller.registerCallback(object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    metadata?.let { handleMetadata(it) }
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    state?.let {

                        fun deliverPlaybackState(state: PlaybackState) {
                            provider?.player?.setPlaybackState(state)
                        }

                        if (state.state == PlaybackState.STATE_PLAYING) {
                            pendingPauseJob?.cancel()
                            pendingPauseJob = null

                            deliverPlaybackState(state)
                        } else {
                            pendingPauseJob?.cancel()
                            pendingPauseJob = scope.launch {
                                delay(100.milliseconds)

                                withContext(Dispatchers.Main) {
                                    deliverPlaybackState(state)
                                }
                            }
                        }
                    }
                }

                override fun onSessionDestroyed() {
                    controllers.remove(controller.sessionToken)
                }
            }, handler)

            controller.metadata?.let { handleMetadata(it) }
            controller.playbackState?.let { provider?.player?.setPlaybackState(it) }
        }
    }

    /**
     * 处理曲目元数据变化，解析歌词并下发。
     * @param metadata 最新媒体元数据。
     */
    private fun handleMetadata(metadata: MediaMetadata) {
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        songCache[id]?.let { cached ->
            provider?.player?.setSong(cached)
            return
        }

        YLog.info(tag = TAG, msg = "metadata id=$id title=$title artist=$artist")

        scope.launch {
            val lyric = cache?.load(id) ?: kit?.fetch(id)?.also { cache?.save(it) }
            val song = lyric?.toSong()
            withContext(Dispatchers.Main) {
                if (song != null) {
                    songCache[id] = song
                    provider?.player?.setSong(song)
                } else {
                    provider?.player?.setSong(
                        Song(
                            id = id,
                            name = title.orEmpty(),
                            artist = artist.orEmpty(),
                            duration = duration
                        )
                    )
                }
            }
        }
    }
}
