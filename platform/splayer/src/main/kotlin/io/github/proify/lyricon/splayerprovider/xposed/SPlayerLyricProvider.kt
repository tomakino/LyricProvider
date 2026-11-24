/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("DestructuringDeclaration")

package io.github.proify.lyricon.splayerprovider.xposed

import android.media.session.PlaybackState
import android.webkit.WebView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.makeAccessible
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

object SPlayerLyricProvider : YukiBaseHooker() {
    private const val TAG = "SPlayerProvider"
    private const val INJECTED_JS =
        $$"(function(){if(window.__slpLyrics)return;window.__slpLyrics=true;var i=setInterval(function(){try{var a=document.querySelector('#app');if(!a||!a.__vue_app__)return;var p=a.__vue_app__.config.globalProperties['$pinia'];if(!p)return;var m=p._s.get('music');if(!m)return;var l=JSON.stringify(m.songLyric.lrcData??[]);var y=JSON.stringify(m.songLyric.yrcData??[]);var c=l+y;if(c!==window.__slpLast&&c!=='[][]'){window.__slpLast=c;if(typeof Capacitor!=='undefined'&&Capacitor.Plugins&&Capacitor.Plugins.AndroidNativePlayback){Capacitor.Plugins.AndroidNativePlayback.updateFloatingLyricData({lrcData:l,yrcData:y});}}}catch(e){}},2000);})();"
    private var provider: LyriconProvider? = null
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private var currentSongName = ""
    private var currentArtist = ""
    private var titleField: java.lang.reflect.Field? = null
    private var artistField: java.lang.reflect.Field? = null

    override fun onHook() {
        YLog.debug(tag = TAG, msg = "正在注入进程: $processName")

        registerAppLifecycle {
            onCreate {
                val created = LyriconFactory.createProvider(
                    context = this,
                    providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
                    playerPackageName = Constants.MUSIC_PACKAGE_NAME,
                    logo = ProviderLogo.fromSvg(Constants.ICON),
                )
                provider = created
                created.player.setDisplayTranslation(true)
                created.player.setDisplayRoma(true)
                created.register()
            }
        }

        injectLyricsJs()

        "top.imsyy.splayer.android.playback.PlaybackManager".toClass().resolve().apply {
            firstMethod {
                name = "updateFloatingLyricSongInfo"
                parameters(classOf<String>(), classOf<String>())
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
                parameters(classOf<String>(), classOf<String>())
            }.hook {
                after {
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
                name = "updateMetadata"
                parameters($$"top.imsyy.splayer.android.playback.PlaybackManager$TrackMetadata".toClass())
            }.hook {
                after {
                    val metadata = args[0] ?: return@after
                    try {
                        if (titleField == null) {
                            titleField = metadata::class.java.getDeclaredField("title")
                                .apply { makeAccessible() }
                            artistField = metadata::class.java.getDeclaredField("artist")
                                .apply { makeAccessible() }
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
                parameters(classOf<PlaybackState>())
            }.hook {
                after {
                    val state = args[0] as? PlaybackState
                    provider?.player?.setPlaybackState(state)
                }
            }
        }
    }

    private fun injectLyricsJs() {
        "android.webkit.WebViewClient".toClass().resolve().apply {
            firstMethod {
                name = "onPageFinished"
                parameters(classOf<WebView>(), classOf<String>())
            }.hook {
                after {
                    val webView = args[0] as? WebView ?: return@after
                    webView.evaluateJavascript(INJECTED_JS, null)
                }
            }
        }
    }


    //    {
//        "words":[
//        {
//            "startTime": 35809,
//            "endTime": 40096,
//            "word": "背上所有的梦与想",
//            "romanWord": ""
//        }
//        ],
//        "translatedLyric": "",
//        "romanLyric": "",
//        "isBG": false,
//        "isDuet": false,
//        "startTime": 35809,
//        "endTime": 40096
//    },
    private fun parseToSong(lrcJson: String, yrcJson: String): Song? {
        hostApplication?.filesDir?.resolve("lrcJson.json")?.writeText(lrcJson)
        hostApplication?.filesDir?.resolve("yrcJson.json")?.writeText(yrcJson)

        try {
            val effectiveJson = when {
                yrcJson.isNotBlank() && yrcJson != "[]" -> yrcJson
                lrcJson.isNotBlank() && lrcJson != "[]" -> lrcJson
                else -> return null
            }

            val lines = mutableListOf<RichLyricLine>()
            val lyricLines = json.decodeFromString<List<LyricLine>>(effectiveJson)

            for (line in lyricLines) {

                val begin = line.startTime
                val end = line.endTime
                val translation = line.translatedLyric
                val wordsArr = line.words

                val words = mutableListOf<LyricWord>()

                for (word in wordsArr) {
                    val text = word.word
                    val wBegin = word.startTime
                    val wEnd = word.endTime
                    if (text.isNotEmpty()) {
                        words.add(LyricWord(text = text, begin = wBegin, end = wEnd))
                    }
                }

                val fullText = words.joinToString("") { it.text.toString() }
                if (fullText.isEmpty()) continue

                lines.add(
                    RichLyricLine(
                        begin = begin,
                        end = end,
                        text = fullText,
                        words = if (words.singleOrNull().let {
                                it != null && it.begin == begin && it.end == end
                            }) null else words.ifEmpty { null },
                        translation = translation.ifEmpty { null },
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
