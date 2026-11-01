package com.dynamicisland.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import com.dynamicisland.data.dp
import com.dynamicisland.monitors.TimerEngine
import com.dynamicisland.service.IslandService
import com.dynamicisland.service.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerActivity : AppCompatActivity() {
    private lateinit var page: Page
    private var clock: TextView? = null
    private var minutes = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = Page(this)
        setContentView(page.root)
        TimerEngine.init(this)
        render()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TimerEngine.display.collect { clock?.text = it }
            }
        }
    }

    private fun ensureService() {
        if (!Perms.overlay(this)) {
            Toast.makeText(this, "فعّل إذن الظهور فوق التطبيقات من الشاشة الرئيسية", Toast.LENGTH_LONG).show()
        }
        if (!IslandService.running) IslandService.start(this)
    }

    private fun render() {
        page.clear()
        page.title("المؤقت", "يظهر الوقت داخل الجزيرة ويستمر في الخلفية")
        page.card("الوقت الحالي") {
            clock = label(TimerEngine.display.value, 44f, Ui.TEXT, true, 4).apply { gravity = Gravity.CENTER }
            btn("إيقاف مؤقت / استئناف", 10) { TimerEngine.toggle() }
            btn("إعادة ضبط", 6) { TimerEngine.reset() }
        }
        page.card("ساعة إيقاف") {
            btn("ابدأ ساعة الإيقاف", 6) {
                ensureService()
                TimerEngine.startStopwatch(this@TimerActivity)
            }
        }
        page.card("مؤقت تنازلي") {
            val head = label("المدة: $minutes دقيقة", 14f, top = 6)
            val sl = com.google.android.material.slider.Slider(context).apply {
                valueFrom = 1f
                valueTo = 180f
                stepSize = 1f
                value = minutes.toFloat()
            }
            sl.addOnChangeListener { _, v, fromUser ->
                if (fromUser) {
                    minutes = v.toInt()
                    head.text = "المدة: $minutes دقيقة"
                }
            }
            addView(sl, lp(0))
            btn("ابدأ المؤقت", 6) {
                ensureService()
                TimerEngine.startCountdown(this@TimerActivity, minutes * 60_000L)
            }
        }
    }
}

class AppFilterActivity : AppCompatActivity() {
    private val s by lazy { IslandSettings.get(this) }
    private lateinit var page: Page
    private val selected = HashSet<String>()

    private class AppItem(val pkg: String, val name: String, val icon: Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = Page(this)
        setContentView(page.root)
        selected.addAll(s.str(K.FILTER_APPS).split(',').filter { it.isNotEmpty() })

        page.title("تطبيقات الإشعارات", "تنطبق على الإشعارات والمكالمات والتنزيلات")
        page.card("الوضع") {
            switchRow(
                "السماح للتطبيقات المحددة فقط",
                "إيقافه = كل التطبيقات مسموحة عدا المحددة (حظر)",
                s.int(K.FILTER_MODE) == 1, 6
            ) { s.put(K.FILTER_MODE, if (it) 1 else 0) }
        }
        val listCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.col.addView(listCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        listCard.label("جارٍ تحميل التطبيقات…", 13f, Ui.SUB)

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.Default) { loadApps() }
            listCard.removeAllViews()
            apps.forEach { listCard.addView(row(it)) }
        }
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.name.lowercase() }
    }

    private fun row(a: AppItem): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        r.addView(ImageView(this).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        r.addView(
            TextView(this).apply { text = a.name; setTextColor(Ui.TEXT); textSize = 15f; setPadding(dp(12), 0, dp(12), 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        r.addView(CheckBox(this).apply {
            isChecked = a.pkg in selected
            setOnCheckedChangeListener { _, on ->
                if (on) selected.add(a.pkg) else selected.remove(a.pkg)
                s.put(K.FILTER_APPS, selected.joinToString(","))
            }
        })
        return r
    }
}

class XiaomiGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = Page(this)
        setContentView(page.root)
        val appLabel = getString(com.dynamicisland.R.string.app_name)

        page.title("استمرارية الجزيرة على Xiaomi", "MIUI يقتل التطبيقات الخلفية بقوة. هذه الخطوات تعطي أعلى موثوقية عملية ضمن الصلاحيات الرسمية، لكنها لا تضمن بقاء التطبيق 100%.")

        page.card("1) التشغيل التلقائي (Autostart)") {
            label("يسمح للتطبيق بالعودة بعد الإقلاع أو بعد إغلاقه من النظام.", 12f, Ui.SUB, top = 4)
            btn("فتح إعداد التشغيل التلقائي", 8) {
                launchSafe(Perms.xiaomiAutostartIntent(), Perms.appDetailsIntent(this@XiaomiGuideActivity))
            }
        }
        page.card("2) توفير البطارية: «بدون قيود»") {
            label("اختر «No restrictions / بدون قيود» لهذا التطبيق، ولا تستخدم «Battery saver».", 12f, Ui.SUB, top = 4)
            btn("فتح إعداد البطارية", 8) {
                launchSafe(Perms.xiaomiBatteryIntent(this@XiaomiGuideActivity, appLabel), Perms.batteryIntent(), Perms.appDetailsIntent(this@XiaomiGuideActivity))
            }
        }
        page.card("3) إذن الإشعارات") {
            label("فعّل إشعارات التطبيق كي لا تُخفى إشعار الخدمة التي تُبقي الجزيرة حية.", 12f, Ui.SUB, top = 4)
            btn("فتح إعدادات الإشعارات", 8) {
                launchSafe(Perms.appNotifSettingsIntent(this@XiaomiGuideActivity), Perms.appDetailsIntent(this@XiaomiGuideActivity))
            }
        }
        page.card("4) الظهور فوق التطبيقات والنوافذ المنبثقة") {
            label("فعّل «الظهور فوق التطبيقات الأخرى». وفي MIUI افتح «أذونات أخرى» وفعّل «عرض النوافذ المنبثقة أثناء التشغيل في الخلفية».", 12f, Ui.SUB, top = 4)
            btn("فتح إذن الظهور", 8) { launchSafe(Perms.overlayIntent(this@XiaomiGuideActivity)) }
            btn("فتح الأذونات الأخرى (MIUI)", 6) {
                launchSafe(Perms.xiaomiPermEditorIntent(this@XiaomiGuideActivity), Perms.appDetailsIntent(this@XiaomiGuideActivity))
            }
        }
        page.card("5) الوصول إلى الإشعارات") {
            label("ضروري لمعاينة الإشعارات والوسائط. عند تعطيله لا تعمل هذه الميزات.", 12f, Ui.SUB, top = 4)
            btn("فتح إعداد الوصول للإشعارات", 8) { launchSafe(Perms.notifAccessIntent()) }
        }
        page.card("6) قفل التطبيق في Recent Apps") {
            label("افتح شاشة التطبيقات الأخيرة، اضغط مطولًا على بطاقة Dynamic Island ثم اختر أيقونة القفل (أو اسحب البطاقة للأسفل في بعض الإصدارات). هذا يمنع «مسح الكل» من إغلاقه.", 12f, Ui.SUB, top = 4)
        }
        page.card("ملاحظات صادقة") {
            label("• لا توجد طريقة رسمية تمنع النظام من قتل التطبيق نهائيًا؛ التطبيق يستعيد نفسه عبر خدمة أمامية وWorkManager ومستقبل الإقلاع.\n• الإعدادات تختلف حسب إصدار MIUI؛ إن لم يفتح زر الصفحة المناسبة سننقلك لصفحة التطبيق العامة.", 12f, Ui.SUB, top = 4)
        }
    }

    private fun launchSafe(vararg intents: Intent) {
        if (!Perms.open(this, *intents)) {
            Toast.makeText(this, "تعذّر فتح الإعداد، افتحه يدويًا من إعدادات النظام", Toast.LENGTH_LONG).show()
        }
    }
}
