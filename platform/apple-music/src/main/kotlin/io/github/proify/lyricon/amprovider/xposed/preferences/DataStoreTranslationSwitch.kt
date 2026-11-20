/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.preferences

import java.io.File

/** 7.x 的 DataStore 存储。 */
internal class DataStoreTranslationSwitch(
    dataDir: File,
    onChanged: (Boolean) -> Unit,
) : TranslationSwitchFileWatcher(
    directory = File(dataDir, "files/datastore"),
    fileName = DATA_STORE_FILE,
    onChanged = onChanged,
) {

    override val fallback: Boolean = DEFAULT

    override fun read(): Boolean? {
        val file = File(directory, fileName)
        if (!file.isFile) return null
        return PreferencesProto.readBoolean(file, KEY)
    }

    private companion object {
        const val DATA_STORE_FILE = "settings.preferences_pb"
        const val KEY = "key_player_lyrics_translation_selected"
        const val DEFAULT = true
    }
}
