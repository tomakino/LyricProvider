/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.xposed.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log

object SafUriResolver {
    private const val TAG = "SafUriResolver"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    fun resolveToUri(context: Context, standardDocumentId: String): Uri? {
        if (standardDocumentId.isBlank() || !standardDocumentId.contains(":")) {
            Log.w(TAG, "Invalid SAF Document ID format: $standardDocumentId")
            return null
        }

        val contentResolver = context.contentResolver
        val persistedPermissions = contentResolver.persistedUriPermissions

        val inputVolume = standardDocumentId.substringBefore(":")

        for (permission in persistedPermissions) {
            if (!permission.isReadPermission) continue

            val treeUri = permission.uri
            if (EXTERNAL_STORAGE_AUTHORITY != treeUri.authority) continue

            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri) ?: continue
            val treeVolume = treeDocumentId.substringBefore(":")

            if (inputVolume.equals(treeVolume, ignoreCase = true) &&
                standardDocumentId.startsWith(treeDocumentId)
            ) {
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, standardDocumentId)
            }
        }

        // 降级：尝试直接转换为文件路径（仅限外部存储）
        if (standardDocumentId.startsWith("primary:")) {
            val relativePath = standardDocumentId.substringAfter(":")
            val file = Environment.getExternalStoragePublicDirectory(relativePath)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }

        Log.w(TAG, "No matching persisted permission for ID: $standardDocumentId")
        return null
    }
}