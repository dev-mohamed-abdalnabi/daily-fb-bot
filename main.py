import os
import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.chrome.service import Service
from groq import Groq

# جلب البيانات من الخزنة (Secrets)
FB_EMAIL = os.environ.get("FB_EMAIL")
FB_PASS = os.environ.get("FB_PASS")
GROQ_KEY = os.environ.get("GROQ_KEY")

def get_ai_content():
    try:
        client = Groq(api_key=GROQ_KEY)
        completion = client.chat.completions.create(
            model="llama3-8b-8192",
            messages=[
                {"role": "system", "content": "أنت صانع محتوى إسلامي حكيم. اكتب بوست قصير جداً ومؤثر للفيسبوك (آية، حديث، أو نصيحة) مع هاشتاج واحد فقط."},
                {"role": "user", "content": "اكتب بوست اليوم."}
            ]
        )
        return completion.choices[0].message.content
    except Exception as e:
        print(f"Error calling Groq: {e}")
        return "سبحان الله وبحمده، سبحان الله العظيم."

def start_posting():
    chrome_options = Options()
    chrome_options.add_argument("--headless") # تشغيل مخفي
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    
    # إعداد المتصفح
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    
    try:
        print("🌐 فتح فيسبوك...")
        driver.get("https://mbasic.facebook.com/")
        time.sleep(3)
        
        # تسجيل الدخول
        print("🔑 تسجيل الدخول...")
        driver.find_element(By.NAME, "email").send_keys(FB_EMAIL)
        driver.find_element(By.NAME, "pass").send_keys(FB_PASS)
        driver.find_element(By.NAME, "login").click()
        time.sleep(5)
        
        # فحص لو في رسالة أمان
        if "checkpoint" in driver.current_url:
            print("⚠️ فيسبوك طلب تأكيد هوية. ستحتاج لفتح الحساب من موبايلك والموافقة.")
            return

        # كتابة المنشور
        print("✍️ كتابة المنشور...")
        post_text = get_ai_content()
        # البحث عن صندوق النص في نسخة mbasic
        textarea = driver.find_element(By.NAME, "xc_message")
        textarea.send_keys(post_text)
        
        # الضغط على زر نشر
        driver.find_element(By.NAME, "view_post").click()
        print(f"✅ تم النشر بنجاح: {post_text}")
        
    except Exception as e:
        print(f"❌ حدث خطأ: {e}")
    finally:
        driver.quit()

if __name__ == "__main__":
    start_posting()
