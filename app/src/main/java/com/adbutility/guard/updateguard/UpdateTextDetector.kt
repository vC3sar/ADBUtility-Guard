package com.adbutility.guard.updateguard

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale

/**
 * Coordenadas rectangulares puras independientes de Android UI para análisis y pruebas unitarias.
 */
data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun isValid(): Boolean = right > left && bottom > top && width >= 4 && height >= 4

    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)
}

/**
 * Representa un botón o elemento interactivo candidato extraído de la ventana actual.
 */
data class ActionButtonCandidate(
    val rawLabel: String,
    val normalizedLabel: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val boundsInScreen: NodeBounds,
    val boundsInWindow: NodeBounds? = null,
    val isClickable: Boolean = true,
    val isEnabled: Boolean = true
)

/**
 * Instantánea contextual completa de una ventana o diálogo observado por el servicio.
 */
data class ScreenContextSnapshot(
    val packageName: String,
    val className: String,
    val eventType: Int,
    val rawTexts: List<String>,
    val normalizedText: String,
    val viewIds: List<String>,
    val buttons: List<ActionButtonCandidate>,
    val clickedLabel: String = "",
    val clickedViewId: String = ""
)

/**
 * Extractor y normalizador de textos, identificadores, jerarquía de nodos y límites de botones
 * a partir del árbol de accesibilidad.
 */
object UpdateTextDetector {

    private const val MAX_TREE_DEPTH = 10
    private const val MAX_NODES_VISITED = 160

    fun normalizeText(input: String): String {
        if (input.isBlank()) return ""
        val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Construye una instantánea contextual a partir de un evento de accesibilidad y su árbol activo.
     */
    fun captureSnapshot(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): ScreenContextSnapshot {
        val pkgName = event.packageName?.toString()?.trim().orEmpty()
        val className = event.className?.toString()?.trim().orEmpty()
        val rawTexts = mutableListOf<String>()
        val viewIds = mutableListOf<String>()
        val buttons = mutableListOf<ActionButtonCandidate>()

        event.text?.forEach { cs ->
            val str = cs?.toString()?.trim()
            if (!str.isNullOrEmpty()) rawTexts.add(str)
        }
        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            rawTexts.add(it)
        }

        var clickedLabel = ""
        var clickedViewId = ""
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = try {
                event.source
            } catch (_: Exception) {
                null
            }
            if (source != null) {
                try {
                    clickedLabel = extractCompositeLabel(source)
                    clickedViewId = source.viewIdResourceName?.trim().orEmpty()
                } finally {
                    @Suppress("DEPRECATION")
                    try {
                        source.recycle()
                    } catch (_: Exception) {
                    }
                }
            } else {
                clickedLabel = event.text?.joinToString(" ")?.trim().orEmpty()
            }
        }

        if (rootNode != null) {
            val visitedCount = IntArray(1)
            traverseNode(
                node = rootNode,
                depth = 0,
                visitedCount = visitedCount,
                rawTexts = rawTexts,
                viewIds = viewIds,
                buttons = buttons,
                recycleCurrent = false // El root pertenece al caller
            )
        }

        val combinedRaw = rawTexts.distinct().joinToString(" ")
        val normalized = normalizeText(combinedRaw)

        return ScreenContextSnapshot(
            packageName = pkgName,
            className = className,
            eventType = event.eventType,
            rawTexts = rawTexts.distinct(),
            normalizedText = normalized,
            viewIds = viewIds.distinct(),
            buttons = buttons,
            clickedLabel = normalizeText(clickedLabel),
            clickedViewId = clickedViewId.lowercase(Locale.ROOT)
        )
    }

    /**
     * Construye una instantánea directamente desde el nodo raíz visible (sin evento previo),
     * útil cuando el servicio se reconecta mientras un diálogo ya está abierto en pantalla.
     */
    fun captureSnapshotFromRoot(
        rootNode: AccessibilityNodeInfo,
        windowTitle: String = ""
    ): ScreenContextSnapshot {
        val pkgName = rootNode.packageName?.toString()?.trim().orEmpty()
        val className = rootNode.className?.toString()?.trim().orEmpty()
        val rawTexts = mutableListOf<String>()
        val viewIds = mutableListOf<String>()
        val buttons = mutableListOf<ActionButtonCandidate>()

        if (windowTitle.isNotBlank()) {
            rawTexts.add(windowTitle.trim())
        }

        val visitedCount = IntArray(1)
        traverseNode(
            node = rootNode,
            depth = 0,
            visitedCount = visitedCount,
            rawTexts = rawTexts,
            viewIds = viewIds,
            buttons = buttons,
            recycleCurrent = false
        )

        val combinedRaw = rawTexts.distinct().joinToString(" ")
        return ScreenContextSnapshot(
            packageName = pkgName,
            className = className,
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            rawTexts = rawTexts.distinct(),
            normalizedText = normalizeText(combinedRaw),
            viewIds = viewIds.distinct(),
            buttons = buttons
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        visitedCount: IntArray,
        rawTexts: MutableList<String>,
        viewIds: MutableList<String>,
        buttons: MutableList<ActionButtonCandidate>,
        recycleCurrent: Boolean
    ) {
        if (depth > MAX_TREE_DEPTH || visitedCount[0] >= MAX_NODES_VISITED) {
            if (recycleCurrent) safeRecycle(node)
            return
        }
        visitedCount[0]++

        try {
            val ownText = node.text?.toString()?.trim().orEmpty()
            val ownDesc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName?.trim().orEmpty()
            val clsName = node.className?.toString()?.trim().orEmpty()

            if (ownText.isNotEmpty()) rawTexts.add(ownText)
            if (ownDesc.isNotEmpty()) rawTexts.add(ownDesc)
            if (viewId.isNotEmpty()) viewIds.add(viewId.lowercase(Locale.ROOT))

            val looksLikeButton = node.isClickable && (
                clsName.contains("Button", ignoreCase = true) ||
                    viewId.contains("button", ignoreCase = true) ||
                    viewId.contains("btn", ignoreCase = true) ||
                    viewId.contains("action", ignoreCase = true) ||
                    ownText.isNotEmpty() ||
                    ownDesc.isNotEmpty() ||
                    hasDirectTextDescendant(node)
                )

            if (looksLikeButton && node.isVisibleToUser) {
                val effectiveLabel = when {
                    ownText.isNotEmpty() -> ownText
                    ownDesc.isNotEmpty() -> ownDesc
                    else -> extractCompositeLabel(node)
                }

                if (effectiveLabel.isNotBlank()) {
                    val screenRect = Rect()
                    node.getBoundsInScreen(screenRect)
                    val screenBounds = NodeBounds(
                        screenRect.left,
                        screenRect.top,
                        screenRect.right,
                        screenRect.bottom
                    )

                    val windowBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val winRect = Rect()
                        node.getBoundsInWindow(winRect)
                        NodeBounds(winRect.left, winRect.top, winRect.right, winRect.bottom)
                    } else {
                        null
                    }

                    if (screenBounds.isValid()) {
                        buttons.add(
                            ActionButtonCandidate(
                                rawLabel = effectiveLabel,
                                normalizedLabel = normalizeText(effectiveLabel),
                                contentDescription = normalizeText(ownDesc),
                                viewId = viewId.lowercase(Locale.ROOT),
                                className = clsName,
                                boundsInScreen = screenBounds,
                                boundsInWindow = windowBounds,
                                isClickable = node.isClickable,
                                isEnabled = node.isEnabled
                            )
                        )
                    }
                }
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (visitedCount[0] >= MAX_NODES_VISITED) break
                val child = node.getChild(i) ?: continue
                traverseNode(
                    node = child,
                    depth = depth + 1,
                    visitedCount = visitedCount,
                    rawTexts = rawTexts,
                    viewIds = viewIds,
                    buttons = buttons,
                    recycleCurrent = true
                )
            }
        } finally {
            if (recycleCurrent) safeRecycle(node)
        }
    }

    private fun hasDirectTextDescendant(node: AccessibilityNodeInfo): Boolean {
        val count = node.childCount.coerceAtMost(5)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                if (!child.text.isNullOrBlank() || !child.contentDescription.isNullOrBlank()) {
                    return true
                }
            } finally {
                safeRecycle(child)
            }
        }
        return false
    }

    private fun extractCompositeLabel(node: AccessibilityNodeInfo, depth: Int = 0): String {
        val own = node.text?.toString()?.trim().orEmpty()
        if (own.isNotEmpty()) return own
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty()) return desc
        if (depth >= 2) return ""

        val parts = mutableListOf<String>()
        val count = node.childCount.coerceAtMost(6)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try {
                val childText = extractCompositeLabel(child, depth + 1)
                if (childText.isNotEmpty()) parts.add(childText)
            } finally {
                safeRecycle(child)
            }
        }
        return parts.joinToString(" ").trim()
    }

    private fun safeRecycle(node: AccessibilityNodeInfo) {
        try {
            @Suppress("DEPRECATION")
            node.recycle()
        } catch (_: Exception) {
        }
    }
}

