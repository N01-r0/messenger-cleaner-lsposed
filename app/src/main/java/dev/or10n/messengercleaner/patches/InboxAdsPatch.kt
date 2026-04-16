package dev.or10n.messengercleaner.patches

import android.content.pm.ApplicationInfo
import dev.or10n.messengercleaner.INBOX_AD_ITEM_CLASS
import dev.or10n.messengercleaner.findClassOrNull
import dev.or10n.messengercleaner.logError
import dev.or10n.messengercleaner.logInfo
import dev.or10n.messengercleaner.logWarn
import dev.or10n.messengercleaner.methodKey
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier
import java.util.ArrayList

private const val INBOX_LIST_PROCESSOR_CLASS = "X.2Mq"
private const val INBOX_LIST_PROCESSOR_METHOD = "Czo"

fun removeInboxAds(classLoader: ClassLoader, bridge: DexKitBridge, appInfo: ApplicationInfo) {
    val referenceHooked = tryHookReferenceSupplier(classLoader, bridge)
    val adaptiveHooked = tryHookAdaptiveInboxProcessors(classLoader, bridge, appInfo)
    val fallbackHooked = if (adaptiveHooked) true else tryHookKnownProcessorFallback(classLoader)

    if (!referenceHooked && !adaptiveHooked && !fallbackHooked) {
        logWarn("No inbox ad hook attached")
    }
}

private fun tryHookReferenceSupplier(classLoader: ClassLoader, bridge: DexKitBridge): Boolean {
    return try {
        val classData = bridge.findClass {
            searchPackages("com.facebook.messaging.business.inboxads.plugins.inboxads.itemsupplier")
            matcher {
                className("com.facebook.messaging.business.inboxads.plugins.inboxads.itemsupplier.InboxAdsItemSupplierImplementation")
            }
        }.firstOrNull() ?: return false

        val method = bridge.findMethod {
            searchInClass(listOf(classData))
            matcher {
                returnType("void")
                usingStrings("ads_load_begin", "inbox_ads_fetch_start")
                modifiers(Modifier.PUBLIC or Modifier.STATIC)
            }
        }.firstOrNull() ?: return false

        XposedBridge.hookMethod(method.getMethodInstance(classLoader), XC_MethodReplacement.DO_NOTHING)
        logInfo("Attached reference inbox-ads supplier hook")
        true
    } catch (t: Throwable) {
        logWarn("Reference inbox-ads hook failed: ${t.message}")
        false
    }
}

private fun tryHookAdaptiveInboxProcessors(
    classLoader: ClassLoader,
    bridge: DexKitBridge,
    appInfo: ApplicationInfo,
): Boolean {
    return try {
        val hostImmutableListClass = XposedHelpers.findClass("com.google.common.collect.ImmutableList", classLoader)
        val adItemClass = findClassOrNull(INBOX_AD_ITEM_CLASS, classLoader) ?: return false
        val processorKeys = collectInboxAdsProcessorKeys(appInfo)
        if (processorKeys.isEmpty()) {
            logWarn("No inbox-ad processor keys discovered from APK")
            return false
        }

        val candidates = bridge.findMethod {
            matcher {
                returnType("com.google.common.collect.ImmutableList")
                paramCount(3)
                addParamType("com.google.common.collect.ImmutableList")
                addParamType("java.lang.String")
            }
        }.filter { methodKey(it.className, it.name) in processorKeys }

        if (candidates.isEmpty()) {
            logWarn("No adaptive inbox processor methods matched discovered APK keys")
            return false
        }

        var hookedCount = 0
        candidates.forEach { methodData ->
            val method = methodData.getMethodInstance(classLoader)
            XposedBridge.hookMethod(method, buildInboxAdFilter(hostImmutableListClass, adItemClass))
            hookedCount++
        }

        logInfo("Attached $hookedCount adaptive inbox list filter(s)")
        hookedCount > 0
    } catch (t: Throwable) {
        logError("Adaptive inbox list filter failed", t)
        false
    }
}

private fun tryHookKnownProcessorFallback(classLoader: ClassLoader): Boolean {
    return try {
        val hostImmutableListClass = XposedHelpers.findClass("com.google.common.collect.ImmutableList", classLoader)
        val adItemClass = findClassOrNull(INBOX_AD_ITEM_CLASS, classLoader) ?: return false
        XposedHelpers.findAndHookMethod(
            INBOX_LIST_PROCESSOR_CLASS,
            classLoader,
            INBOX_LIST_PROCESSOR_METHOD,
            "X.1kU",
            hostImmutableListClass,
            String::class.java,
            buildInboxAdFilter(hostImmutableListClass, adItemClass),
        )
        logInfo("Attached known inbox processor fallback")
        true
    } catch (t: Throwable) {
        logError("Known inbox processor fallback failed", t)
        false
    }
}

private fun buildInboxAdFilter(
    hostImmutableListClass: Class<*>,
    adItemClass: Class<*>,
): XC_MethodHook {
    return object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val original = param.result as? Iterable<*> ?: return
            val filtered = ArrayList<Any?>()
            var removed = 0

            original.forEach { item ->
                if (item != null && adItemClass.isInstance(item)) {
                    removed++
                } else {
                    filtered += item
                }
            }

            if (removed == 0) {
                return
            }

            val rebuilt = XposedHelpers.callStaticMethod(
                hostImmutableListClass,
                "copyOf",
                filtered,
            )
            param.result = rebuilt
            logInfo("Filtered $removed inbox ad item(s)")
        }
    }
}
