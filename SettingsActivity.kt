package com.dynamicisland.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dynamicisland.R
import com.dynamicisland.core.EventType
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import com.dynamicisland.data.PRESETS

class SettingsActivity : AppCompatActivity() {
    private lateinit var page: Page
    private val s by lazy { IslandSettings.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = Page(this)
        setContentView(page.root)
        render()
    }

    private fun render() {
        page.clear()
        page.title("التخصيص", "كل تغيير يُحفظ ويُطبَّق فورًا")
        page.card {
            btn("معاينة الجزيرة", 0) { Ui.preview(this@SettingsActivity) }
        }

        page.card("القوالب الجاهزة") {
            PRESETS.keys.forEach { name ->
                btn(name, 6) {
                    s.applyPreset(name)
                    render()
                    Ui.preview(this@SettingsActivity)
                }
            }
        }

        page.card("الحجم") {
            sliderRow("العرض المضغوط", s, K.WIDTH, 90, 260, " dp")
            sliderRow("الارتفاع (0 = تلقائي حسب الكاميرا)", s, K.HEIGHT, 0, 80, " dp")
            sliderRow("عرض الجزيرة الموسعة", s, K.EXP_WIDTH, 240, 420, " dp")
            sliderRow("انحناء الزوايا", s, K.RADIUS, 12, 44, " dp")
            sliderRow("الحشوة الداخلية", s, K.PADDING, 6, 24, " dp")
            sliderRow("حجم الأيقونات", s, K.ICON, 14, 34, " dp")
            sliderRow("حجم النص", s, K.TEXT, 11, 20, " sp")
        }

        page.card("الألوان") {
            swatches("الخلفية", s, K.BG, Ui.BG_COLORS, 6)
            swatches("لون التمييز", s, K.ACCENT, Ui.ACCENTS)
            sliderRow("الشفافية", s, K.ALPHA, 30, 100, "%")
            switchRow("حد خفيف حول الجزيرة", null, s.bool(K.BORDER)) { s.put(K.BORDER, it) }
            switchRow("الوضع الليلي التلقائي", "خلفية سوداء عند تفعيل الوضع الداكن للنظام", s.int(K.NIGHT) == 1) {
                s.put(K.NIGHT, if (it) 1 else 0)
            }
        }

        page.card("الحركة") {
            label("نوع الحركة", 14f, top = 6)
            val names = listOf("مرِنة", "ناعمة", "سريعة")
            names.forEachIndexed { i, n ->
                btn((if (s.int(K.MOTION) == i) "● " else "○ ") + n, 6) {
                    s.put(K.MOTION, i)
                    render()
                    Ui.preview(this@SettingsActivity)
                }
            }
            sliderRow("سرعة الأنيميشن", s, K.ANIM_SPEED, 50, 200, "%")
            switchRow("اهتزاز خفيف عند الظهور", null, s.bool(K.HAPTICS)) { s.put(K.HAPTICS, it) }
        }

        page.card("السلوك والموضع") {
            sliderRow("مدة ظهور التنبيهات", s, K.DURATION, 2, 12, " ث", 6)
            sliderRow("إزاحة أفقية", s, K.OFFSET_X, -60, 60, " dp")
            sliderRow("إزاحة رأسية", s, K.OFFSET_Y, -10, 60, " dp")
            sliderRow("حد سرعة الشبكة لإظهارها", s, K.NET_THRESHOLD, 20, 2000, " KB/s")
            switchRow("إظهار نص الإشعارات", "إيقافه يعرض اسم التطبيق فقط", s.bool(K.NOTIF_PREVIEW)) { s.put(K.NOTIF_PREVIEW, it) }
            switchRow("تنبيه انخفاض البطارية", null, s.bool(K.BATTERY_ALERT)) { s.put(K.BATTERY_ALERT, it) }
            sliderRow("حد انخفاض البطارية", s, K.BATTERY_LOW, 5, 40, "%")
        }

        page.card("ترتيب الأولويات (الأعلى يظهر فوق غيره)") {
            val order = s.order()
            order.forEachIndexed { idx, id ->
                val type = EventType.fromId(id) ?: return@forEachIndexed
                val row = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.HORIZONTAL }
                val name = label("${idx + 1}. ${Ui.TYPE_LABELS[type]}", 14f, top = 8)
                (name.parent as LinearLayout).removeView(name)
                row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (idx > 0) row.addView(small("▲") { move(order, idx, idx - 1) })
                if (idx < order.size - 1) row.addView(small("▼") { move(order, idx, idx + 1) })
                addView(row, lp(2))
            }
        }

        page.card("تصدير / استيراد") {
            btn("تصدير الإعدادات (مشاركة)", 6) {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.exportJson())
                startActivity(Intent.createChooser(i, "تصدير الإعدادات"))
            }
            btn("استيراد من الحافظة") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                val ok = txt.isNotEmpty() && s.importJson(txt)
                Toast.makeText(this@SettingsActivity, if (ok) "تم الاستيراد" else "الحافظة لا تحتوي إعدادات صالحة", Toast.LENGTH_SHORT).show()
                if (ok) render()
            }
            btn("استعادة الإعدادات الافتراضية") {
                s.resetAll()
                render()
            }
        }
    }

    private fun small(t: String, onClick: () -> Unit): com.google.android.material.button.MaterialButton =
        com.google.android.material.button.MaterialButton(this).apply {
            text = t
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { onClick() }
        }

    private fun move(order: List<String>, from: Int, to: Int) {
        val m = order.toMutableList()
        val x = m.removeAt(from)
        m.add(to, x)
        s.put(K.ORDER, m.joinToString(","))
        render()
    }
}
