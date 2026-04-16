package dev.or10n.messengercleaner.patches

import android.content.pm.ApplicationInfo
import dev.or10n.messengercleaner.INBOX_AD_ITEM_DESCRIPTOR
import dev.or10n.messengercleaner.allApkPaths
import dev.or10n.messengercleaner.existingFiles
import dev.or10n.messengercleaner.methodKey
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.MethodImplementation
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.TypeReference

fun collectInboxAdsProcessorKeys(appInfo: ApplicationInfo): Set<String> {
    val keys = linkedSetOf<String>()

    for (apkFile in existingFiles(allApkPaths(appInfo))) {
        val container = DexFileFactory.loadDexContainer(apkFile, Opcodes.getDefault())
        for (dexEntryName in container.dexEntryNames) {
            val dexFile = container.getEntry(dexEntryName)?.dexFile ?: continue
            for (classDef in dexFile.classes) {
                for (method in classDef.methods) {
                    if (!looksLikeInboxListProcessor(method.parameterTypes, method.returnType)) {
                        continue
                    }
                    val implementation = method.implementation ?: continue
                    if (!containsInboxAdsTypeReference(implementation)) {
                        continue
                    }

                    val className = classDef.type
                        .removePrefix("L")
                        .removeSuffix(";")
                        .replace('/', '.')
                    keys += methodKey(className, method.name)
                }
            }
        }
    }

    return keys
}

private fun looksLikeInboxListProcessor(
    parameterTypes: List<CharSequence>,
    returnType: String,
): Boolean {
    if (parameterTypes.size != 3) {
        return false
    }

    return returnType == "Lcom/google/common/collect/ImmutableList;" &&
        parameterTypes.any { it.toString() == "Lcom/google/common/collect/ImmutableList;" } &&
        parameterTypes.any { it.toString() == "Ljava/lang/String;" }
}

private fun containsInboxAdsTypeReference(implementation: MethodImplementation): Boolean {
    return implementation.instructions.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        val typeReference = reference as? TypeReference
        typeReference?.type == INBOX_AD_ITEM_DESCRIPTOR
    }
}
