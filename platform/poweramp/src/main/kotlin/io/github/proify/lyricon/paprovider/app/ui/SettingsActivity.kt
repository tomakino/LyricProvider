/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.paprovider.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.paprovider.R
import io.github.proify.lyricon.paprovider.bridge.BridgeConstants
import io.github.proify.lyricon.paprovider.bridge.Configs
import io.github.proify.lyricon.paprovider.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private val xposedScope by lazy {
        resources.getStringArray(R.array.xposed_scope)
    }

    val channels by lazy {
        xposedScope.map { dataChannel(it) }
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
    }

    private fun initViews() {
        binding.switchTranslation.isChecked = prefs().get(Configs.ENABLE_TRANSLATION)
        binding.switchNetSearch.isChecked = prefs().get(Configs.ENABLE_NET_SEARCH)

        //binding.switchAutoSave.isChecked = prefs().get(Config.ENABLE_AUTO_SAVE)
        //updateAutoSaveState(binding.switchNetSearch.isChecked)

        binding.switchTranslation.setOnCheckedChangeListener { _, isChecked ->
            YLog.debug("[设置] 翻译显示 -> $isChecked")
            prefs().edit().apply {
                put(Configs.ENABLE_TRANSLATION, isChecked)
                commit() //使用 commit() 而不是 apply()，确保数据被立即写入
            }
            notifySettingsChanged()
        }

        binding.switchNetSearch.setOnCheckedChangeListener { _, isChecked ->
            YLog.debug("[设置] 云端搜索 -> $isChecked")
            prefs().edit().apply {
                put(Configs.ENABLE_NET_SEARCH, isChecked)
                commit()
            }
            notifySettingsChanged()
            //updateAutoSaveState(isChecked)
        }

//        binding.switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
//            YLog.debug("[设置] 自动保存 -> $isChecked")
//            prefs().edit { put(Config.ENABLE_AUTO_SAVE, isChecked) }
//        }

        setSupportActionBar(binding.toolbar)
    }

    fun notifySettingsChanged() {
        channels.forEach {
            it.put(BridgeConstants.ACTION_SETTING_CHANGED)
        }
    }

//    private fun updateAutoSaveState(isNetSearchEnabled: Boolean) {
//        binding.switchAutoSave.isEnabled = isNetSearchEnabled
//        binding.switchAutoSave.alpha = if (isNetSearchEnabled) 1.0f else 0.4f
//    }
}