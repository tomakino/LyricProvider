/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.app.Application
import android.app.Instrumentation
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.proify.extensions.json
import io.github.proify.lyricon.cmprovider.xposed.Constants.ICON
import io.github.proify.lyricon.cmprovider.xposed.Constants.PROVIDER_PACKAGE_NAME
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.yrckit.download.response.LyricResponse
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.reflect.Executable
import java.util.concurrent.atomic.AtomicBoolean

/** Installs and owns the modern libxposed hook chain for one supported Cloud Music process. */
internal class CloudMusic(
    private val module: XposedModule,
    private val logger: ModuleLogger,
    private val packageName: String,
    private val processName: String,
    private val readyClassLoader: ClassLoader
) : DownloadCallback {
    private val stateLock = Any()
    private val initializationStarted = AtomicBoolean(false)
    private val lyricCacheStore = LyricCacheStore(json)

    private var application: Application? = null
    private var lyricProvider: LyriconProvider? = null
    private var preferencesMonitor: PreferencesMonitor? = null

    private var latestMetadata: Metadata? = null
    private var latestPlaybackState: PlaybackState? = null
    private var hasPlaybackState = false
    private var currentMusicId: Long? = null
    private var lastSetSong: Song? = null
    private var translationType = DISABLED_TRANSLATION_TYPE

    fun installHooks() {
        logger.info(
            "Installing hooks: package=$packageName, process=$processName, " +
                    "classLoader=$readyClassLoader"
        )
        hookApplicationLifecycle()
        hookMediaSession()
    }

    private fun hookApplicationLifecycle() {
        val method = Instrumentation::class.java.getDeclaredMethod(
            "callApplicationOnCreate",
            Application::class.java
        )
        installProtectiveAfterHook(method, "Instrumentation.callApplicationOnCreate") { chain ->
            val hostApplication = chain.args.getOrNull(0) as? Application
            if (hostApplication == null) {
                logger.warn("callApplicationOnCreate did not contain an Application")
            } else {
                onApplicationCreated(hostApplication)
            }
        }
    }

    private fun hookMediaSession() {
        val metadataMethod = MediaSession::class.java.getDeclaredMethod(
            "setMetadata",
            MediaMetadata::class.java
        )
        installProtectiveAfterHook(metadataMethod, "MediaSession.setMetadata") { chain ->
            val metadata = chain.args.getOrNull(0) as? MediaMetadata
            if (metadata != null) onMetadataChanged(metadata)
        }

        val playbackMethod = MediaSession::class.java.getDeclaredMethod(
            "setPlaybackState",
            PlaybackState::class.java
        )
        installProtectiveAfterHook(playbackMethod, "MediaSession.setPlaybackState") { chain ->
            onPlaybackStateChanged(chain.args.getOrNull(0) as? PlaybackState)
        }
    }

    /**
     * Runs host-side work only after the original method and always returns its original result.
     * PROTECTIVE mode keeps module failures from terminating the player process.
     */
    private fun installProtectiveAfterHook(
        executable: Executable,
        description: String,
        callback: (XposedInterface.Chain) -> Unit
    ) {
        try {
            module.hook(executable)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    try {
                        callback(chain)
                    } catch (throwable: Throwable) {
                        logger.error("After-hook failed: $description", throwable)
                    }
                    result
                }
            logger.debug("Installed protective hook: $description")
        } catch (throwable: Throwable) {
            logger.error("Unable to install hook: $description", throwable)
        }
    }

    private fun onApplicationCreated(hostApplication: Application) {
        if (hostApplication.packageName != packageName) {
            logger.warn(
                "Ignoring unexpected Application ${hostApplication.packageName} in $processName"
            )
            return
        }
        if (!initializationStarted.compareAndSet(false, true)) {
            logger.debug("Application initialization already completed in $processName")
            return
        }

        synchronized(stateLock) {
            application = hostApplication
        }
        logger.info(
            "Application ready: package=$packageName, process=$processName, " +
                    "classLoader=${hostApplication.classLoader}"
        )

        try {
            setupProvider(hostApplication)
        } catch (throwable: Throwable) {
            logger.error("Provider initialization failed in $processName", throwable)
            return
        }

        startPreferencesMonitorInitialization(hostApplication)
    }

    private fun startPreferencesMonitorInitialization(hostApplication: Application) {
        try {
            Thread(
                {
                    val monitor = createPreferencesMonitor(hostApplication)
                    val previousMonitor = synchronized(stateLock) {
                        val previous = preferencesMonitor
                        preferencesMonitor = monitor
                        previous
                    }
                    if (previousMonitor !== monitor) {
                        previousMonitor?.close()
                    }
                },
                "Lyricon-DexKit-$processName"
            ).apply {
                isDaemon = true
                start()
            }
        } catch (throwable: Throwable) {
            logger.error("Unable to start preference capability initialization", throwable)
        }
    }

    private fun createPreferencesMonitor(hostApplication: Application): PreferencesMonitor? {
        return try {
            ensureDexKitLoaded()
            val monitor =
                DexKitBridge.create(hostApplication.applicationInfo.sourceDir).use { bridge ->
                    PreferencesMonitor(
                        kitBridge = bridge,
                        callback = { type ->
                            dispatchTranslationOptionChange(hostApplication, type)
                        },
                        logger = logger,
                        hostPackageName = packageName
                    )
                }
            // MethodData survives the query bridge; resolve it with the actual Application loader.
            monitor.update(hostApplication.classLoader)
            monitor
        } catch (throwable: Throwable) {
            logger.error(
                "Preference capability disabled because DexKit initialization failed",
                throwable
            )
            dispatchTranslationOptionChange(hostApplication, DISABLED_TRANSLATION_TYPE)
            null
        }
    }

    private fun dispatchTranslationOptionChange(
        hostApplication: Application,
        type: Int
    ) {
        try {
            hostApplication.mainExecutor.execute {
                onTranslationOptionChanged(type)
            }
        } catch (throwable: Throwable) {
            logger.error("Unable to dispatch the translation preference", throwable)
        }
    }

    private fun setupProvider(hostApplication: Application) {
        val initialTranslationType = synchronized(stateLock) { translationType }
        val provider = LyriconFactory.createProvider(
            context = hostApplication,
            providerPackageName = PROVIDER_PACKAGE_NAME,
            playerPackageName = packageName,
            logo = ProviderLogo.fromSvg(ICON)
        ).apply {
            player.setDisplayTranslation(initialTranslationType == TRANSLATION_TYPE)
            player.setDisplayRoma(initialTranslationType == ROMANIZATION_TYPE)
            register()
        }

        val replay = synchronized(stateLock) {
            lyricProvider = provider
            val metadataToReplay = latestMetadata?.takeIf { currentMusicId != it.id }
            if (metadataToReplay != null) {
                currentMusicId = metadataToReplay.id
                lastSetSong = null
            }
            ReplayState(
                metadata = metadataToReplay,
                playbackState = latestPlaybackState,
                hasPlaybackState = hasPlaybackState
            )
        }

        logger.info("Provider registered in $processName")
        replay.metadata?.let(::onSongChanged)
        if (replay.hasPlaybackState) {
            provider.player.setPlaybackState(replay.playbackState)
        }
    }

    private fun onMetadataChanged(mediaMetadata: MediaMetadata) {
        val metadata = MediaMetadataCache.save(mediaMetadata) ?: run {
            logger.debug("Ignoring metadata without a supported media id")
            return
        }

        val shouldDispatch = synchronized(stateLock) {
            latestMetadata = metadata
            if (lyricProvider == null || currentMusicId == metadata.id) {
                false
            } else {
                currentMusicId = metadata.id
                lastSetSong = null
                true
            }
        }
        if (shouldDispatch) onSongChanged(metadata)
    }

    private fun onPlaybackStateChanged(state: PlaybackState?) {
        val provider = synchronized(stateLock) {
            latestPlaybackState = state
            hasPlaybackState = true
            lyricProvider
        }
        provider?.player?.setPlaybackState(state)
    }

    private fun onTranslationOptionChanged(type: Int) {
        val normalizedType = type.takeIf {
            it == TRANSLATION_TYPE || it == ROMANIZATION_TYPE
        } ?: DISABLED_TRANSLATION_TYPE
        val provider = synchronized(stateLock) {
            if (translationType == normalizedType) return
            translationType = normalizedType
            lyricProvider
        }

        logger.debug("Translation option changed: type=$normalizedType")
        provider?.player?.setDisplayTranslation(normalizedType == TRANSLATION_TYPE)
        provider?.player?.setDisplayRoma(normalizedType == ROMANIZATION_TYPE)
    }

    override fun onDownloadFinished(id: Long, response: LyricResponse) {
        logger.debug("Download finished: id=$id")
        val cache = response.toValidCacheOrNull(id)
        if (cache == null) {
            logger.warn("Ignoring invalid lyric response: id=$id, code=${response.code}")
            return
        }
        try {
            writeToLocalLyricCache(id, cache)
        } catch (throwable: Throwable) {
            logger.error("Writing downloaded lyrics failed: id=$id", throwable)
        }
    }

    override fun onDownloadFailed(id: Long, e: Exception) {
        logger.error("Download failed: id=$id", e)
    }

    private fun getDownloadLyricFile(id: Long): File? {
        val hostApplication = synchronized(stateLock) { application } ?: return null
        val directory = Constants.getDownloadLyricDirectory(hostApplication)
            ?: File(hostApplication.filesDir, "LyriconDownload")
        if (!directory.exists() && !directory.mkdirs()) {
            logger.warn("Unable to create lyric cache directory: $directory")
            return null
        }
        return File(directory, id.toString())
    }

    private fun writeToLocalLyricCache(id: Long, cache: LocalLyricCache) {
        val outputFile = getDownloadLyricFile(id) ?: return
        if (!lyricCacheStore.write(outputFile, cache)) {
            logger.warn("Unable to atomically replace lyric cache: id=$id")
            return
        }

        val currentId = synchronized(stateLock) { currentMusicId }
        if (CurrentTrackPolicy.shouldPublish(id, currentId)) {
            loadLyricFromFile(cacheSource = "network", id = id, cacheFile = outputFile)
        } else {
            logger.debug("Cached stale download without replaying it: id=$id")
        }
    }

    private fun loadLyricFromFile(cacheSource: String, id: Long, cacheFile: File): Boolean {
        if (!isCurrentMusic(id)) return false
        logger.debug("Loading lyric file: source=$cacheSource, file=$cacheFile")

        val metadata = MediaMetadataCache.get(id) ?: synchronized(stateLock) {
            latestMetadata?.takeIf { it.id == id }
        } ?: return false
        val cache = lyricCacheStore.read(cacheFile, id) ?: return false
        val parsedSong = try {
            cache.toSong(metadata)
        } catch (exception: Exception) {
            cacheFile.delete()
            logger.error("Synchronous lyric parsing failed: id=$id", exception)
            return false
        }
        if (!cache.pureMusic && parsedSong.lyrics.isNullOrEmpty()) {
            cacheFile.delete()
            logger.warn("Deleting lyric cache with no parseable source lyrics: id=$id")
            return false
        }
        setSong(id, parsedSong)
        return true
    }

    private fun onSongChanged(metadata: Metadata) {
        if (!isCurrentMusic(metadata.id)) return
        val localCacheFile = getDownloadLyricFile(metadata.id)
        if (localCacheFile?.exists() == true && loadLyricFromFile(
                cacheSource = "localCache",
                id = metadata.id,
                cacheFile = localCacheFile
            )
        ) {
            return
        }

        // Always publish basic metadata before the asynchronous cache recovery/download.
        setSong(
            metadata.id,
            Song(
                id = metadata.id.toString(),
                name = metadata.title,
                artist = metadata.artist,
                duration = resolveSongDuration(metadata.duration, null)
            )
        )
        Downloader.download(metadata.id, this)
    }

    private fun setSong(id: Long, song: Song) {
        synchronized(stateLock) {
            if (currentMusicId != id || lastSetSong == song) return
            lastSetSong = song
            // Keep the current-id check and provider update in one critical section so a
            // concurrently arriving track cannot be overwritten by this result.
            lyricProvider?.player?.setSong(song)
        }
    }

    private fun isCurrentMusic(id: Long): Boolean =
        synchronized(stateLock) { currentMusicId == id }

    private data class ReplayState(
        val metadata: Metadata?,
        val playbackState: PlaybackState?,
        val hasPlaybackState: Boolean
    )

    companion object {
        const val TAG: String = "CloudMusic"
        private const val DISABLED_TRANSLATION_TYPE = -1
        private const val TRANSLATION_TYPE = 0
        private const val ROMANIZATION_TYPE = 1

        val dexKitLoadLock = Any()

        @Volatile
        private var dexKitLoaded = false

        private fun ensureDexKitLoaded() {
            if (dexKitLoaded) return
            synchronized(dexKitLoadLock) {
                if (dexKitLoaded) return
                System.loadLibrary("dexkit")
                dexKitLoaded = true
            }
        }
    }
}
