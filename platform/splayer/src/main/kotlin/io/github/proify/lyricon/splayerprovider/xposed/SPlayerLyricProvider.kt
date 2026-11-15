/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.splayerprovider.xposed

import android.annotation.SuppressLint
import android.media.session.PlaybackState
import android.webkit.WebView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object SPlayerLyricProvider : YukiBaseHooker() {
    private const val TAG = "SPlayerProvider"
    private const val INJECTED_JS =
        "(function(){if(window.__slpLyrics)return;window.__slpLyrics=true;var i=setInterval(function(){try{var a=document.querySelector('#app');if(!a||!a.__vue_app__)return;var p=a.__vue_app__.config.globalProperties['\$pinia'];if(!p)return;var m=p._s.get('music');if(!m)return;var l=JSON.stringify(m.songLyric.lrcData??[]);var y=JSON.stringify(m.songLyric.yrcData??[]);var c=l+y;if(c!==window.__slpLast&&c!=='[][]'){window.__slpLast=c;if(typeof Capacitor!=='undefined'&&Capacitor.Plugins&&Capacitor.Plugins.AndroidNativePlayback){Capacitor.Plugins.AndroidNativePlayback.updateFloatingLyricData({lrcData:l,yrcData:y});}}}catch(e){}},2000);})();"
    private var provider: LyriconProvider? = null
    private var initAttempted = false
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private var currentSongName = ""
    private var currentArtist = ""
    private var titleField: java.lang.reflect.Field? = null
    private var artistField: java.lang.reflect.Field? = null

    override fun onHook() {
        YLog.debug(tag = TAG, msg = "正在注入进程: $processName")

        injectLyricsJs()

        "top.imsyy.splayer.android.playback.PlaybackManager".toClass().resolve().apply {
            firstMethod {
                name = "updateFloatingLyricSongInfo"
                parameters(String::class.java, String::class.java)
            }.hook {
                after {
                    currentSongName = args[0] as? String ?: ""
                    currentArtist = args[1] as? String ?: ""
                    YLog.debug(
                        tag = TAG,
                        msg = "updateFloatingLyricSongInfo: $currentSongName - $currentArtist"
                    )
                }
            }

            firstMethod {
                name = "updateFloatingLyricData"
                parameters(String::class.java, String::class.java)
            }.hook {
                after {
                    ensureInitialized()
                    val lrcJson = args[0] as? String ?: ""
                    val yrcJson = args[1] as? String ?: ""
                    val song = parseToSong(lrcJson, yrcJson)
                    YLog.debug(
                        tag = TAG,
                        msg = "updateFloatingLyricData: lrcLen=${lrcJson.length}, yrcLen=${yrcJson.length}, parsed=${song != null}"
                    )
                    if (song != null) {
                        provider?.player?.setSong(song)
                    }
                }
            }

            firstMethod {
                name = "updateFloatingLyricProgress"
                parameters(
                    Long::class.javaPrimitiveType ?: Long::class.java,
                    Boolean::class.javaPrimitiveType ?: Boolean::class.java
                )
            }.hook {
                after {
                    ensureInitialized()
                    val timeMs = args[0] as? Long ?: 0L
                    val playing = args[1] as? Boolean ?: false
                    YLog.debug(
                        tag = TAG,
                        msg = "updateFloatingLyricProgress: timeMs=$timeMs, playing=$playing"
                    )
                    provider?.player?.setPosition(timeMs)
                    provider?.player?.setPlaybackState(playing)
                }
            }

            firstMethod {
                name = "updateMetadata"
                parameters($$"top.imsyy.splayer.android.playback.PlaybackManager$TrackMetadata".toClass())
            }.hook {
                after {
                    ensureInitialized()
                    val metadata = args[0] ?: return@after
                    try {
                        if (titleField == null) {
                            titleField = metadata::class.java.getDeclaredField("title")
                                .apply { isAccessible = true }
                            artistField = metadata::class.java.getDeclaredField("artist")
                                .apply { isAccessible = true }
                        }
                        val title = titleField!!.get(metadata) as? String ?: return@after
                        val artist = artistField!!.get(metadata) as? String ?: ""
                        currentSongName = title
                        currentArtist = artist
                        YLog.debug(tag = TAG, msg = "updateMetadata: $title - $artist")
                    } catch (t: Throwable) {
                        YLog.error(
                            tag = TAG,
                            msg = "updateMetadata: failed to read TrackMetadata fields",
                            e = t
                        )
                    }
                }
            }
        }

        "android.media.session.MediaSession".toClass().resolve().apply {
            firstMethod {
                name = "setPlaybackState"
                parameters(PlaybackState::class.java)
            }.hook {
                after {
                    ensureInitialized()
                    val state = args[0] as? PlaybackState
                    if (state != null) {
                        YLog.debug(
                            tag = TAG,
                            msg = "MediaSession.setPlaybackState: state=${state.state}, position=${state.position}"
                        )
                    } else {
                        YLog.debug(tag = TAG, msg = "MediaSession.setPlaybackState: state=null")
                    }
                    if (provider == null) {
                        YLog.error(
                            tag = TAG,
                            msg = "MediaSession.setPlaybackState: provider is null!"
                        )
                    } else {
                        provider?.player?.setPlaybackState(state)
                    }
                }
            }
        }
    }

    private fun injectLyricsJs() {
        "android.webkit.WebViewClient".toClass().resolve().apply {
            firstMethod {
                name = "onPageFinished"
                parameters(WebView::class.java, String::class.java)
            }.hook {
                after {
                    val webView = args[0] as? WebView ?: return@after
                    webView.evaluateJavascript(INJECTED_JS, null)
                }
            }
        }
    }

    @SuppressLint("PrivateApi")
    @Synchronized
    private fun ensureInitialized() {
        if (provider != null || initAttempted) return
        initAttempted = true
        try {
            val context = try {
                val atClass = Class.forName("android.app.ActivityThread")
                atClass.getMethod("currentApplication").invoke(null) as? android.content.Context
            } catch (e: Exception) {
                YLog.error(tag = TAG, msg = "ensureInitialized: ActivityThread error: ${e.message}")
                null
            }
            if (context == null) {
                initAttempted = false
                return
            }
            val created = LyriconFactory.createProvider(
                context = context,
                providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
                playerPackageName = Constants.MUSIC_PACKAGE_NAME,
                logo = ProviderLogo.fromSvg(Constants.ICON),
                processName = processName
            )
            created.player.setDisplayTranslation(true)
            created.player.setDisplayRoma(true)
            created.register()
            provider = created
            Runtime.getRuntime().addShutdownHook(Thread {
                provider?.destroy()
                YLog.debug(tag = TAG, msg = "provider destroyed on shutdown")
            })
            YLog.debug(tag = TAG, msg = "provider registered")
        } catch (e: Exception) {
            initAttempted = false
            YLog.error(tag = TAG, msg = "ensureInitialized failed: ${e.message}")
        }
    }

    private fun parseToSong(lrcJson: String, yrcJson: String): Song? {
        try {
            val effectiveJson = when {
                yrcJson.isNotBlank() && yrcJson != "[]" -> yrcJson
                lrcJson.isNotBlank() && lrcJson != "[]" -> lrcJson
                else -> return null
            }

            val lines = mutableListOf<RichLyricLine>()
            val jsonArray: JsonArray = json.parseToJsonElement(effectiveJson).jsonArray

            for (element in jsonArray) {
                val obj = element.jsonObject
                val begin = obj["startTime"]?.jsonPrimitive?.longOrNull ?: 0L
                val end = obj["endTime"]?.jsonPrimitive?.longOrNull ?: 0L
                val translation = obj["translatedLyric"]?.jsonPrimitive?.contentOrNull ?: ""
                val wordsArr = obj["words"]?.jsonArray

                val words = mutableListOf<LyricWord>()

                if (wordsArr != null) {
                    for (w in wordsArr) {
                        val wObj = w.jsonObject
                        val text = wObj["word"]?.jsonPrimitive?.contentOrNull ?: ""
                        val wBegin = wObj["startTime"]?.jsonPrimitive?.longOrNull ?: 0L
                        val wEnd = wObj["endTime"]?.jsonPrimitive?.longOrNull ?: 0L
                        if (text.isNotEmpty()) {
                            words.add(LyricWord(text = text, begin = wBegin, end = wEnd))
                        }
                    }
                }

                val fullText = words.joinToString("") { it.text.toString() }
                if (fullText.isEmpty()) continue

                lines.add(
                    RichLyricLine(
                        begin = begin,
                        end = end,
                        text = fullText,
                        translation = translation.ifEmpty { null },
                        words = words.ifEmpty { null }
                    )
                )
            }

            if (lines.isEmpty()) return null

            return Song(
                id = "${currentSongName}-${currentArtist}",
                name = currentSongName.ifEmpty { "Unknown" },
                artist = currentArtist.ifEmpty { "Unknown" },
                duration = lines.lastOrNull()?.end ?: 0L,
                lyrics = lines
            )
        } catch (e: Exception) {
            YLog.error(tag = TAG, msg = "Failed to parse lyric JSON", e = e)
            return null
        }
    }
}
