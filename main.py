import os
import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
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
                {"role": "system", "content": "أنت صانع محتوى إسلامي حكيم. اكتب بوست قصير ومؤثر للفيسبوك (آية أو حديث أو نصيحة) مع هاشتاج واحد. اجعل الأسلوب بشرياً."},
                {"role": "user", "content": "اكتب بوست اليوم."}
            ]
        )
        return completion.choices[0].message.content
    except Exception as e:
        print(f"⚠️ خطأ في Groq: {e}")
        return "الحمد لله رب العالمين، حمداً كثيراً طيباً مباركاً فيه."

def start_posting():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    # يوزر إيجنت عشان فيسبوك يفتكرنا متصفح عادي
    chrome_options.add_argument("user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    wait = WebDriverWait(driver, 30) # انتظار يصل لـ 30 ثانية
    
    try:
        print("🌐 فتح فيسبوك...")
        driver.get("https://mbasic.facebook.com/")
        
        # 1. التعامل مع الكوكيز لو ظهرت
        try:
            cookie_btns = driver.find_elements(By.XPATH, "//button")
            for btn in cookie_btns:
                if "Allow" in btn.text or "موافق" in btn.text:
                    btn.click()
                    break
        except: pass

        # 2. تسجيل الدخول
        print("🔑 إدخال بيانات الدخول...")
        # تصحيح الخطأ: استخدام presence_of_element_located مع Tuple
        email_input = wait.until(EC.presence_of_element_located((By.NAME, "email")))
        email_input.send_keys(FB_EMAIL)
        
        driver.find_element(By.NAME, "pass").send_keys(FB_PASS)
        
        # محاولة الضغط على زر الدخول
        try:
            login_btn = driver.find_element(By.NAME, "login")
            login_btn.click()
        except:
            driver.find_element(By.XPATH, "//input[@type='submit']").click()
            
        print("⏳ جاري تسجيل الدخول...")
        time.sleep(7)

        # 3. تخطي صفحة "حفظ الجهاز" أو "تأكيد الهوية"
        if "checkpoint" in driver.current_url:
            print("⚠️ فيسبوك طلب تأكيد هوية! وافق من موبايلك.")
            return

        if "login/device-based" in driver.current_url or "save-device" in driver.current_url:
            print("⏩ تخطي صفحة حفظ المتصفح...")
            driver.get("https://mbasic.facebook.com/") # ارجع للرئيسية فوراً
            time.sleep(5)

        # 4. كتابة المنشور
        print("✍️ محاولة كتابة المنشور...")
        post_text = get_ai_content()
        
        # التأكد إننا في الصفحة الرئيسية ومربع الكتابة موجود
        try:
            textarea = wait.until(EC.presence_of_element_located((By.NAME, "xc_message")))
            textarea.send_keys(post_text)
            
            # زر النشر
            submit_btn = driver.find_element(By.NAME, "view_post")
            submit_btn.click()
            print(f"🚀 تم النشر بنجاح: {post_text}")
        except Exception as e:
            print(f"❌ لم نجد صندوق الكتابة. الرابط الحالي: {driver.current_url}")
            raise e
        
    except Exception as e:
        print(f"❌ حدث خطأ: {e}")
    finally:
        driver.quit()

if __name__ == "__main__":
    start_posting()
