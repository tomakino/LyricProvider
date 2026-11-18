/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.param.PackageParam

internal class MediaSessionHooks(
    private val classLoader: ClassLoader,
    private val onPlaybackStateChanged: (PlaybackState?) -> Unit,
    private val onTrackChanged: (TrackMetadata) -> Unit,
) {

    context(_: PackageParam)
    fun install() {
        "android.media.session.MediaSession".toClass(classLoader)
            .resolve()
            .apply {
                firstMethod {
                    name = "setPlaybackState"
                    parameters(classOf<PlaybackState>())
                }.hook {
                    after { onPlaybackStateChanged(args[0] as? PlaybackState) }
                }

                firstMethod {
                    name = "setMetadata"
                    parameters(classOf<MediaMetadata>())
                }.hook {
                    after {
                        val metadata = args[0] as? MediaMetadata ?: return@after
                        metadata.toTrackMetadata()?.let(onTrackChanged)
                    }
                }
            }
    }

    private fun MediaMetadata.toTrackMetadata(): TrackMetadata? {
        val title = getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        return TrackMetadata(
            title = title,
            artist = getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            album = getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            duration = getLong(MediaMetadata.METADATA_KEY_DURATION),
        )
    }
}
