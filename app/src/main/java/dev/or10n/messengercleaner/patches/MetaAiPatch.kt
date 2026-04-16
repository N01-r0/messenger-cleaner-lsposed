package dev.or10n.messengercleaner.patches

import android.content.pm.ApplicationInfo
import dev.or10n.messengercleaner.allApkPaths
import dev.or10n.messengercleaner.existingFiles
import dev.or10n.messengercleaner.logError
import dev.or10n.messengercleaner.logInfo
import dev.or10n.messengercleaner.logWarn
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

private val metaAiKillSwitchTokens = listOf(
    "SearchAiagentImplementationsKillSwitch",
    "AiAgentPluginsKillSwitch",
)

private const val META_AI_PREFIX_LENGTH = 7

fun removeMetaAi(classLoader: ClassLoader, bridge: DexKitBridge, appInfo: ApplicationInfo) {
    try {
        val killSwitchMethods = findKillSwitchMethods(bridge)
        if (killSwitchMethods.isEmpty()) {
            logWarn("No Meta AI kill switch methods found")
        } else {
            hookKillSwitchMethods(classLoader, killSwitchMethods)
        }

        val prefixes = extractFlagPrefixes(killSwitchMethods, appInfo)
        if (prefixes.isEmpty()) {
            logWarn("No Meta AI mobile-config prefixes found")
            return
        }

        hookMobileConfigBooleans(classLoader, bridge, prefixes)
    } catch (t: Throwable) {
        logError("Failed to remove Meta AI", t)
    }
}

private fun findKillSwitchMethods(bridge: DexKitBridge): List<MethodData> {
    return metaAiKillSwitchTokens
        .flatMap { token ->
            bridge.findMethod {
                matcher {
                    usingStrings(token)
                }
            }
        }
        .distinctBy { "${it.className}#${it.name}" }
}

private fun hookKillSwitchMethods(classLoader: ClassLoader, methods: List<MethodData>) {
    methods.forEach { methodData ->
        if (!methodData.isMethod) {
            return@forEach
        }

        val method = methodData.getMethodInstance(classLoader)
        val returnType = method.returnType
        if (returnType == Boolean::class.javaPrimitiveType || returnType == java.lang.Boolean::class.java) {
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
            logInfo("Hooked Meta AI switch ${method.declaringClass.name}.${method.name}")
        }
    }
}

private fun extractFlagPrefixes(
    methods: List<MethodData>,
    appInfo: ApplicationInfo,
): Set<String> {
    if (methods.isEmpty()) {
        return emptySet()
    }

    val classDescriptors = methods
        .map { "L" + it.className.replace('.', '/') + ";" }
        .toSet()

    val prefixes = linkedSetOf<String>()
    for (apkFile in existingFiles(allApkPaths(appInfo))) {
        val container = DexFileFactory.loadDexContainer(apkFile, Opcodes.getDefault())
        for (dexEntryName in container.dexEntryNames) {
            val dexFile = container.getEntry(dexEntryName)?.dexFile ?: continue
            for (classDef in dexFile.classes) {
                if (classDef.type !in classDescriptors) {
                    continue
                }

                for (method in classDef.methods) {
                    val implementation = method.implementation ?: continue
                    for (instruction in implementation.instructions) {
                        val literal = (instruction as? WideLiteralInstruction)?.wideLiteral ?: continue
                        if (literal <= 0) {
                            continue
                        }

                        val digits = literal.toString()
                        if (digits.length >= META_AI_PREFIX_LENGTH) {
                            prefixes += digits.substring(0, META_AI_PREFIX_LENGTH)
                        }
                    }
                }
            }
        }
    }

    if (prefixes.isNotEmpty()) {
        logInfo("Meta AI flag prefixes: ${prefixes.joinToString()}")
    }
    return prefixes
}

private fun hookMobileConfigBooleans(
    classLoader: ClassLoader,
    bridge: DexKitBridge,
    prefixes: Set<String>,
) {
    val mobileConfigClasses = bridge.findClass {
        matcher {
            addInterface("com.facebook.mobileconfig.factory.MobileConfigUnsafeContext")
        }
    }

    if (mobileConfigClasses.isEmpty()) {
        logWarn("No MobileConfigUnsafeContext implementation found")
        return
    }

    val booleanGetters = bridge.findMethod {
        searchInClass(mobileConfigClasses)
        matcher {
            returnType("boolean")
            paramCount(1)
            addParamType("long")
        }
    }

    if (booleanGetters.isEmpty()) {
        logWarn("No MobileConfig boolean getters found")
        return
    }

    booleanGetters.forEach { methodData ->
        val method = methodData.getMethodInstance(classLoader)
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val flagId = param.args.firstOrNull() as? Long ?: return
                val digits = flagId.toString()
                if (prefixes.any(digits::startsWith)) {
                    param.result = false
                }
            }
        })
    }

    logInfo("Hooked ${booleanGetters.size} MobileConfig boolean getters for Meta AI")
}
