package com.dynamicisland.motion

import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K

/**
 * محرك الحركة: مواصفات Spring مشتقة من إعدادات المستخدم (النوع + السرعة)،
 * ومحرك واحد لكل خاصية يحافظ على السرعة الحالية عند تغيّر الهدف (انتقالات بلا تقطيع).
 */
class MotionEngine(private val s: IslandSettings) {
    class Spec(val stiffness: Float, val damping: Float)

    private fun speedSq(): Float {
        val sp = s.int(K.ANIM_SPEED).coerceIn(40, 250) / 100f
        return sp * sp
    }

    /** حركة التحوّل (الحجم/الانحناء). 0 مرِن، 1 ناعم، 2 سريع. */
    fun morph(): Spec = when (s.int(K.MOTION)) {
        1 -> Spec(320f * speedSq(), 0.92f)
        2 -> Spec(700f * speedSq(), 0.78f)
        else -> Spec(420f * speedSq(), 0.62f)
    }

    /** الظهور/الاختفاء: بدون ارتداد. */
    fun fade(): Spec = Spec(520f * speedSq(), 1f)

    fun mix(): Spec = Spec(380f * speedSq(), 1f)

    fun press(): Spec = Spec(900f, 0.55f)

    fun go(a: SpringAnimation, to: Float, spec: Spec) {
        val force = a.spring ?: SpringForce(to).also { a.setSpring(it) }
        force.setStiffness(spec.stiffness.coerceAtLeast(1f)).setDampingRatio(spec.damping)
        a.animateToFinalPosition(to)
    }
}
