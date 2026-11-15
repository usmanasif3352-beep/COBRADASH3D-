package com.example.game.renderer

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GpuMesh(
    vertices: FloatArray,
    indices: ShortArray,
    private val drawMode: Int = GLES30.GL_TRIANGLES
) {
    private val vaoId: Int
    private val vboId: Int
    private val iboId: Int
    private val indexCount: Int = indices.size

    init {
        val vao = IntArray(1)
        val bufs = IntArray(2)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(2, bufs, 0)
        vaoId = vao[0]
        vboId = bufs[0]
        iboId = bufs[1]

        val vBuf: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }

        val iBuf: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(indices); position(0) }

        GLES30.glBindVertexArray(vaoId)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, iBuf, GLES30.GL_STATIC_DRAW)

        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)

        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawElements(drawMode, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId), 0)
        GLES30.glDeleteBuffers(2, intArrayOf(vboId, iboId), 0)
    }
}

object MeshFactory {

    fun createSphere(slices: Int = 16, stacks: Int = 12): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * PI.toFloat()
            val y = cos(phi)
            val r = sin(phi)
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                val x = r * cos(theta)
                val z = r * sin(theta)
                verts.addAll(listOf(x, y, z, x, y, z))
            }
        }
        val row = slices + 1
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = (i * row + j).toShort()
                val b = ((i + 1) * row + j).toShort()
                val c = ((i + 1) * row + j + 1).toShort()
                val d = (i * row + j + 1).toShort()
                idx.addAll(listOf(a, b, d, b, c, d))
            }
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray())
    }

    fun createCobraHead(slices: Int = 18, stacks: Int = 12): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks
            val phi = v * PI.toFloat()
            val baseY = cos(phi)
            val r = sin(phi)
            for (j in 0..slices) {
                val u = j.toFloat() / slices
                val theta = u * 2f * PI.toFloat()
                val rawX = r * cos(theta)
                val rawZ = r * sin(theta)
                // Sculpted cobra hood: wider flared sides near back, tapered forward snout (+X)
                val hoodFlare = if (rawX < 0.25f) 1.48f else 0.92f
                val x = rawX * 1.38f
                val y = baseY * 0.72f + (if (rawX > 0.3f && baseY > 0f) 0.14f else 0f)
                val z = rawZ * hoodFlare
                val len = sqrt(x * x + y * y + z * z).coerceAtLeast(0.001f)
                verts.addAll(listOf(x, y, z, x / len, y / len, z / len))
            }
        }
        val row = slices + 1
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = (i * row + j).toShort()
                val b = ((i + 1) * row + j).toShort()
                val c = ((i + 1) * row + j + 1).toShort()
                val d = (i * row + j + 1).toShort()
                idx.addAll(listOf(a, b, d, b, c, d))
            }
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray())
    }

    fun createCylinder(segments: Int = 18): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        // Side wall
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            val x = cos(theta)
            val z = sin(theta)
            verts.addAll(listOf(x, 0f, z, x, 0f, z))
            verts.addAll(listOf(x, 1f, z, x, 0f, z))
        }
        for (i in 0 until segments) {
            val b0 = (i * 2).toShort()
            val t0 = (i * 2 + 1).toShort()
            val b1 = ((i + 1) * 2).toShort()
            val t1 = ((i + 1) * 2 + 1).toShort()
            idx.addAll(listOf(b0, t0, b1, t0, t1, b1))
        }
        // Top cap
        val topCenter = (verts.size / 6).toShort()
        verts.addAll(listOf(0f, 1f, 0f, 0f, 1f, 0f))
        val ringStart = (verts.size / 6)
        for (i in 0..segments) {
            val theta = (i.toFloat() / segments) * 2f * PI.toFloat()
            verts.addAll(listOf(cos(theta), 1f, sin(theta), 0f, 1f, 0f))
        }
        for (i in 0 until segments) {
            idx.addAll(listOf(topCenter, (ringStart + i).toShort(), (ringStart + i + 1).toShort()))
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray())
    }

    fun createTorusRing(majorRadius: Float = 1f, minorRadius: Float = 0.06f, segs: Int = 48, tubeSegs: Int = 8): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        for (i in 0..segs) {
            val u = (i.toFloat() / segs) * 2f * PI.toFloat()
            val cu = cos(u)
            val su = sin(u)
            for (j in 0..tubeSegs) {
                val v = (j.toFloat() / tubeSegs) * 2f * PI.toFloat()
                val cv = cos(v)
                val sv = sin(v)
                val x = (majorRadius + minorRadius * cv) * cu
                val y = minorRadius * sv
                val z = (majorRadius + minorRadius * cv) * su
                val nx = cv * cu
                val ny = sv
                val nz = cv * su
                verts.addAll(listOf(x, y, z, nx, ny, nz))
            }
        }
        val row = tubeSegs + 1
        for (i in 0 until segs) {
            for (j in 0 until tubeSegs) {
                val a = (i * row + j).toShort()
                val b = ((i + 1) * row + j).toShort()
                val c = ((i + 1) * row + j + 1).toShort()
                val d = (i * row + j + 1).toShort()
                idx.addAll(listOf(a, b, d, b, c, d))
            }
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray())
    }

    fun createOctahedron(): GpuMesh {
        val p = listOf(
            floatArrayOf(0f, 1.2f, 0f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 0f, -1f),
            floatArrayOf(0f, -1.2f, 0f)
        )
        val faces = listOf(
            intArrayOf(0, 1, 2), intArrayOf(0, 2, 3), intArrayOf(0, 3, 4), intArrayOf(0, 4, 1),
            intArrayOf(5, 2, 1), intArrayOf(5, 3, 2), intArrayOf(5, 4, 3), intArrayOf(5, 1, 4)
        )
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        var c: Short = 0
        for (f in faces) {
            val a = p[f[0]]
            val b = p[f[1]]
            val d = p[f[2]]
            val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
            val vx = d[0] - a[0]; val vy = d[1] - a[1]; val vz = d[2] - a[2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
            nx /= len; ny /= len; nz /= len
            for (pt in listOf(a, b, d)) {
                verts.addAll(listOf(pt[0], pt[1], pt[2], nx, ny, nz))
                idx.add(c++)
            }
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray())
    }

    fun createArenaDisc(segments: Int = 64): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        verts.addAll(listOf(0f, 0f, 0f, 0f, 1f, 0f))
        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * 2f * PI.toFloat()
            verts.addAll(listOf(cos(a), 0f, sin(a), 0f, 1f, 0f))
        }
        for (i in 1..segments) {
            idx.addAll(listOf(0.toShort(), i.toShort(), (i + 1).toShort()))
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray())
    }

    fun createArenaGridLines(radius: Float, step: Float = 6f): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        var count: Short = 0
        var p = -radius
        while (p <= radius) {
            val span = sqrt((radius * radius - p * p).coerceAtLeast(0f))
            if (span > 0.5f) {
                verts.addAll(listOf(-span, 0.03f, p, 0f, 1f, 0f))
                verts.addAll(listOf(span, 0.03f, p, 0f, 1f, 0f))
                idx.add(count++); idx.add(count++)

                verts.addAll(listOf(p, 0.03f, -span, 0f, 1f, 0f))
                verts.addAll(listOf(p, 0.03f, span, 0f, 1f, 0f))
                idx.add(count++); idx.add(count++)
            }
            p += step
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray(), GLES30.GL_LINES)
    }

    fun createCrown(): GpuMesh {
        val verts = ArrayList<Float>()
        val idx = ArrayList<Short>()
        val spikes = 5
        var c: Short = 0
        for (i in 0 until spikes) {
            val a0 = (i.toFloat() / spikes) * 2f * PI.toFloat()
            val a1 = ((i + 0.5f) / spikes) * 2f * PI.toFloat()
            val a2 = ((i + 1f) / spikes) * 2f * PI.toFloat()
            val p0 = floatArrayOf(cos(a0) * 0.55f, 0f, sin(a0) * 0.55f)
            val p1 = floatArrayOf(cos(a1) * 0.62f, 0.65f, sin(a1) * 0.62f)
            val p2 = floatArrayOf(cos(a2) * 0.55f, 0f, sin(a2) * 0.55f)
            val nx = cos(a1); val ny = 0.35f; val nz = sin(a1)
            for (pt in listOf(p0, p1, p2)) {
                verts.addAll(listOf(pt[0], pt[1], pt[2], nx, ny, nz))
                idx.add(c++)
            }
        }
        return GpuMesh(verts.toFloatArray(), idx.toShortArray())
    }

    fun createCube(halfExtent: Float = 0.5f): GpuMesh {
        return GpuMesh(
            CubeGeometryData.buildVertices(halfExtent),
            CubeGeometryData.buildIndices()
        )
    }
}
