/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed.kugou

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import io.github.proify.lyricon.kgprovider.xposed.Constants
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

@Suppress("TooManyFunctions", "LongMethod", "ComplexMethod")
abstract class KuGouBase : YukiBaseHooker() {

    protected val tag = "KuGouProvider"

    protected var provider: LyriconProvider? = null
    protected var currentSongId: String? = null
    protected var currentSongTitle: String = ""
    protected var currentSongArtist: String = ""
    protected var currentLyricsHash: Int = 0
    protected var lastLyricDataHash: Int = 0
    protected var pendingLyrics: List<RichLyricLine>? = null
    protected var isInitialized = false

    override fun onHook() {
        if (shouldHookProcess()) {
            hookLyricDataSetters()
            hookMediaSession()
            onAppLifecycle {
                onCreate {
                    initProvider()
                }
            }
        }
    }
    
    protected abstract fun shouldHookProcess(): Boolean

    private fun initProvider() {
        if (isInitialized) return
        val ctx = appContext ?: return
        isInitialized = true

        try {
            provider = LyriconFactory.createProvider(
                context = ctx,
                providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
                playerPackageName = ctx.packageName,
                logo = ProviderLogo.fromBase64(Constants.ICON)
            ).apply {
                register()
                player.setDisplayTranslation(true)
            }
        } catch (e: Exception) {
            Log.e(tag, "initProvider failed: ${e.message}")
        }
    }

    protected fun hookLyricDataSetters() {
        runCatching {
            val lyricDataClass = "com.kugou.framework.lyric.LyricData".toClass(appClassLoader) ?: return@runCatching

            val config = KuGouVersionConfig.DEFAULT_METHOD_CONFIG
            var hookedBeginTimes = false
            var hookedEndTimes = false
            var hookedOriginalTexts = false
            var hookedTranslation = false
            var stringArrayMethodCount = 0

            lyricDataClass.declaredMethods.forEach { method ->
                if (method.parameterCount != 1) return@forEach
                val paramType = method.parameterTypes[0]
                val paramTypeName = paramType.name

                when {
                    method.name in config.beginTimesMethods && paramType == LongArray::class.java -> {
                        hookBeginTimesMethod(method)
                        hookedBeginTimes = true
                    }
                    method.name in config.endTimesMethods && paramType == LongArray::class.java -> {
                        hookEndTimesMethod(method)
                        hookedEndTimes = true
                    }
                    method.name in config.originalTextsMethods -> {
                        hookOriginalTextsMethod(method)
                        hookedOriginalTexts = true
                    }
                    method.name in config.translationMethods && paramTypeName.contains("String") -> {
                        hookTranslationMethod(method)
                        hookedTranslation = true
                    }
                    paramType == LongArray::class.java && !hookedBeginTimes -> {
                        hookBeginTimesMethod(method)
                        hookedBeginTimes = true
                    }
                    paramType == LongArray::class.java && !hookedEndTimes -> {
                        hookEndTimesMethod(method)
                        hookedEndTimes = true
                    }
                    paramTypeName == "[[Ljava.lang.String;" -> {
                        stringArrayMethodCount++
                        if (!hookedOriginalTexts) {
                            hookOriginalTextsMethod(method)
                            hookedOriginalTexts = true
                        } else if (!hookedTranslation) {
                            hookTranslationMethod(method)
                            hookedTranslation = true
                        } else {
                            hookAllStringArrayMethod(method)
                        }
                    }
                }
            }
        }.onFailure { e ->
            Log.e(tag, "hookLyricDataSetters failed: ${e.message}")
        }
    }
    
    private fun hookBeginTimesMethod(method: java.lang.reflect.Method) {
        method.hook {
            after {
                tryBuildLyricsFromFields(this.instance)
            }
        }
    }
    
    private fun hookEndTimesMethod(method: java.lang.reflect.Method) {
        method.hook {
            after {
                tryBuildLyricsFromFields(this.instance)
            }
        }
    }
    
    private fun hookOriginalTextsMethod(method: java.lang.reflect.Method) {
        method.hook {
            after {
                tryBuildLyricsFromFields(this.instance)
            }
        }
    }
    
    private fun hookTranslationMethod(method: java.lang.reflect.Method) {
        method.hook {
            after {
                tryBuildLyricsFromFields(this.instance, forceRebuild = true)
            }
        }
    }
    
    private fun hookAllStringArrayMethod(method: java.lang.reflect.Method) {
        method.hook {
            after {
                tryBuildLyricsFromFields(this.instance, forceRebuild = true)
            }
        }
    }

    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod")
    protected fun tryBuildLyricsFromFields(lyricData: Any, forceRebuild: Boolean = false) {
        runCatching {
            val hash = System.identityHashCode(lyricData)
            if (hash == lastLyricDataHash && !forceRebuild) {
                return
            }

            var beginTimes: LongArray? = null
            var endTimes: LongArray? = null
            @Suppress("UNCHECKED_CAST")
            var originalTexts: Array<Array<String>>? = null
            @Suppress("UNCHECKED_CAST")
            var translationTexts: Array<Array<String>>? = null

            for (config in KuGouVersionConfig.FIELD_CONFIGS) {
                beginTimes = null
                endTimes = null
                originalTexts = null
                translationTexts = null
                
                lyricData.javaClass.declaredFields.forEach { field ->
                    field.isAccessible = true
                    val value = field.get(lyricData)
                    val fieldName = field.name
                    
                    @Suppress("UNCHECKED_CAST")
                    when (fieldName) {
                        in config.beginTimesFields -> beginTimes = value as? LongArray
                        in config.endTimesFields -> endTimes = value as? LongArray
                        in config.originalTextsFields -> originalTexts = value as? Array<Array<String>>
                        in config.translationFields -> translationTexts = value as? Array<Array<String>>
                    }
                }
                
                if (beginTimes != null && originalTexts != null && beginTimes.isNotEmpty()) {
                    break
                }
            }

            if (beginTimes != null && originalTexts != null && beginTimes.isNotEmpty()) {
                lastLyricDataHash = hash

                val lines = mutableListOf<RichLyricLine>()
                val size = minOf(beginTimes.size, originalTexts.size)

                for (i in 0 until size) {
                    val begin = beginTimes[i]
                    var end = if (endTimes != null && i < endTimes.size) endTimes[i] else 0L

                    if (end <= begin) {
                        end = if (i + 1 < beginTimes.size) beginTimes[i + 1] else begin + 5000L
                    }

                    val textArray = originalTexts[i]
                    val text = textArray?.joinToString("") ?: ""

                    if (text.isNotBlank()) {
                        var translation: String? = null
                        if (translationTexts != null && i < translationTexts.size) {
                            val transArray = translationTexts[i]
                            if (transArray != null && transArray.isNotEmpty()) {
                                translation = transArray.joinToString("")
                            }
                        }

                        lines.add(RichLyricLine(
                            begin = begin,
                            end = end,
                            duration = if (end > begin) end - begin else 0L,
                            text = text,
                            translation = translation
                        ))
                    }
                }

                if (lines.isNotEmpty()) {
                    val sortedLines = lines.sortedBy { it.begin }
                    sendLyrics(sortedLines)
                }
            }
        }.onFailure { e ->
            Log.e(tag, "tryBuildLyricsFromFields failed: ${e.message}")
        }
    }

    protected fun hookMediaSession() {
        runCatching {
            val mediaSessionClass = "android.media.session.MediaSession".toClass(appClassLoader) ?: return@runCatching

            mediaSessionClass.declaredMethods.forEach { method ->
                if (method.name == "setMetadata" && method.parameterCount == 1) {
                    method.hook {
                        after {
                            val metadata = args[0] as? MediaMetadata ?: return@after
                            handleMetadataChange(metadata)
                        }
                    }
                }

                if (method.name == "setPlaybackState" && method.parameterCount == 1) {
                    method.hook {
                        after {
                            val state = args[0] as? PlaybackState
                            provider?.player?.setPlaybackState(state)
                        }
                    }
                }
            }
        }.onFailure { e ->
            Log.e(tag, "hookMediaSession failed: ${e.message}")
        }
    }

    protected open fun handleMetadataChange(metadata: MediaMetadata) {
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        
        val songId = "$rawTitle|$artist|$album|$duration"
        
        if (songId == currentSongId) return
        
        currentSongId = songId
        currentSongTitle = rawTitle
        currentSongArtist = artist

        Log.i(tag, "New song: $rawTitle - $artist (${duration}ms)")
        
        currentLyricsHash = 0
        lastLyricDataHash = 0
        
        val cached = LyricsCache.get(songId)
        if (cached != null) {
            currentLyricsHash = cached.hashCode()
            lastLyricDataHash = 0
            
            provider?.player?.setSong(Song(
                id = songId,
                name = currentSongTitle,
                artist = currentSongArtist,
                duration = 0,
                lyrics = cached
            ))
            return
        }
        
        pendingLyrics?.let { lyrics ->
            sendLyrics(lyrics)
            pendingLyrics = null
        }
    }

    protected open fun sendLyrics(lyrics: List<RichLyricLine>) {
        val hash = lyrics.hashCode()
        if (hash == currentLyricsHash) return

        if (currentSongId.isNullOrEmpty()) {
            pendingLyrics = lyrics
            return
        }

        currentLyricsHash = hash
        
        LyricsCache.put(currentSongId!!, lyrics)

        val song = Song(
            id = currentSongId!!,
            name = currentSongTitle,
            artist = currentSongArtist,
            duration = 0,
            lyrics = lyrics
        )

        provider?.player?.setSong(song)
        
        Log.i(tag, "=== Lyrics (${lyrics.size} lines) ===")
        lyrics.forEach { line ->
            if (line.translation != null) {
                Log.i(tag, "${line.text} | ${line.translation}")
            } else {
                Log.i(tag, line.text ?: "")
            }
        }
        Log.i(tag, "=== End Lyrics ===")
    }
}
