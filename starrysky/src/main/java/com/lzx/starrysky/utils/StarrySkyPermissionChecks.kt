package com.lzx.starrysky.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 与「媒体通知」「蓝牙耳机断开暂停」等能力相关的**运行时**权限检测。
 *
 * ### 隐私合规（重要）
 * - `starrysky` 仅在 **AndroidManifest** 中合并声明部分敏感权限，**不会在库代码里主动调用**
 *   `requestPermissions` / `registerForActivityResult`；不会出现「一集成就静默弹系统权限框」的行为。
 * - 是否申请、何时申请，必须由 **宿主 App** 在 **用户知情同意** 之后发起（例如：用户已同意隐私政策、
 *   或在「开启通知栏播放控制」「使用蓝牙耳机相关功能」等场景下，先说明用途再调系统申请）。
 * - 上架合规（应用商店 / 个人信息保护法）通常还要求：在隐私政策中写明权限目的、与功能对应关系；
 *   本类只解决「技术侧不在 SDK 内擅自申请」与「便于宿主判断当前是否已授权」。
 */
object StarrySkyPermissionChecks {

    /** API 33+ 通知权限是否已授予；低版本视为已满足（无该运行时权限项）。 */
    fun isPostNotificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** API 31+ 蓝牙连接信息是否可读；低版本视为已满足。未授权时 [com.lzx.starrysky.service.MusicService] 内耳机逻辑会静默失败（已 try/catch）。 */
    fun isBluetoothConnectGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requiresPostNotificationsRuntimePrompt(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun requiresBluetoothConnectRuntimePrompt(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
