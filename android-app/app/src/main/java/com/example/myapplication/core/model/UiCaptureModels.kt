package com.example.myapplication.core.model

data class NormalizedNode(
    val text: String,
    val contentDescription: String,
    val className: String,
    val clickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val bounds: NormalizedBounds
)

data class NormalizedBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class CaptureResult(
    val packageName: String,
    val activityName: String,
    val nodes: List<NormalizedNode>,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val screenshotBytes: ByteArray? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
) {
    val scaleX: Float get() = if (screenWidth > 0) imageWidth.toFloat() / screenWidth else 1f
    val scaleY: Float get() = if (screenHeight > 0) imageHeight.toFloat() / screenHeight else 1f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CaptureResult) return false
        return packageName == other.packageName &&
            activityName == other.activityName &&
            nodes == other.nodes &&
            screenWidth == other.screenWidth &&
            screenHeight == other.screenHeight &&
            imageWidth == other.imageWidth &&
            imageHeight == other.imageHeight &&
            screenshotBytes.contentEquals(other.screenshotBytes)
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + activityName.hashCode()
        result = 31 * result + nodes.hashCode()
        result = 31 * result + (screenshotBytes?.contentHashCode() ?: 0)
        result = 31 * result + screenWidth
        result = 31 * result + screenHeight
        result = 31 * result + imageWidth
        result = 31 * result + imageHeight
        return result
    }
}

data class ScreenshotCapture(
    val bytes: ByteArray,
    val imageWidth: Int,
    val imageHeight: Int,
    val savedPath: String? = null
)

fun CaptureResult.withScreenshot(screenshot: ScreenshotCapture): CaptureResult = copy(
    screenshotBytes = screenshot.bytes,
    imageWidth = screenshot.imageWidth,
    imageHeight = screenshot.imageHeight
)

fun screenToImage(screenPx: Int, screenDim: Int, imageDim: Int): Int =
    if (screenDim > 0) (screenPx.toFloat() / screenDim * imageDim).toInt() else screenPx

fun imageToScreen(imagePx: Int, imageDim: Int, screenDim: Int): Int =
    if (imageDim > 0) (imagePx.toFloat() / imageDim * screenDim).toInt() else imagePx

fun NormalizedBounds.toImageSpace(result: CaptureResult): NormalizedBounds = NormalizedBounds(
    left = screenToImage(left, result.screenWidth, result.imageWidth),
    top = screenToImage(top, result.screenHeight, result.imageHeight),
    right = screenToImage(right, result.screenWidth, result.imageWidth),
    bottom = screenToImage(bottom, result.screenHeight, result.imageHeight)
)

data class RawNodeSnapshot(
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = false,
    val bounds: NormalizedBounds,
    val children: List<RawNodeSnapshot> = emptyList()
)

object NodeNormalizer {

    fun normalize(root: RawNodeSnapshot): List<NormalizedNode> {
        val viewport = root.bounds
        val directNodes = mutableListOf<NormalizedNode>()
        val mergedByBounds = linkedMapOf<NormalizedBounds, MergedInteractiveNode>()

        traverse(
            node = root,
            directNodes = directNodes,
            mergedByBounds = mergedByBounds,
            clickableAncestor = null,
            viewport = viewport
        )

        val resolvedNodes = directNodes.toMutableList()
        mergedByBounds.values.forEach { merged ->
            val existingIndex = resolvedNodes.indexOfFirst { it.bounds == merged.bounds && it.clickable }
            if (existingIndex >= 0) {
                resolvedNodes[existingIndex] = merged.mergeInto(resolvedNodes[existingIndex])
            } else {
                resolvedNodes += merged.toNormalizedNode()
            }
        }

        return resolvedNodes
            .mapNotNull { it.sanitizeForViewport(viewport) }
            .sortedWith(compareBy<NormalizedNode> { it.bounds.top }.thenBy { it.bounds.left })
    }

    private fun traverse(
        node: RawNodeSnapshot,
        directNodes: MutableList<NormalizedNode>,
        mergedByBounds: MutableMap<NormalizedBounds, MergedInteractiveNode>,
        clickableAncestor: RawNodeSnapshot?,
        viewport: NormalizedBounds
    ) {
        if (!node.bounds.isRectValid()) {
            return
        }

        val normalized = NormalizedNode(
            text = node.text,
            contentDescription = node.contentDescription,
            className = node.className,
            clickable = node.clickable,
            editable = node.editable,
            enabled = node.enabled,
            bounds = node.bounds
        )

        if (normalized.hasInteractiveSemantics()) {
            directNodes.add(normalized)
        } else if (normalized.hasReadableLabel() &&
            clickableAncestor != null
        ) {
            val merged = mergedByBounds.getOrPut(clickableAncestor.bounds) {
                MergedInteractiveNode(
                    bounds = clickableAncestor.bounds,
                    className = clickableAncestor.className,
                    enabled = clickableAncestor.enabled,
                    clickable = true,
                    editable = clickableAncestor.editable
                )
            }
            merged.addLabel(normalized)
        } else if (normalized.hasReadableLabel()) {
            directNodes.add(normalized)
        }

        val nextAncestor = if (node.clickable) node else clickableAncestor
        node.children.forEach { child ->
            traverse(child, directNodes, mergedByBounds, nextAncestor, viewport)
        }
    }

    private class MergedInteractiveNode(
        val bounds: NormalizedBounds,
        private val className: String,
        private val enabled: Boolean,
        private val clickable: Boolean,
        private val editable: Boolean
    ) {
        private val texts = linkedSetOf<String>()
        private val descriptions = linkedSetOf<String>()

        fun addLabel(node: NormalizedNode) {
            if (node.text.isNotBlank()) texts += node.text
            if (node.contentDescription.isNotBlank()) descriptions += node.contentDescription
        }

        fun mergeInto(node: NormalizedNode): NormalizedNode = node.copy(
            text = mergeStrings(node.text, texts),
            contentDescription = mergeStrings(node.contentDescription, descriptions)
        )

        fun toNormalizedNode(): NormalizedNode = NormalizedNode(
            text = texts.joinToString(" ").trim(),
            contentDescription = descriptions.joinToString(" ").trim(),
            className = className,
            clickable = clickable,
            editable = editable,
            enabled = enabled,
            bounds = bounds
        )

        private fun mergeStrings(primary: String, extras: LinkedHashSet<String>): String {
            val values = linkedSetOf<String>()
            if (primary.isNotBlank()) values += primary
            values += extras
            return values.joinToString(" ").trim()
        }
    }
}

private fun NormalizedNode.hasInteractiveSemantics(): Boolean = clickable || editable

private fun NormalizedNode.hasReadableLabel(): Boolean =
    text.isNotBlank() || contentDescription.isNotBlank()

private fun NormalizedNode.sanitizeForViewport(viewport: NormalizedBounds): NormalizedNode? {
    val clipped = bounds.intersect(viewport) ?: return null
    if (!clipped.isRectValid()) return null

    val cleaned = copy(bounds = clipped)
    if (!cleaned.hasInteractiveSemantics() && !cleaned.hasReadableLabel()) {
        return null
    }

    val isContainerClass = className.endsWith("Layout") ||
        className.endsWith("ViewGroup") ||
        className.endsWith("FrameLayout")
    if (isContainerClass && !cleaned.hasInteractiveSemantics() && !cleaned.hasReadableLabel()) {
        return null
    }

    return cleaned
}

private fun NormalizedBounds.isRectValid(): Boolean = left < right && top < bottom

private fun NormalizedBounds.intersect(other: NormalizedBounds): NormalizedBounds? {
    val clippedLeft = maxOf(left, other.left)
    val clippedTop = maxOf(top, other.top)
    val clippedRight = minOf(right, other.right)
    val clippedBottom = minOf(bottom, other.bottom)
    return if (clippedLeft < clippedRight && clippedTop < clippedBottom) {
        NormalizedBounds(clippedLeft, clippedTop, clippedRight, clippedBottom)
    } else {
        null
    }
}
