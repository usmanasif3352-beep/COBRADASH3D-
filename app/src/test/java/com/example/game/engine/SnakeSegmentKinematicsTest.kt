package com.example.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class SnakeSegmentKinematicsTest {

    @Test
    fun initialChain_positionsSegmentsBehindHeadAlongYawAndTapersRadius() {
        val model = SnakeSegmentKinematicsModel(
            initialHeadPosition = Vec3(0f, 0.56f, 0f),
            initialYawRad = 0f,
            initialSegmentCount = 10,
            initialHeadRadius = 0.8f
        )

        val segs = model.segments
        assertEquals(10, segs.size)

        val expectedSpacing = model.segmentSpacing()
        assertEquals(-expectedSpacing, segs[0].position.x, 0.001f)
        assertEquals(0f, segs[0].position.z, 0.001f)
        assertTrue(segs[0].radius > segs.last().radius)
        assertTrue(segs[0].position.y > segs.last().position.y)
    }

    @Test
    fun advanceByHeadMovementVector_preservesSpacingAndFollowsCurvedTurn() {
        val model = SnakeSegmentKinematicsModel(
            initialHeadPosition = Vec3(0f, 0.56f, 0f),
            initialYawRad = 0f,
            initialSegmentCount = 12,
            initialHeadRadius = 0.72f
        )
        val spacing = model.segmentSpacing()

        // Move straight along +X then steer 90 degrees onto +Z
        repeat(5) {
            model.advanceBy(HeadMovementVector(dx = 0.4f, dy = 0f, dz = 0f))
        }
        repeat(8) {
            model.advanceBy(HeadMovementVector(dx = 0f, dy = 0f, dz = 0.45f))
        }

        val segs = model.segments
        assertEquals(12, segs.size)

        // First segment must remain exactly `spacing` units from the current head position
        val dHead = hypot(
            model.headPosition.x - segs[0].position.x,
            model.headPosition.z - segs[0].position.z
        )
        assertEquals(spacing, dHead, 0.002f)

        // Every adjacent pair of body segments preserves exact spacing constraint
        for (i in 1 until segs.size) {
            val d = hypot(
                segs[i - 1].position.x - segs[i].position.x,
                segs[i - 1].position.z - segs[i].position.z
            )
            assertEquals("Segment $i spacing drifted", spacing, d, 0.002f)
        }

        // Front segment direction should point along +Z turn, while tail retains earlier turn curve
        assertTrue(segs[0].direction.z > 0.7f)
        assertEquals(PI.toFloat() / 2f, model.headYawRad, 0.001f)
    }

    @Test
    fun updateBodyMetricsAndZeroMovement_handlesGrowthAndStable3DCoordinates() {
        val model = SnakeSegmentKinematicsModel(initialSegmentCount = 6)
        model.updateBodyMetrics(segmentCount = 14, newHeadRadius = 0.9f)
        assertEquals(14, model.segments.size)

        val before = model.toVec3Coordinates()
        model.advanceBy(HeadMovementVector(dx = 0f, dy = 0f, dz = 0f))
        val after = model.toVec3Coordinates()

        assertEquals(before.size, after.size)
        val worldCoords = model.segments[0].toWorldArray()
        assertEquals(3, worldCoords.size)
        assertEquals(after[0].x, worldCoords[0], 0.0001f)
        assertEquals(after[0].y, worldCoords[1], 0.0001f)
        assertEquals(after[0].z, worldCoords[2], 0.0001f)
    }
}
