package dev.or10n.messengercleaner.patches

import android.app.Activity
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.or10n.messengercleaner.TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.ArrayDeque

private const val HIDDEN_TAG_KEY = 0x4d434c4e

private val bannedSubstrings = listOf(
    "chat with ais",
    "create an ai",
    "facebook reels",
    "facebook events",
    "chat moments",
    "also from meta",
)

private val bannedExactLabels = setOf("meta ai")

private var uiScrubberInstalled = false

fun installUiScrubber() {
    if (uiScrubberInstalled) {
        return
    }

    XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val activity = param.thisObject as? Activity ?: return
            val decorView = activity.window?.decorView ?: return

            scheduleScrubPasses(decorView)
        }
    })

    uiScrubberInstalled = true
}

private fun scheduleScrubPasses(root: View) {
    val delays = longArrayOf(0, 50, 300, 1000, 2000, 4000, 8000, 12000, 16000, 30000, 60000)
    delays.forEach { delay ->
        if (delay == 0L) {
            scrubViewTree(root)
        } else {
            root.postDelayed({ scrubViewTree(root) }, delay)
        }
    }
}

private fun scrubViewTree(root: View) {
    val queue = ArrayDeque<View>()
    queue.add(root)

    while (queue.isNotEmpty()) {
        val view = queue.removeFirst()
        val matchedLabel = matchedLabel(view)
        if (matchedLabel != null) {
            hideMatchedView(root, view, matchedLabel)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                queue.addLast(view.getChildAt(index))
            }
        }
    }
}

private fun matchedLabel(view: View): String? {
    val candidates = mutableListOf<CharSequence>()
    val description = view.contentDescription
    if (!TextUtils.isEmpty(description)) {
        candidates += description
    }

    if (view is TextView) {
        if (!TextUtils.isEmpty(view.text)) {
            candidates += view.text
        }
        if (!TextUtils.isEmpty(view.hint)) {
            candidates += view.hint
        }
    }

    return candidates
        .map(CharSequence::toString)
        .firstOrNull(::isBannedLabel)
}

private fun isBannedLabel(raw: String): Boolean {
    val label = raw.trim().lowercase()
    if (label.isEmpty()) {
        return false
    }

    if (label in bannedExactLabels) {
        return true
    }

    return bannedSubstrings.any(label::contains)
}

private fun hideMatchedView(root: View, view: View, label: String) {
    val target = findHideTarget(root, view) ?: return
    if (target.getTag(HIDDEN_TAG_KEY) == true) {
        return
    }

    target.setTag(HIDDEN_TAG_KEY, true)
    val normalizedLabel = label.trim().lowercase()
    if (normalizedLabel in bannedExactLabels) {
        target.visibility = View.INVISIBLE
        target.alpha = 0f
        target.isEnabled = false
        target.isClickable = false
        target.isLongClickable = false
        target.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    } else {
        target.visibility = View.GONE
        target.isClickable = false
        target.isLongClickable = false
        target.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    XposedBridge.log("$TAG hid UI surface: $label")
}

private fun findHideTarget(root: View, view: View): View? {
    val parent = view.parent as? View
    val grandParent = parent?.parent as? View
    val candidates = listOfNotNull(view, parent, grandParent)

    return candidates.firstOrNull { isSafeHideTarget(root, it) }
}

private fun isSafeHideTarget(root: View, target: View): Boolean {
    val width = if (target.width > 0) target.width else target.right - target.left
    val height = if (target.height > 0) target.height else target.bottom - target.top
    if (width <= 0 || height <= 0) {
        return false
    }

    val rootWidth = if (root.width > 0) root.width else root.right - root.left
    val rootHeight = if (root.height > 0) root.height else root.bottom - root.top
    val rootArea = rootWidth * rootHeight
    val targetArea = width * height

    if (rootArea > 0 && targetArea > rootArea / 3) {
        return false
    }

    val className = target.javaClass.name
    return target.isClickable ||
        target.isFocusable ||
        target is TextView ||
        className.contains("Button") ||
        className.contains("ImageView")
}
