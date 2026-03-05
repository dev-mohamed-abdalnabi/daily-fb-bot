import os
import time
import json
import random
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.chrome.service import Service
from groq import Groq

# جلب البيانات من Secrets
FB_COOKIES_JSON = os.environ.get("FB_COOKIES")
GROQ_KEY = os.environ.get("GROQ_KEY")

def get_ai_content():
    try:
        # تنظيف المفتاح من أي مسافات مخفية
        key = GROQ_KEY.strip() if GROQ_KEY else ""
        client = Groq(api_key=key)
        completion = client.chat.completions.create(
            model="llama3-8b-8192",
            messages=[
                {"role": "system", "content": "أنت صانع محتوى إسلامي. اكتب ذكر أو نصيحة إسلامية قصيرة ومؤثرة جداً للفيسبوك مع هاشتاج واحد فقط."},
                {"role": "user", "content": "اكتب بوست اليوم."}
            ]
        )
        return completion.choices[0].message.content
    except Exception as e:
        print(f"⚠️ خطأ في الذكاء الاصطناعي: {e}")
        return random.choice([
            "سبحان الله وبحمده، سبحان الله العظيم. #ذكر",
            "اللهم صل وسلم وبارك على نبينا محمد. #الجمعة",
            "لا إله إلا أنت سبحانك إني كنت من الظالمين. #دعاء"
        ])

def start_posting():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    # اليوزر إيجنت مهم جداً عشان يطابق الكوكيز
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    wait = WebDriverWait(driver, 30)
    
    try:
        print("🌐 تهيئة المتصفح...")
        # لازم نفتح فيسبوك الأول عشان نقدر نحط الكوكيز في نطاق الدومين
        driver.get("https://mbasic.facebook.com/")
        time.sleep(3)

        print("🍪 حقن الكوكيز...")
        cookies = json.loads(FB_COOKIES_JSON)
        for cookie in cookies:
            # تنظيف الكوكيز من القيم التي قد تسبب مشاكل في Selenium
            if 'sameSite' in cookie and cookie['sameSite'] not in ["Strict", "Lax", "None"]:
                del cookie['sameSite']
            try:
                driver.add_cookie(cookie)
            except:
                continue
        
        print("🔄 تسجيل الدخول التلقائي بالكوكيز...")
        # الذهاب مباشرة لصفحة النشر
        driver.get("https://mbasic.facebook.com/composer/")
        time.sleep(7)

        # التأكد إننا جوه صفحة الكتابة مش صفحة اللوجين
        if "login" in driver.current_url:
            print("❌ فشل الدخول بالكوكيز! (ممكن تكون انتهت أو JSON غير صحيح)")
            return

        print("✍️ جاري كتابة المنشور...")
        post_text = get_ai_content()
        print(f"🤖 المحتوى: {post_text}")
        
        # البحث عن صندوق النص في نسخة mbasic
        textarea = wait.until(EC.presence_of_element_located((By.NAME, "xc_message")))
        textarea.send_keys(post_text)
        time.sleep(2)
        
        print("🚀 جاري النشر...")
        submit_btn = driver.find_element(By.NAME, "view_post")
        submit_btn.click()
        
        time.sleep(5)
        print(f"✅ مبروك! البوست نزل بنجاح على حسابك الشخصي.")
            
    except Exception as e:
        print(f"❌ حدث خطأ غير متوقع: {e}")
        driver.save_screenshot("final_error.png")
    finally:
        driver.quit()

if __name__ == "__main__":
    start_posting()
