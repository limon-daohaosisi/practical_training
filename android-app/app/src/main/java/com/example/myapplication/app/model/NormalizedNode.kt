package com.example.myapplication.app.model

data class NormalizedNode(
    val text: String,
    val contentDescription: String,
    val className: String,
    val clickable: Boolean,
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
    /** X-axis scale factor: image pixels per screen pixel */
    val scaleX: Float get() = if (screenWidth > 0) imageWidth.toFloat() / screenWidth else 1f
    /** Y-axis scale factor: image pixels per screen pixel */
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

/** Map a point from screen coordinate space to image coordinate space. */
fun screenToImage(screenPx: Int, screenDim: Int, imageDim: Int): Int =
    if (screenDim > 0) (screenPx.toFloat() / screenDim * imageDim).toInt() else screenPx

/** Map a point from image coordinate space to screen coordinate space. */
fun imageToScreen(imagePx: Int, imageDim: Int, screenDim: Int): Int =
    if (imageDim > 0) (imagePx.toFloat() / imageDim * screenDim).toInt() else imagePx

/** Map a [NormalizedBounds] from screen space to image space. */
fun NormalizedBounds.toImageSpace(result: CaptureResult): NormalizedBounds = NormalizedBounds(
    left = screenToImage(left, result.screenWidth, result.imageWidth),
    top = screenToImage(top, result.screenHeight, result.imageHeight),
    right = screenToImage(right, result.screenWidth, result.imageWidth),
    bottom = screenToImage(bottom, result.screenHeight, result.imageHeight)
)
