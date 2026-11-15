/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.musicfreeprovider.xposed

import android.media.session.PlaybackState
import android.view.View
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.proify.extensions.android.AndroidUtils
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo

open class MusicFree(val tag: String = "MusicFreeProvider") : YukiBaseHooker() {
    private var provider: LyriconProvider? = null

    override fun onHook() {
        AndroidUtils.openBluetoothA2dpOn(appClassLoader)
        YLog.debug(tag = tag, msg = "进程: $processName")

        onAppLifecycle {
            onCreate {
                initProvider()
            }
        }
        hookMediaSession()
        hook()
    }

    private fun hook() {
        XposedHelpers.findAndHookMethod(
            "fun.upup.musicfree.lyricUtil.LyricView".toClass(),
            "setText",
            String::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ts = XposedHelpers.getObjectField(param.thisObject, "tv") as TextView

                    val rootView = ts.getRootView() ?: return
                    rootView.visibility = View.GONE
                    rootView.setAlpha(0f)

                    val text = param.args[0] as CharSequence

                    if (text.isBlank()) return

                    val newText = text.lines().mapNotNull {
                        if (it.trim() == "//") null else it
                    }.joinToString("\n")

                    provider?.player?.sendText(newText)
                }
            })
    }

    private fun initProvider() {
        val context = appContext ?: return
        provider = LyriconFactory.createProvider(
            context = context,
            providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
            playerPackageName = context.packageName,
            logo = ProviderLogo.fromBase64(Constants.ICON)
        ).apply {
            player.setDisplayTranslation(true)
            register()
        }
    }

    private fun hookMediaSession() {
        "android.media.session.MediaSession".toClass().resolve().apply {
            firstMethod {
                name = "setPlaybackState"
                parameters(PlaybackState::class.java)
            }.hook {
                after {
                    val state = args[0] as? PlaybackState
                    provider?.player?.setPlaybackState(state)
                }
            }
        }
    }
}