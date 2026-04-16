package dev.or10n.messengercleaner

import android.content.pm.ApplicationInfo
import android.util.Log
import de.robv.android.xposed.XposedHelpers
import java.io.File

const val TAG = "MessengerCleaner"
const val TARGET_PACKAGE = "com.facebook.orca"
const val INBOX_AD_ITEM_CLASS = "com.facebook.messaging.business.inboxads.common.InboxAdsItem"
const val INBOX_AD_ITEM_DESCRIPTOR = "Lcom/facebook/messaging/business/inboxads/common/InboxAdsItem;"

private var dexKitLoaded = false

fun loadDexKitLibrary() {
    if (dexKitLoaded) {
        return
    }

    System.loadLibrary("dexkit")
    dexKitLoaded = true
}

fun selectDexKitSource(appInfo: ApplicationInfo): String {
    return appInfo.splitSourceDirs
        ?.firstOrNull { it.contains("split_d_longtail_raw", ignoreCase = true) }
        ?: appInfo.sourceDir
}

fun allApkPaths(appInfo: ApplicationInfo): List<String> {
    return buildList {
        add(appInfo.sourceDir)
        appInfo.splitSourceDirs?.let { addAll(it) }
    }.distinct()
}

fun logInfo(message: String) {
    Log.i(TAG, message)
}

fun logWarn(message: String) {
    Log.w(TAG, message)
}

fun logError(message: String, throwable: Throwable? = null) {
    Log.e(TAG, message, throwable)
}

fun findClassOrNull(name: String, classLoader: ClassLoader): Class<*>? {
    return try {
        XposedHelpers.findClass(name, classLoader)
    } catch (_: XposedHelpers.ClassNotFoundError) {
        null
    }
}

fun existingFiles(paths: Iterable<String>): List<File> {
    return paths.map(::File).filter(File::exists)
}

fun logTargetBuild(appInfo: ApplicationInfo) {
    val splitCount = appInfo.splitSourceDirs?.size ?: 0
    val versionCode = runCatching {
        (XposedHelpers.callMethod(appInfo, "getLongVersionCode") as? Long)
    }.getOrNull() ?: runCatching {
        (appInfo.javaClass.getField("versionCode").get(appInfo) as? Int)?.toLong()
    }.getOrNull()
    logInfo(
        "Target build package=${appInfo.packageName} " +
            "versionCode=${versionCode ?: "unknown"} " +
            "splits=$splitCount"
    )
}

fun methodKey(className: String, methodName: String): String {
    return "$className#$methodName"
}
