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
    val className: String,
    val nodes: List<NormalizedNode>,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val screenshotBytes: ByteArray? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CaptureResult) return false
        return packageName == other.packageName &&
            className == other.className &&
            nodes == other.nodes &&
            screenWidth == other.screenWidth &&
            screenHeight == other.screenHeight &&
            imageWidth == other.imageWidth &&
            imageHeight == other.imageHeight &&
            screenshotBytes.contentEquals(other.screenshotBytes)
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + className.hashCode()
        result = 31 * result + nodes.hashCode()
        result = 31 * result + (screenshotBytes?.contentHashCode() ?: 0)
        result = 31 * result + screenWidth
        result = 31 * result + screenHeight
        result = 31 * result + imageWidth
        result = 31 * result + imageHeight
        return result
    }
}
