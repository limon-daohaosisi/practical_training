package com.example.myapplication.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeNormalizationTest {

    @Test
    fun clickable_parent_merges_child_labels_into_parent_bounds() {
        val root = RawNodeSnapshot(
            className = "android.view.ViewGroup",
            bounds = NormalizedBounds(0, 0, 1080, 2400),
            children = listOf(
                RawNodeSnapshot(
                    className = "android.view.ViewGroup",
                    clickable = true,
                    enabled = true,
                    bounds = NormalizedBounds(0, 900, 1080, 1200),
                    children = listOf(
                        RawNodeSnapshot(
                            text = "Flutter 3.29 更新内容",
                            className = "android.widget.TextView",
                            enabled = true,
                            bounds = NormalizedBounds(40, 940, 640, 1000)
                        ),
                        RawNodeSnapshot(
                            contentDescription = "12分钟",
                            className = "android.widget.TextView",
                            enabled = true,
                            bounds = NormalizedBounds(40, 1010, 220, 1060)
                        ),
                        RawNodeSnapshot(
                            text = "Flutter 3.29 更新内容",
                            className = "android.widget.TextView",
                            enabled = true,
                            bounds = NormalizedBounds(40, 940, 640, 1000)
                        )
                    )
                )
            )
        )

        val nodes = NodeNormalizer.normalize(root)

        assertEquals(1, nodes.size)
        assertEquals("Flutter 3.29 更新内容", nodes[0].text)
        assertEquals("12分钟", nodes[0].contentDescription)
        assertTrue(nodes[0].clickable)
        assertFalse(nodes[0].editable)
        assertEquals(NormalizedBounds(0, 900, 1080, 1200), nodes[0].bounds)
    }

    @Test
    fun editable_node_is_preserved_without_focusable_signal() {
        val root = RawNodeSnapshot(
            className = "android.view.ViewGroup",
            bounds = NormalizedBounds(0, 0, 1080, 2400),
            children = listOf(
                RawNodeSnapshot(
                    text = "请输入手机号",
                    className = "android.widget.EditText",
                    editable = true,
                    enabled = true,
                    bounds = NormalizedBounds(48, 320, 1032, 440)
                )
            )
        )

        val nodes = NodeNormalizer.normalize(root)

        assertEquals(1, nodes.size)
        assertEquals("请输入手机号", nodes[0].text)
        assertFalse(nodes[0].clickable)
        assertTrue(nodes[0].editable)
        assertEquals(NormalizedBounds(48, 320, 1032, 440), nodes[0].bounds)
    }

    @Test
    fun drops_invalid_offscreen_and_semantically_empty_nodes() {
        val root = RawNodeSnapshot(
            className = "android.view.ViewGroup",
            bounds = NormalizedBounds(0, 0, 1080, 2400),
            children = listOf(
                RawNodeSnapshot(
                    className = "android.view.ViewGroup",
                    enabled = true,
                    bounds = NormalizedBounds(100, 100, 100, 180)
                ),
                RawNodeSnapshot(
                    text = "屏外节点",
                    className = "android.widget.TextView",
                    enabled = true,
                    bounds = NormalizedBounds(1200, 100, 1400, 180)
                ),
                RawNodeSnapshot(
                    className = "android.widget.FrameLayout",
                    enabled = true,
                    bounds = NormalizedBounds(100, 300, 300, 420)
                ),
                RawNodeSnapshot(
                    text = "可见文本",
                    className = "android.widget.TextView",
                    enabled = true,
                    bounds = NormalizedBounds(120, 500, 320, 560)
                )
            )
        )

        val nodes = NodeNormalizer.normalize(root)

        assertEquals(1, nodes.size)
        assertEquals("可见文本", nodes[0].text)
        assertEquals(NormalizedBounds(120, 500, 320, 560), nodes[0].bounds)
    }
}
