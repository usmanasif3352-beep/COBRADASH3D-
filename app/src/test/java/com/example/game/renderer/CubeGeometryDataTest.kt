package com.example.game.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeGeometryDataTest {

    @Test
    fun buildVerticesAndIndices_matchInterleavedOpenGlEs3Layout() {
        val vertices = CubeGeometryData.buildVertices(halfExtent = 0.5f)
        val indices = CubeGeometryData.buildIndices()

        assertEquals(CubeGeometryData.VERTEX_COUNT * CubeGeometryData.FLOATS_PER_VERTEX, vertices.size)
        assertEquals(24 * 6, vertices.size)
        assertEquals(CubeGeometryData.INDEX_COUNT, indices.size)
        assertEquals(36, indices.size)

        // All indices must reference valid vertices [0..23]
        assertTrue(indices.all { it in 0 until CubeGeometryData.VERTEX_COUNT })

        // Verify front face normal (+Z) and direct buffer capacities
        assertEquals(0f, vertices[3], 0.0001f)
        assertEquals(0f, vertices[4], 0.0001f)
        assertEquals(1f, vertices[5], 0.0001f)

        val vBuf = CubeGeometryData.allocateVertexBuffer(vertices)
        val iBuf = CubeGeometryData.allocateIndexBuffer(indices)
        assertEquals(vertices.size, vBuf.remaining())
        assertEquals(indices.size, iBuf.remaining())
    }

    @Test
    fun shaderSources_declareOpenGlEs300AndExpectedAttributes() {
        assertTrue(CubeShaderProgram.VERTEX_SHADER_SRC.contains("#version 300 es"))
        assertTrue(CubeShaderProgram.VERTEX_SHADER_SRC.contains("layout(location = 0) in vec3 aPosition"))
        assertTrue(CubeShaderProgram.VERTEX_SHADER_SRC.contains("layout(location = 1) in vec3 aNormal"))
        assertTrue(CubeShaderProgram.FRAGMENT_SHADER_SRC.contains("#version 300 es"))
        assertTrue(CubeShaderProgram.FRAGMENT_SHADER_SRC.contains("out vec4 fragColor"))
    }
}
