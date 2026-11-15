/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/** Modern libxposed entry point for the NetEase Cloud Music providers. */
class HookEntry : XposedModule() {
    private val logger = ModuleLogger(this)
    private val hooksInstalled = AtomicBoolean(false)

    @Volatile
    private var processName: String? = null

    @Suppress("unused")
    private var cloudMusic: CloudMusic? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        logger.info(
            "Module loaded: process=${param.processName}, " +
                    "framework=${frameworkName} $frameworkVersion (${frameworkVersionCode}), " +
                    "api=${apiVersion}, systemServer=${param.isSystemServer}"
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val loadedProcess = processName
        if (loadedProcess == null) {
            logger.warn("Ignoring ${param.packageName}: onModuleLoaded was not received")
            return
        }

        val packageName = param.packageName
        if (!HookTarget.accepts(
                packageName = packageName,
                applicationPackageName = param.applicationInfo.packageName,
                processName = loadedProcess,
                isFirstPackage = param.isFirstPackage
            )
        ) {
            logger.debug("Ignoring unsupported package/process $packageName in $loadedProcess")
            return
        }

        if (!hooksInstalled.compareAndSet(false, true)) {
            logger.debug("Hooks already installed in $loadedProcess")
            return
        }

        val manager = CloudMusic(
            module = this,
            logger = logger,
            packageName = packageName,
            processName = loadedProcess,
            readyClassLoader = param.classLoader
        )
        cloudMusic = manager
        manager.installHooks()
    }
}

/** Routes all module diagnostics through the framework logger. */
internal class ModuleLogger(
    private val module: XposedModule,
    private val tag: String = "CloudMusicProvider"
) {
    fun debug(message: String) = module.log(Log.DEBUG, tag, message)

    fun info(message: String) = module.log(Log.INFO, tag, message)

    fun warn(message: String) = module.log(Log.WARN, tag, message)

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            module.log(Log.ERROR, tag, message)
        } else {
            module.log(Log.ERROR, tag, message, throwable)
        }
    }
}
