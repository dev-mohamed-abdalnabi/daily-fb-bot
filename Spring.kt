package com.abdalnaby.dynamicisland.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Damped harmonic oscillator (mass = 1) integrated with semi-implicit Euler and
 * fixed 4 ms sub-steps so it stays stable regardless of frame rate.
 *
 * stiffness    = k  (N/m), higher = faster
 * dampingRatio = ζ, < 1 bouncy, 1 critically damped, > 1 sluggish
 */
class Spring(
    stiffness: Float = 380f,
    dampingRatio: Float = 0.72f,
    initial: Float = 0f,
    var restThreshold: Float = 0.5f,
) {
    var stiffness: Float = clampStiffness(stiffness)
        private set
    var dampingRatio: Float = clampDamping(dampingRatio)
        private set

    var position: Float = initial
        private set
    var velocity: Float = 0f
        private set
    var target: Float = initial
        private set

    val isAtRest: Boolean get() = position == target && velocity == 0f

    fun configure(stiffness: Float, dampingRatio: Float) {
        this.stiffness = clampStiffness(stiffness)
        this.dampingRatio = clampDamping(dampingRatio)
    }

    fun animateTo(value: Float) {
        target = value
    }

    fun snapTo(value: Float) {
        position = value
        target = value
        velocity = 0f
    }

    /** Advances the simulation. Returns true while the spring is still moving. */
    fun step(dtSeconds: Float): Boolean {
        if (isAtRest) return false
        var remaining = dtSeconds.coerceIn(0f, MAX_FRAME)
        val damping = 2f * dampingRatio * sqrt(stiffness)
        while (remaining > 0f) {
            val h = if (remaining < MAX_SUBSTEP) remaining else MAX_SUBSTEP
            val accel = -stiffness * (position - target) - damping * velocity
            velocity += accel * h
            position += velocity * h
            remaining -= h
        }
        if (abs(position - target) < restThreshold && abs(velocity) < restThreshold * 10f) {
            position = target
            velocity = 0f
            return false
        }
        return true
    }

    companion object {
        const val MAX_SUBSTEP = 0.004f
        /** Frames longer than this (e.g. after the screen was off) are clamped to avoid jumps. */
        const val MAX_FRAME = 0.064f
        const val MIN_STIFFNESS = 10f
        const val MAX_STIFFNESS = 3000f

        private fun clampStiffness(v: Float) = v.coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)
        private fun clampDamping(v: Float) = v.coerceIn(0.05f, 3f)
    }
}
