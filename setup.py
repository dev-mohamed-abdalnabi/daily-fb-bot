#!/usr/bin/env python3
"""
Setup Generator — يحوّل ملفات الجذر المسطّحة إلى هيكل مشروع Android كامل (app/src/main/...).
يُشغَّل تلقائيًا داخل GitHub Actions، ويمكن تشغيله يدويًا: python3 setup.py
- ملفات .kt توضع حسب سطر package داخلها.
- كل ملفات الموارد (strings/colors/themes/الأيقونات/المشغّل/إعداد إمكانية الوصول) تُولَّد من هذا الملف.
- يتحقق من اتساق المشروع قبل البناء ويفشل برسالة واضحة عند وجود خطأ.
"""
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
MAIN = APP / "src" / "main"
NS = "com.dynamicisland"

errors = []


def fail(msg):
    errors.append(msg)
    print("ERROR: " + msg)


def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


# ───────────────────────── الموارد المولَّدة ─────────────────────────

STRINGS = {
    "app_name": "Dynamic Island",
    "channel_service": "خدمة الجزيرة",
    "service_running": "الجزيرة تعمل",
    "a11y_desc": "تُستخدم فقط لرسم الجزيرة فوق شريط الحالة. لا تقرأ أي محتوى ولا تستقبل أي أحداث.",
}

COLORS = {"island_bg": "#FF0B0C0F", "ic_bg": "#FF1E2430"}

THEME = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Island" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">#FF4FC3F7</item>
        <item name="colorOnPrimary">#FF00253A</item>
        <item name="colorSurface">#FF16181D</item>
        <item name="android:windowBackground">@color/island_bg</item>
        <item name="android:statusBarColor">@color/island_bg</item>
        <item name="android:navigationBarColor">@color/island_bg</item>
    </style>
</resources>
"""

ICONS = {
    "ic_play": "M8,5v14l11,-7z",
    "ic_pause": "M6,19h4V5H6v14zM14,5v14h4V5h-4z",
    "ic_next": "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z",
    "ic_prev": "M6,6h2v12H6zM9.5,12l8.5,6V6z",
    "ic_stop": "M6,6h12v12H6z",
    "ic_bolt": "M7,2v11h3v9l7,-12h-4l4,-8z",
    "ic_wifi": "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
    "ic_signal": "M2,22h20V2L2,22z",
    "ic_bluetooth": "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z",
    "ic_airplane": "M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z",
    "ic_vpn": "M12.65,10C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H17v4h4v-4h2v-4H12.65zM7,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z",
    "ic_mic": "M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
    "ic_call": "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z",
    "ic_download": "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z",
    "ic_timer": "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
    "ic_bell": "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z",
    "ic_music": "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z",
    "ic_nav": "M12,2L4.5,20.29l0.71,0.71L12,18l6.79,3 0.71,-0.71z",
}

VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{d}" />
</vector>
"""

LAUNCHER_FG = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FF000000" android:pathData="M36,44h36a10,10 0 0 1 0,20h-36a10,10 0 0 1 0,-20z" />
    <path android:fillColor="#FF4FC3F7" android:pathData="M54,54m-5,0a5,5 0 1,0 10,0a5,5 0 1,0 -10,0" />
</vector>
"""

LAUNCHER = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_bg" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""

A11Y = """<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="false"
    android:notificationTimeout="100"
    android:description="@string/a11y_desc" />
"""


def gen_resources():
    res = MAIN / "res"
    items = "".join(f'    <string name="{k}">{v}</string>\n' for k, v in STRINGS.items())
    write(res / "values" / "strings.xml", f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{items}</resources>\n')
    items = "".join(f'    <color name="{k}">{v}</color>\n' for k, v in COLORS.items())
    write(res / "values" / "colors.xml", f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{items}</resources>\n')
    write(res / "values" / "themes.xml", THEME)
    for name, d in ICONS.items():
        write(res / "drawable" / f"{name}.xml", VECTOR.replace("{d}", d))
    write(res / "drawable" / "ic_launcher_foreground.xml", LAUNCHER_FG)
    write(res / "mipmap-anydpi-v26" / "ic_launcher.xml", LAUNCHER)
    write(res / "xml" / "island_accessibility.xml", A11Y)


# ───────────────────────── المصدر والملفات ─────────────────────────

def gen_sources():
    kt_files = sorted(ROOT.glob("*.kt"))
    if not kt_files:
        fail("لم يتم العثور على أي ملف .kt في الجذر")
    for f in kt_files:
        text = f.read_text(encoding="utf-8")
        m = re.search(r"^package\s+([\w.]+)", text, re.M)
        if not m:
            fail(f"{f.name}: لا يحتوي سطر package")
            continue
        pkg = m.group(1)
        if not pkg.startswith(NS):
            fail(f"{f.name}: package خارج {NS}: {pkg}")
        dest = MAIN / "java" / Path(*pkg.split("."))
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(f, dest / f.name)


def gen_gradle():
    for need in ("settings.gradle.kts", "build.gradle.kts", "gradle.properties", "app_build.gradle.kts", "AndroidManifest.xml"):
        if not (ROOT / need).exists():
            fail(f"الملف المطلوب غير موجود في الجذر: {need}")
    if errors:
        return
    APP.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / "app_build.gradle.kts", APP / "build.gradle.kts")
    MAIN.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / "AndroidManifest.xml", MAIN / "AndroidManifest.xml")


# ───────────────────────── مراجعة الاتساق ─────────────────────────

def review():
    sources = "\n".join(p.read_text(encoding="utf-8") for p in (MAIN / "java").rglob("*.kt"))
    declared = set(re.findall(r"\b(?:class|object|interface)\s+(\w+)", sources))

    manifest = (MAIN / "AndroidManifest.xml").read_text(encoding="utf-8")
    for name in re.findall(r'android:name="(\.[\w.]+)"', manifest):
        cls = name.split(".")[-1]
        if cls not in declared:
            fail(f"AndroidManifest يشير إلى كلاس غير موجود: {name}")
        else:
            subpkg = name.rsplit(".", 1)[0].lstrip(".")
            expected = MAIN / "java" / Path(*NS.split(".")) / Path(*subpkg.split(".")) if subpkg else MAIN / "java" / Path(*NS.split("."))
            hits = [p for p in expected.glob("*.kt") if re.search(rf"\b(class|object)\s+{cls}\b", p.read_text(encoding="utf-8"))]
            if not hits:
                fail(f"الكلاس {cls} غير موجود في الحزمة المتوقعة {NS}.{subpkg}")

    for d in set(re.findall(r"R\.drawable\.(\w+)", sources)):
        if d not in ICONS and d != "ic_launcher_foreground":
            fail(f"R.drawable.{d} مستخدم في الكود ولا يوجد له مورد")
    for d in set(re.findall(r"R\.string\.(\w+)", sources)):
        if d not in STRINGS:
            fail(f"R.string.{d} مستخدم في الكود ولا يوجد له مورد")
    for r in re.findall(r"@(?:drawable|color|string|xml|mipmap|style)/([\w.]+)", manifest):
        known = set(ICONS) | set(STRINGS) | set(COLORS) | {"ic_launcher", "ic_launcher_foreground", "island_accessibility", "Theme.Island"}
        if r not in known:
            fail(f"AndroidManifest يشير إلى مورد غير موجود: {r}")

    for f in (MAIN / "java").rglob("*.kt"):
        t = f.read_text(encoding="utf-8")
        if "TODO(" in t or "// TODO" in t:
            fail(f"{f.name}: يحتوي TODO")
        for a, b in (("{", "}"), ("(", ")")):
            # الأقواس داخل النصوص العربية مثل "1)" قد تخلّ بالعدّ، نتحقق من الأقواس المعقوفة فقط
            if a == "{" and t.count(a) != t.count(b):
                fail(f"{f.name}: عدد الأقواس المعقوفة غير متوازن")


def main():
    if APP.exists():
        shutil.rmtree(APP / "src", ignore_errors=True)
    gen_gradle()
    if errors:
        sys.exit(1)
    gen_sources()
    gen_resources()
    review()
    if errors:
        print(f"\nفشل الإعداد: {len(errors)} خطأ. أصلحها ثم ارفع الملفات مرة أخرى.")
        sys.exit(1)
    n_kt = len(list((MAIN / "java").rglob("*.kt")))
    print(f"OK: تم توليد هيكل المشروع ({n_kt} ملف Kotlin، {len(ICONS) + 1} أيقونة).")


if __name__ == "__main__":
    main()
