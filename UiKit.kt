package com.dynamicisland.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dynamicisland.R
import com.dynamicisland.core.EventEngine
import com.dynamicisland.core.EventType
import com.dynamicisland.core.IslandEvent
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.dp
import com.dynamicisland.data.dpf
import com.dynamicisland.service.IslandService
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

object Ui {
    val BG = 0xFF0B0C0F.toInt()
    val CARD = 0xFF16181D.toInt()
    val TEXT = 0xFFF2F4F8.toInt()
    val SUB = 0xFF9AA3B2.toInt()
    val OK = 0xFF66BB6A.toInt()
    val BAD = 0xFFEF5350.toInt()
    val BG_COLORS = intArrayOf(
        0xFF000000.toInt(), 0xFF101418.toInt(), 0xFF1A1033.toInt(), 0xFF0D2B3E.toInt(),
        0xFF1B2E1F.toInt(), 0xFF3A0F18.toInt(), 0xFFFFFFFF.toInt()
    )
    val ACCENTS = intArrayOf(
        0xFF4FC3F7.toInt(), 0xFF00E676.toInt(), 0xFFFF5252.toInt(), 0xFFFFB74D.toInt(),
        0xFFBA68C8.toInt(), 0xFFF06292.toInt(), 0xFFFFFFFF.toInt()
    )

    val TYPE_LABELS = mapOf(
        EventType.CALL to "المكالمات",
        EventType.TIMER to "المؤقت",
        EventType.MEDIA to "الموسيقى",
        EventType.CHARGING to "الشحن والبطارية",
        EventType.NOTIFICATION to "الإشعارات",
        EventType.CONNECTIVITY to "الاتصال",
        EventType.AUDIO to "الميكروفون",
        EventType.PROGRESS to "التنزيلات والملاحة والتحديثات",
        EventType.NETWORK to "سرعة الشبكة"
    )

    /** معاينة صريحة لضبط الشكل والموضع (ليست بيانات مزيفة: تحمل تسمية "معاينة"). */
    fun preview(ctx: Context) {
        if (!IslandService.running) IslandService.start(ctx)
        EventEngine.post(
            IslandEvent(
                key = "preview", type = EventType.NOTIFICATION, title = "معاينة الجزيرة",
                subtitle = "هكذا تظهر التنبيهات بإعداداتك الحالية", iconRes = R.drawable.ic_bell,
                autoExpand = true, durationMs = 3500
            )
        )
    }
}

fun Context.shape(color: Int, radiusDp: Int): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp.toFloat())
    }

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun LinearLayout.lp(top: Int = 0, w: Int = MATCH, h: Int = WRAP) =
    LinearLayout.LayoutParams(w, h).apply { topMargin = context.dp(top) }

fun LinearLayout.label(t: String, sp: Float = 15f, color: Int = Ui.TEXT, bold: Boolean = false, top: Int = 0): TextView {
    val v = TextView(context).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    addView(v, lp(top))
    return v
}

fun LinearLayout.switchRow(title: String, desc: String?, checked: Boolean, top: Int = 12, onChange: (Boolean) -> Unit) {
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    col.label(title, 15f)
    if (desc != null) col.label(desc, 12f, Ui.SUB, top = 2)
    row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
    val sw = MaterialSwitch(context).apply {
        isChecked = checked
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }
    row.addView(sw)
    addView(row, lp(top))
}

fun LinearLayout.sliderRow(title: String, s: IslandSettings, key: String, from: Int, to: Int, unit: String, top: Int = 12) {
    val cur = s.int(key).coerceIn(from, to)
    val head = label("$title: $cur$unit", 14f, top = top)
    val sl = Slider(context).apply {
        valueFrom = from.toFloat()
        valueTo = to.toFloat()
        stepSize = 1f
        value = cur.toFloat()
    }
    sl.addOnChangeListener { _, v, fromUser ->
        if (fromUser) {
            head.text = "$title: ${v.toInt()}$unit"
            s.put(key, v.toInt())
        }
    }
    addView(sl, lp(0))
}

fun LinearLayout.swatches(title: String, s: IslandSettings, key: String, colors: IntArray, top: Int = 12) {
    label(title, 14f, top = top)
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    colors.forEach { c -> row.addView(swatch(c, s, key, colors, row)) }
    addView(row, lp(8))
}

private fun swatch(c: Int, s: IslandSettings, key: String, colors: IntArray, row: LinearLayout): View {
    val ctx = row.context
    return View(ctx).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(c)
            setStroke(ctx.dp(if (s.int(key) == c) 3 else 1), if (s.int(key) == c) Color.WHITE else 0x55FFFFFF)
        }
        layoutParams = LinearLayout.LayoutParams(ctx.dp(38), ctx.dp(38)).apply { marginEnd = ctx.dp(10) }
        setOnClickListener {
            s.put(key, c)
            row.removeAllViews()
            colors.forEach { cc -> row.addView(swatch(cc, s, key, colors, row)) }
        }
    }
}

fun LinearLayout.btn(text: String, top: Int = 10, onClick: () -> Unit): MaterialButton {
    val b = MaterialButton(context).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }
    addView(b, lp(top))
    return b
}

fun LinearLayout.statusRow(title: String, ok: Boolean, desc: String, action: String?, top: Int = 12, onAction: () -> Unit) {
    label((if (ok) "✓  " else "✗  ") + title, 15f, if (ok) Ui.OK else Ui.BAD, true, top)
    label(desc, 12f, Ui.SUB, top = 2)
    if (!ok && action != null) btn(action, 6, onAction)
}

/** هيكل صفحة داكنة: ScrollView + عمود + بطاقات. */
class Page(val act: Activity) {
    val root = ScrollView(act)
    val col = LinearLayout(act)

    init {
        root.setBackgroundColor(Ui.BG)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(act.dp(16), act.dp(16), act.dp(16), act.dp(40))
        root.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
    }

    fun clear() = col.removeAllViews()

    fun title(t: String, sub: String? = null) {
        col.label(t, 26f, Ui.TEXT, true)
        if (sub != null) col.label(sub, 13f, Ui.SUB, top = 4)
    }

    fun card(heading: String? = null, build: LinearLayout.() -> Unit) {
        val c = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = act.shape(Ui.CARD, 20)
            setPadding(act.dp(16), act.dp(14), act.dp(16), act.dp(16))
        }
        if (heading != null) c.label(heading, 13f, Ui.SUB, true)
        c.build()
        col.addView(c, col.lp(14))
    }
}
