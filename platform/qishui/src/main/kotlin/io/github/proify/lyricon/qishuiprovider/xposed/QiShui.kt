package io.github.proify.lyricon.qishuiprovider.xposed

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.json
import io.github.proify.extensions.md5
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.qishuiprovider.xposed.parser.NetResponseCache
import io.github.proify.lyricon.qishuiprovider.xposed.parser.toRichLyric
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.io.File

object QiShui : YukiBaseHooker() {

    private const val TAG = "QiShui"
    private var provider: LyriconProvider? = null

    private var curMediaId: String? = null
    private var lastSong: Song? = null

    override fun onHook() {
        YLog.info(tag = TAG, msg = "$packageName/$processName")

        onAppLifecycle {
            onCreate {
                hook()
            }
        }
    }

    private var hooked = false
    private fun hook() {
        if (hooked) {
            YLog.info(tag = TAG, msg = "何意味")
            return
        }
        hooked = true

        initProvider()
        hookMediaSession()
    }

    private fun initProvider() {
        val context = appContext ?: return
        provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = context.packageName,
            logo = ProviderLogo.fromSvg(Constants.ICON),
            processName = processName
        ).apply {
            player.setDisplayTranslation(true)
            register()
        }
        YLog.debug(tag = TAG, msg = "provider registered, provider=${provider?.providerInfo}")
    }

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass()
            .resolve()
            .apply {
                firstMethod {
                    name = "setPlaybackState"
                    parameters(PlaybackState::class.java)
                }.hook {
                    after {
                        val state = args[0] as? PlaybackState
                        provider?.player?.setPlaybackState(state)
                        updateSongIfNeed()
                    }
                }

                firstMethod {
                    name = "setMetadata"
                    parameters("android.media.MediaMetadata")
                }.hook {
                    after {
                        val mediaMetadata = args[0] as? MediaMetadata ?: return@after
                        val id = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)

                        if (curMediaId == id) return@after

                        curMediaId = id
                        MetadataCache.save(mediaMetadata)
                        updateSong()
                    }
                }
            }
    }

    private fun updateSongIfNeed() {
        if (curMediaId.isNullOrBlank()) return
        val lastSong = this.lastSong
        if (lastSong?.lyrics.isNullOrEmpty()) updateSong()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun updateSong() {
        val id = curMediaId ?: return

        val cache = runCatching {
            val file = getNetLyricCacheFile(id)
            if (file != null && file.exists()) {
                file.inputStream().use {
                    json.decodeFromStream<NetResponseCache>(it)
                }
            } else null
        }.onFailure {
            YLog.error(tag = TAG, msg = "cache load failed, mediaId=$id, error=$it")
        }.getOrNull()

        if (cache == null) {
            val metadata = MetadataCache.get(id)
            setSong(Song(name = metadata?.title, artist = metadata?.artist))
            return
        }

        val song = cache.buildSong(id)
        setSong(song)
    }

    private fun setSong(song: Song) {
        if (song == lastSong) return
        provider?.player?.setSong(song)
        lastSong = song
    }

    fun NetResponseCache.buildSong(id: String): Song {
        val metadata = MetadataCache.get(id)
        return Song(
            id = id,
            name = metadata?.title.orEmpty(),
            artist = metadata?.artist.orEmpty(),
            duration = metadata?.duration ?: 0L,
            lyrics = toRichLyric()
        )
    }

    private val netCacheLoaderDir by lazy { appContext!!.cacheDir.resolve("NetCacheLoader") }

    fun getNetLyricCacheFile(id: String): File? {
        val fileName = calculateLyricCacheFileName(id)

        return runCatching {
            var targetFile: File? = null
            netCacheLoaderDir.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name == fileName) {
                        targetFile = file
                        return@forEach
                    }
                }
                if (targetFile != null) return@forEach
            }
            targetFile
        }.onFailure {
            YLog.error(tag = TAG, msg = "getNetLyricCacheFile failed, mediaId=$id, error=$it")
        }.getOrNull()
    }

    fun calculateLyricCacheFileName(id: String): String =
        "/luna/track_v2/$id".md5()
}