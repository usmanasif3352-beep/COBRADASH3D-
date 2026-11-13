package com.example.game.engine

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable representation of a single 3D Snake body segment prepared for OpenGL ES coordinate mapping.
 *
 * Uses CobraDash3D's right-handed Y-up 3D world convention:
 * - X / Z: Arena horizontal ground plane
 * - Y: Vertical elevation above the arena floor
 */
data class SnakeBodySegment(
    val index: Int,
    val position: Vec3,
    val direction: Vec3 = Vec3(1f, 0f, 0f),
    val yawRad: Float = 0f,
    val radius: Float = 0.65f,
    val distanceFromHead: Float = 0f
) {
    /**
     * Converts this segment into a 3-element [x, y, z] float array for direct 3D matrix translation.
     */
    fun toWorldArray(): FloatArray = floatArrayOf(position.x, position.y, position.z)

    /**
     * Yaw orientation in degrees aligned with OpenGL `Matrix.rotateM(..., yawDeg, 0f, 1f, 0f)`.
     */
    fun yawDegrees(): Float = -yawRad * (180f / PI.toFloat())
}

/**
 * Represents a snake head displacement / velocity vector in 3D space.
 */
data class HeadMovementVector(
    val dx: Float,
    val dy: Float = 0f,
    val dz: Float
) {
    fun horizontalMagnitude(): Float = hypot(dx, dz)

    fun magnitude3D(): Float = sqrt(dx * dx + dy * dy + dz * dz)

    fun normalized(): Vec3 {
        val mag = magnitude3D()
        return if (mag > EPSILON) {
            Vec3(dx / mag, dy / mag, dz / mag)
        } else {
            Vec3(1f, 0f, 0f)
        }
    }

    fun headingYawRad(): Float = atan2(dz, dx)

    companion object {
        private const val EPSILON = 1e-5f

        fun fromSpeedAndYaw(speed: Float, yawRad: Float, dtSec: Float): HeadMovementVector {
            val step = speed * dtSec
            return HeadMovementVector(
                dx = cos(yawRad) * step,
                dy = 0f,
                dz = sin(yawRad) * step
            )
        }
    }
}

/**
 * Kinematic model that computes constrained 3D body segment positions, orientations,
 * and radii driven by incoming [HeadMovementVector] deltas.
 */
class SnakeSegmentKinematicsModel(
    initialHeadPosition: Vec3 = Vec3(0f, 0.56f, 0f),
    initialYawRad: Float = 0f,
    initialSegmentCount: Int = 14,
    initialHeadRadius: Float = 0.72f,
    private val spacingMultiplier: Float = 1.18f
) {
    var headPosition: Vec3 = initialHeadPosition.copy()
        private set

    var headYawRad: Float = initialYawRad
        private set

    var headRadius: Float = initialHeadRadius
        private set

    var targetSegmentCount: Int = initialSegmentCount.coerceAtLeast(1)
        private set

    private val _segments = mutableListOf<SnakeBodySegment>()
    val segments: List<SnakeBodySegment> get() = _segments

    init {
        rebuildInitialChain()
    }

    fun segmentSpacing(): Float = (headRadius * spacingMultiplier).coerceAtLeast(0.15f)

    /**
     * Resets the head and lays out all body segments smoothly behind [headYawRad].
     */
    fun reset(
        headPos: Vec3 = Vec3(0f, headRadius * 0.78f, 0f),
        yawRad: Float = 0f,
        segmentCount: Int = targetSegmentCount,
        newHeadRadius: Float = headRadius
    ) {
        headPosition = headPos.copy()
        headYawRad = yawRad
        headRadius = newHeadRadius.coerceAtLeast(0.1f)
        targetSegmentCount = segmentCount.coerceAtLeast(1)
        rebuildInitialChain()
    }

    /**
     * Dynamically adjusts segment count and radius (e.g., as orbs are collected).
     */
    fun updateBodyMetrics(segmentCount: Int, newHeadRadius: Float) {
        val clampedCount = segmentCount.coerceAtLeast(1)
        headRadius = newHeadRadius.coerceAtLeast(0.1f)
        if (clampedCount > _segments.size) {
            val spacing = segmentSpacing()
            val last = _segments.lastOrNull()
            var prevPos = last?.position ?: headPosition
            var dir = last?.direction ?: Vec3(cos(headYawRad), 0f, sin(headYawRad))
            for (idx in _segments.size until clampedCount) {
                val nextPos = Vec3(
                    x = prevPos.x - dir.x * spacing,
                    y = computeSegmentY(idx, clampedCount),
                    z = prevPos.z - dir.z * spacing
                )
                _segments.add(
                    SnakeBodySegment(
                        index = idx,
                        position = nextPos,
                        direction = dir.copy(),
                        yawRad = atan2(dir.z, dir.x),
                        radius = computeSegmentRadius(idx, clampedCount),
                        distanceFromHead = (idx + 1) * spacing
                    )
                )
                prevPos = nextPos
            }
        } else if (clampedCount < _segments.size) {
            while (_segments.size > clampedCount) {
                _segments.removeAt(_segments.lastIndex)
            }
        }
        targetSegmentCount = clampedCount
        refreshRadiiAndElevations()
    }

    /**
     * Advances the head by [movement] and recalculates every body segment position
     * using inverse-kinematic follow constraints ready for 3D coordinate mapping.
     */
    fun advanceBy(movement: HeadMovementVector): List<SnakeBodySegment> {
        val mag = movement.magnitude3D()
        if (mag > MIN_MOVEMENT_EPSILON) {
            headPosition = Vec3(
                x = headPosition.x + movement.dx,
                y = (headPosition.y + movement.dy).coerceAtLeast(headRadius * 0.55f),
                z = headPosition.z + movement.dz
            )
            if (movement.horizontalMagnitude() > MIN_MOVEMENT_EPSILON) {
                headYawRad = movement.headingYawRad()
            }
        }

        val updated = calculateSegments(
            headPos = headPosition,
            headYawRad = headYawRad,
            previousSegments = _segments,
            targetCount = targetSegmentCount,
            headRadius = headRadius,
            spacing = segmentSpacing()
        )
        _segments.clear()
        _segments.addAll(updated)
        return segments
    }

    /**
     * Convenience helper that maps segments into pure [Vec3] coordinates matching [SnakeEntity.segments].
     */
    fun toVec3Coordinates(): List<Vec3> = _segments.map { it.position.copy() }

    /**
     * Stateless kinematic solver computing ordered [SnakeBodySegment] positions from a leading point.
     */
    fun calculateSegments(
        headPos: Vec3,
        headYawRad: Float,
        previousSegments: List<SnakeBodySegment>,
        targetCount: Int,
        headRadius: Float,
        spacing: Float = (headRadius * spacingMultiplier).coerceAtLeast(0.15f)
    ): List<SnakeBodySegment> {
        val count = targetCount.coerceAtLeast(1)
        val result = ArrayList<SnakeBodySegment>(count)
        var leaderPos = headPos
        var cumulativeDist = 0f

        for (i in 0 until count) {
            val prevSegPos = previousSegments.getOrNull(i)?.position ?: Vec3(
                x = leaderPos.x - cos(headYawRad) * spacing,
                y = computeSegmentY(i, count, headRadius),
                z = leaderPos.z - sin(headYawRad) * spacing
            )

            val toLeaderX = leaderPos.x - prevSegPos.x
            val toLeaderZ = leaderPos.z - prevSegPos.z
            val horizDist = hypot(toLeaderX, toLeaderZ)

            val dirX: Float
            val dirZ: Float
            if (horizDist > MIN_MOVEMENT_EPSILON) {
                dirX = toLeaderX / horizDist
                dirZ = toLeaderZ / horizDist
            } else {
                val fallback = previousSegments.getOrNull(i)?.direction
                if (fallback != null && hypot(fallback.x, fallback.z) > MIN_MOVEMENT_EPSILON) {
                    val fMag = hypot(fallback.x, fallback.z)
                    dirX = fallback.x / fMag
                    dirZ = fallback.z / fMag
                } else {
                    dirX = cos(headYawRad)
                    dirZ = sin(headYawRad)
                }
            }

            // Place current segment exactly `spacing` units behind its leader along the spine vector
            val newX = leaderPos.x - dirX * spacing
            val newZ = leaderPos.z - dirZ * spacing
            val newY = computeSegmentY(i, count, headRadius)
            val pos = Vec3(newX, newY, newZ)
            cumulativeDist += spacing

            result.add(
                SnakeBodySegment(
                    index = i,
                    position = pos,
                    direction = Vec3(dirX, 0f, dirZ),
                    yawRad = atan2(dirZ, dirX),
                    radius = computeSegmentRadius(i, count, headRadius),
                    distanceFromHead = cumulativeDist
                )
            )
            leaderPos = pos
        }
        return result
    }

    private fun rebuildInitialChain() {
        _segments.clear()
        val spacing = segmentSpacing()
        val dirX = cos(headYawRad)
        val dirZ = sin(headYawRad)
        for (i in 0 until targetSegmentCount) {
            val dist = (i + 1) * spacing
            _segments.add(
                SnakeBodySegment(
                    index = i,
                    position = Vec3(
                        x = headPosition.x - dirX * dist,
                        y = computeSegmentY(i, targetSegmentCount),
                        z = headPosition.z - dirZ * dist
                    ),
                    direction = Vec3(dirX, 0f, dirZ),
                    yawRad = headYawRad,
                    radius = computeSegmentRadius(i, targetSegmentCount),
                    distanceFromHead = dist
                )
            )
        }
    }

    private fun refreshRadiiAndElevations() {
        val count = _segments.size.coerceAtLeast(1)
        for (i in _segments.indices) {
            val seg = _segments[i]
            val pos = seg.position.copy(y = computeSegmentY(i, count))
            _segments[i] = seg.copy(
                index = i,
                position = pos,
                radius = computeSegmentRadius(i, count),
                distanceFromHead = (i + 1) * segmentSpacing()
            )
        }
    }

    private fun computeSegmentY(
        index: Int,
        totalCount: Int,
        radiusRef: Float = headRadius
    ): Float {
        val headY = radiusRef * 0.78f
        val taper = 1f - (index.toFloat() / (totalCount + 4)) * 0.28f
        return headY * taper
    }

    private fun computeSegmentRadius(
        index: Int,
        totalCount: Int,
        radiusRef: Float = headRadius
    ): Float {
        val taper = 1f - (index.toFloat() / (totalCount + 4)) * 0.45f
        return radiusRef * 0.88f * taper
    }

    private companion object {
        const val MIN_MOVEMENT_EPSILON = 1e-5f
    }
}
