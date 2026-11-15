/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.paprovider.xposed.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * SAF URI 工具
 */
object SafUriResolver {
    private const val TAG = "SafUriResolver"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * 根据标准的 SAF Document ID 还原具备权限的 Uri
     *
     * @param context 上下文
     * @param standardDocumentId 标准的 SAF ID，例如 "primary:Music/Jay/暗号.flac"
     * @return 还原后的 Uri，若无匹配权限则返回 null
     */
    fun resolveToUri(context: Context, standardDocumentId: String): Uri? {
        if (standardDocumentId.isBlank() || !standardDocumentId.contains(":")) {
            Log.w(TAG, "Invalid SAF Document ID format: $standardDocumentId")
            return null
        }

        val contentResolver = context.contentResolver
        val persistedPermissions = contentResolver.persistedUriPermissions

        // 1. 获取输入 ID 的卷标识部分 (例如 "primary")
        val inputVolume = standardDocumentId.substringBefore(":")

        for (permission in persistedPermissions) {
            if (!permission.isReadPermission) continue

            val treeUri = permission.uri
            if (EXTERNAL_STORAGE_AUTHORITY != treeUri.authority) continue

            // 2. 获取授权目录的 Tree ID (如 "primary:Music")
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri) ?: continue
            val treeVolume = treeDocumentId.substringBefore(":")

            // 3. 匹配逻辑：
            // 首先卷 ID 必须一致 (primary 对 primary)
            // 其次，输入的完整 ID 必须以授权的 Tree ID 开头（即在该文件夹下）
            if (inputVolume.equals(treeVolume, ignoreCase = true) &&
                standardDocumentId.startsWith(treeDocumentId)
            ) {

                // 4. 合成最终 URI
                // 使用 buildDocumentUriUsingTree 确保 URI 携带了来自 treeUri 的持久化授权上下文
                val fileUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, standardDocumentId)

                Log.i(TAG, "Resolved SAF URI: $fileUri")
                return fileUri
            }
        }

        Log.w(TAG, "No matching persisted permission for ID: $standardDocumentId")
        return null
    }
}