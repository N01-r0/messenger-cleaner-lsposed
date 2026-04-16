package dev.or10n.messengercleaner

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.or10n.messengercleaner.patches.removeInboxAds
import dev.or10n.messengercleaner.patches.removeMetaAi
import dev.or10n.messengercleaner.patches.installUiScrubber
import org.luckypray.dexkit.DexKitBridge

class Module : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) {
            return
        }

        loadDexKitLibrary()
        logTargetBuild(lpparam.appInfo)

        try {
            DexKitBridge.create(selectDexKitSource(lpparam.appInfo)).use { bridge ->
                if (bridge == null) {
                    Log.e(TAG, "Failed to open DexKit bridge")
                    return
                }

                installUiScrubber()
                removeMetaAi(lpparam.classLoader, bridge, lpparam.appInfo)
                removeInboxAds(lpparam.classLoader, bridge, lpparam.appInfo)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Module initialization failed", t)
        }
    }
}
