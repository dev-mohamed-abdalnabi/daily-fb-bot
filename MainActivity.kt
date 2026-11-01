package com.dynamicisland.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dynamicisland.data.IslandSettings
import com.dynamicisland.data.K
import com.dynamicisland.service.IslandService
import com.dynamicisland.service.Perms
import com.dynamicisland.service.WatchdogWorker

class MainActivity : AppCompatActivity() {
    private lateinit var page: Page
    private val s by lazy { IslandSettings.get(this) }

    private val reqPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    private val features = listOf(
        Triple(K.F_NETWORK, "سرعة الشبكة", "تظهر أثناء نقل البيانات فقط (TrafficStats)"),
        Triple(K.F_MEDIA, "مشغل الوسائط", "تحكم كامل عبر MediaSession — يحتاج الوصول للإشعارات"),
        Triple(K.F_NOTIF, "الإشعارات", "معاينة وتجميع حسب التطبيق"),
        Triple(K.F_CALLS, "المكالمات", "من إشعارات المكالمات: رد/رفض"),
        Triple(K.F_CHARGING, "الشحن والبطارية", "عند توصيل الشاحن واكتمال الشحن وانخفاض البطارية"),
        Triple(K.F_CONNECT, "الاتصال", "Wi-Fi وبيانات الجوال وVPN وBluetooth ووضع الطيران"),
        Triple(K.F_TIMER, "المؤقت وساعة الإيقاف", "الوقت المتبقي مباشرة في الجزيرة"),
        Triple(K.F_AUDIO, "الميكروفون", "تنبيه عند استخدام الميكروفون"),
        Triple(K.F_PROGRESS, "التنزيلات والملاحة والتحديثات", "من إشعارات التقدم والملاحة")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = Page(this)
        setContentView(page.root)
        WatchdogWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        startIfPossible(false)
    }

    private fun startIfPossible(prompt: Boolean) {
        if (!s.bool(K.ENABLED)) return
        if (!Perms.canDraw(this)) {
            if (prompt) {
                Toast.makeText(this, "فعّل إذن الظهور فوق التطبيقات أولًا", Toast.LENGTH_LONG).show()
                Perms.open(this, Perms.overlayIntent(this))
            }
        } else if (!IslandService.running) {
            IslandService.start(this)
        }
    }

    private fun render() {
        page.clear()
        page.title("Dynamic Island", "جزيرة ديناميكية متكاملة مع شريط الحالة")

        page.card("التشغيل") {
            switchRow("تشغيل الجزيرة", "تعمل بخدمة أمامية خفيفة وتُستعاد بعد الإقلاع", s.bool(K.ENABLED)) { on ->
                s.put(K.ENABLED, on)
                if (on) startIfPossible(true) else IslandService.stop(this@MainActivity)
            }
            btn("معاينة الجزيرة") { Ui.preview(this@MainActivity) }
        }

        page.card("الخطوة 1: العرض فوق شريط الحالة") {
            label(
                "نافذة Overlay العادية يرسمها Android تحت شريط الحالة، فالساعة والأيقونات تظهر فوق الجزيرة. " +
                    "لذلك الجزيرة تعتمد على خدمة إمكانية وصول تعطي نافذة فوق الشريط رسميًا. الخدمة لا تقرأ أي محتوى " +
                    "ولا تستقبل أي أحداث، ووظيفتها الوحيدة هي رسم الجزيرة.",
                12f, Ui.SUB, top = 6
            )
            statusRow("خدمة إمكانية الوصول", Perms.a11y(),
                "افتح الإعدادات ← التطبيقات المثبّتة ← Dynamic Island ← فعّلها. بدونها ترجع الجزيرة للرسم تحت الشريط.", "فتح إعدادات إمكانية الوصول") {
                Perms.open(this@MainActivity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            switchRow("الرسم فوق شريط الحالة", null, s.bool(K.A11Y_MODE)) { s.put(K.A11Y_MODE, it) }
        }

        page.card("الصلاحيات") {
            statusRow("الظهور فوق التطبيقات", Perms.overlay(this@MainActivity),
                "يُنصح به للنشر من الخلفية، وهو الاحتياطي إن لم تُفعَّل خدمة إمكانية الوصول.", "منح الإذن", 8) {
                Perms.open(this@MainActivity, Perms.overlayIntent(this@MainActivity))
            }
            statusRow("الوصول إلى الإشعارات", Perms.notifAccess(this@MainActivity),
                "لمعاينة الإشعارات والمكالمات والتنزيلات ولقراءة جلسات الوسائط. تُعالَج محليًا ولا تُخزَّن.", "منح الإذن") {
                Perms.open(this@MainActivity, Perms.notifAccessIntent())
            }
            if (Build.VERSION.SDK_INT >= 33) {
                statusRow("إشعارات التطبيق", Perms.postNotifications(this@MainActivity),
                    "لإظهار إشعار الخدمة الصغير الذي يُبقي الجزيرة تعمل.", "منح الإذن") {
                    reqPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                statusRow("Bluetooth (أسماء الأجهزة)", Perms.bluetooth(this@MainActivity),
                    "اختياري: لعرض اسم السماعة عند الاتصال.", "منح الإذن") {
                    reqPerm.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            statusRow("استثناء البطارية", Perms.batteryUnrestricted(this@MainActivity),
                "موصى به لزيادة الاستمرارية. راجع تعليمات Xiaomi أدناه.", "فتح الإعداد") {
                Perms.open(this@MainActivity, Perms.batteryIntent(), Perms.appDetailsIntent(this@MainActivity))
            }
        }

        page.card("الميزات") {
            features.forEach { (k, t, d) ->
                switchRow(t, d, s.bool(k)) { s.put(k, it) }
            }
        }

        page.card("أدوات") {
            btn("التخصيص (الشكل والحركة والأولويات)", 6) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
            btn("المؤقت وساعة الإيقاف") { startActivity(Intent(this@MainActivity, TimerActivity::class.java)) }
            btn("تطبيقات الإشعارات (السماح/الحظر)") { startActivity(Intent(this@MainActivity, AppFilterActivity::class.java)) }
            btn("تعليمات Xiaomi للاستمرارية") { startActivity(Intent(this@MainActivity, XiaomiGuideActivity::class.java)) }
        }

        page.card("الخصوصية") {
            label(
                "كل المعالجة تتم على جهازك. لا يوجد اتصال بالإنترنت ولا إرسال لأي بيانات. لا يُخزَّن محتوى " +
                    "الإشعارات ولا الحافظة. الأذونات تُطلب فقط لما تحتاجه الميزة المفعّلة.",
                12f, Ui.SUB, top = 6
            )
        }
    }
}
