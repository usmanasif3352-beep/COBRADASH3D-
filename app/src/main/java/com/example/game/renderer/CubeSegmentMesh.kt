package com.example.game.renderer

import android.graphics.Color
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.example.game.engine.SnakeBodySegment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Pure geometry specification and interleaved vertex/index data for a unit 3D cube ([-0.5, 0.5]^3)
 * with per-face normals. Intended for rendering 3D snake body segments via OpenGL ES 3.0.
 *
 * Vertex layout (interleaved):
 * - Location 0: `aPosition` (vec3, 3 floats, byte offset 0)
 * - Location 1: `aNormal`   (vec3, 3 floats, byte offset 12)
 */
object CubeGeometryData {
    const val POSITION_COMPONENTS = 3
    const val NORMAL_COMPONENTS = 3
    const val FLOATS_PER_VERTEX = POSITION_COMPONENTS + NORMAL_COMPONENTS
    const val BYTES_PER_FLOAT = 4
    const val BYTES_PER_SHORT = 2
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * BYTES_PER_FLOAT // 24 bytes
    const val POSITION_OFFSET_BYTES = 0
    const val NORMAL_OFFSET_BYTES = POSITION_COMPONENTS * BYTES_PER_FLOAT // 12 bytes
    const val VERTEX_COUNT = 24 // 6 faces * 4 vertices (unique normals per face)
    const val INDEX_COUNT = 36  // 6 faces * 2 triangles * 3 indices

    /**
     * Generates interleaved vertex data: [px, py, pz, nx, ny, nz] * 24 vertices.
     */
    fun buildVertices(halfExtent: Float = 0.5f): FloatArray {
        val h = halfExtent
        return floatArrayOf(
            // Front (+Z)
            -h, -h,  h,  0f,  0f,  1f,
             h, -h,  h,  0f,  0f,  1f,
             h,  h,  h,  0f,  0f,  1f,
            -h,  h,  h,  0f,  0f,  1f,
            // Back (-Z)
             h, -h, -h,  0f,  0f, -1f,
            -h, -h, -h,  0f,  0f, -1f,
            -h,  h, -h,  0f,  0f, -1f,
             h,  h, -h,  0f,  0f, -1f,
            // Left (-X)
            -h, -h, -h, -1f,  0f,  0f,
            -h, -h,  h, -1f,  0f,  0f,
            -h,  h,  h, -1f,  0f,  0f,
            -h,  h, -h, -1f,  0f,  0f,
            // Right (+X)
             h, -h,  h,  1f,  0f,  0f,
             h, -h, -h,  1f,  0f,  0f,
             h,  h, -h,  1f,  0f,  0f,
             h,  h,  h,  1f,  0f,  0f,
            // Top (+Y)
            -h,  h,  h,  0f,  1f,  0f,
             h,  h,  h,  0f,  1f,  0f,
             h,  h, -h,  0f,  1f,  0f,
            -h,  h, -h,  0f,  1f,  0f,
            // Bottom (-Y)
            -h, -h, -h,  0f, -1f,  0f,
             h, -h, -h,  0f, -1f,  0f,
             h, -h,  h,  0f, -1f,  0f,
            -h, -h,  h,  0f, -1f,  0f
        )
    }

    /**
     * Generates CCW triangle indices (6 faces * 2 triangles * 3 indices = 36 shorts).
     */
    fun buildIndices(): ShortArray {
        val indices = ShortArray(INDEX_COUNT)
        for (face in 0 until 6) {
            val baseVertex = face * 4
            val baseIdx = face * 6
            indices[baseIdx] = baseVertex.toShort()
            indices[baseIdx + 1] = (baseVertex + 1).toShort()
            indices[baseIdx + 2] = (baseVertex + 2).toShort()
            indices[baseIdx + 3] = baseVertex.toShort()
            indices[baseIdx + 4] = (baseVertex + 2).toShort()
            indices[baseIdx + 5] = (baseVertex + 3).toShort()
        }
        return indices
    }

    /**
     * Allocates a native-ordered direct [FloatBuffer] for OpenGL VBO upload.
     */
    fun allocateVertexBuffer(vertices: FloatArray = buildVertices()): FloatBuffer {
        return ByteBuffer.allocateDirect(vertices.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
    }

    /**
     * Allocates a native-ordered direct [ShortBuffer] for OpenGL EBO/IBO upload.
     */
    fun allocateIndexBuffer(indices: ShortArray = buildIndices()): ShortBuffer {
        return ByteBuffer.allocateDirect(indices.size * BYTES_PER_SHORT)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }
    }
}

/**
 * Basic OpenGL ES 3.0 shader program designed for rendering lit 3D cubes
 * representing initial snake body segments.
 */
class CubeShaderProgram {
    var programId: Int = 0
        private set

    var uMvpMatrixLoc: Int = -1
        private set
    var uModelMatrixLoc: Int = -1
        private set
    var uColorLoc: Int = -1
        private set
    var uLightDirLoc: Int = -1
        private set

    fun compile() {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_SRC)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC)

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val infoLog = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            throw IllegalStateException("CubeShaderProgram link failed: $infoLog")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        programId = program

        uMvpMatrixLoc = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrixLoc = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        uColorLoc = GLES30.glGetUniformLocation(programId, "uColor")
        uLightDirLoc = GLES30.glGetUniformLocation(programId, "uLightDir")
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    fun setUniforms(
        mvpMatrix: FloatArray,
        modelMatrix: FloatArray,
        colorArgb: Int,
        lightDirX: Float = 0.45f,
        lightDirY: Float = 0.85f,
        lightDirZ: Float = 0.35f
    ) {
        GLES30.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES30.glUniform4f(
            uColorLoc,
            Color.red(colorArgb) / 255f,
            Color.green(colorArgb) / 255f,
            Color.blue(colorArgb) / 255f,
            Color.alpha(colorArgb) / 255f
        )
        GLES30.glUniform3f(uLightDirLoc, lightDirX, lightDirY, lightDirZ)
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compile error: $info")
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Cube shader compile failed: $info")
        }
        return shader
    }

    companion object {
        private const val TAG = "CubeShaderProgram"

        const val VERTEX_SHADER_SRC = """#version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;

            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;

            out vec3 vWorldNormal;

            void main() {
                vWorldNormal = mat3(uModelMatrix) * aNormal;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        const val FRAGMENT_SHADER_SRC = """#version 300 es
            precision mediump float;

            in vec3 vWorldNormal;

            uniform vec4 uColor;
            uniform vec3 uLightDir;

            out vec4 fragColor;

            void main() {
                vec3 normal = normalize(vWorldNormal);
                vec3 light = normalize(uLightDir);
                float diffuse = max(dot(normal, light), 0.0);
                float shade = 0.35 + diffuse * 0.65;
                fragColor = vec4(uColor.rgb * shade, uColor.a);
            }
        """
    }
}

/**
 * OpenGL ES 3.0 VAO + VBO + EBO vertex buffer structure for indexed 3D cube rendering.
 */
class CubeVertexBuffer(halfExtent: Float = 0.5f) {
    val vaoId: Int
    val vboId: Int
    val eboId: Int
    val indexCount: Int = CubeGeometryData.INDEX_COUNT

    init {
        val vaos = IntArray(1)
        val vbos = IntArray(2)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(2, vbos, 0)

        vaoId = vaos[0]
        vboId = vbos[0]
        eboId = vbos[1]

        val vertexBuffer = CubeGeometryData.allocateVertexBuffer(CubeGeometryData.buildVertices(halfExtent))
        val indexBuffer = CubeGeometryData.allocateIndexBuffer()

        GLES30.glBindVertexArray(vaoId)

        // Upload interleaved position + normal VBO
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            CubeGeometryData.VERTEX_COUNT * CubeGeometryData.STRIDE_BYTES,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Upload cube triangle indices into EBO
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indexCount * CubeGeometryData.BYTES_PER_SHORT,
            indexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Attribute 0: aPosition (vec3)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            CubeGeometryData.POSITION_COMPONENTS,
            GLES30.GL_FLOAT,
            false,
            CubeGeometryData.STRIDE_BYTES,
            CubeGeometryData.POSITION_OFFSET_BYTES
        )

        // Attribute 1: aNormal (vec3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            CubeGeometryData.NORMAL_COMPONENTS,
            GLES30.GL_FLOAT,
            false,
            CubeGeometryData.STRIDE_BYTES,
            CubeGeometryData.NORMAL_OFFSET_BYTES
        )

        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    /**
     * Convenience helper that maps a list of [SnakeBodySegment]s into 3D model transforms
     * and draws each segment as an oriented, scaled 3D cube.
     */
    fun drawSnakeSegments(
        shader: CubeShaderProgram,
        segments: List<SnakeBodySegment>,
        vpMatrix: FloatArray,
        primaryColorArgb: Int,
        secondaryColorArgb: Int
    ) {
        val modelMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        shader.use()
        GLES30.glBindVertexArray(vaoId)

        for (seg in segments) {
            val scale = seg.radius * 1.85f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, seg.position.x, seg.position.y, seg.position.z)
            Matrix.rotateM(modelMatrix, 0, seg.yawDegrees(), 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, scale, scale * 0.9f, scale)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

            val segColor = if (seg.index % 2 == 0) primaryColorArgb else secondaryColorArgb
            shader.setUniforms(mvpMatrix, modelMatrix, segColor)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        }

        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
        GLES30.glDeleteBuffers(2, intArrayOf(vboId, eboId), 0)
    }
}
