import os
import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.keys import Keys
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
                {"role": "system", "content": "أنت صانع محتوى إسلامي حكيم. اكتب بوست قصير ومؤثر للفيسبوك (آية أو حديث أو نصيحة) مع هاشتاج واحد فقط."},
                {"role": "user", "content": "اكتب بوست اليوم."}
            ]
        )
        return completion.choices[0].message.content
    except Exception as e:
        print(f"⚠️ خطأ في Groq: {e}")
        return "الحمد لله رب العالمين."

def start_posting():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    # يوزر إيجنت حديث جداً
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    wait = WebDriverWait(driver, 20)
    
    try:
        print("🌐 فتح فيسبوك (نسخة الموبايل)...")
        driver.get("https://mbasic.facebook.com/")
        time.sleep(5)

        # 1. محاولة إخفاء أي طبقة كوكيز أو تحذيرات بالـ JavaScript
        try:
            driver.execute_script("document.querySelectorAll('button').forEach(b => { if(b.innerText.includes('Allow') || b.innerText.includes('موافق')) b.click(); });")
            time.sleep(2)
        except: pass

        print("🔑 إدخال بيانات الدخول...")
        
        # البحث عن خانة الإيميل والتأكد إنها قابلة للتفاعل
        email_field = wait.until(EC.element_to_be_clickable((By.NAME, "email")))
        
        # استخدام JavaScript للكتابة لضمان تخطي أي عوائق
        driver.execute_script("arguments[0].value = '';", email_input := email_field)
        email_input.send_keys(FB_EMAIL)
        
        pass_field = driver.find_element(By.NAME, "pass")
        pass_field.send_keys(FB_PASS)
        
        print("⏳ ضغط تسجيل الدخول...")
        # نضغط Enter بدل ما ندور على الزرار عشان أضمن
        pass_field.send_keys(Keys.ENTER)
        
        time.sleep(10)

        # 2. فحص الأمان
        if "checkpoint" in driver.current_url:
            print("⚠️ أمان فيسبوك: يرجى الموافقة على الدخول من موبايلك الآن!")
            return

        # 3. الوصول لصفحة النشر
        print("✍️ تجهيز المنشور...")
        # نفتح الرئيسية تاني عشان نتأكد إننا دخلنا
        driver.get("https://mbasic.facebook.com/")
        time.sleep(5)

        post_content = get_ai_content()
        
        # البحث عن صندوق النص
        # في mbasic أحياناً بيكون اسمه 'xc_message'
        try:
            textarea = wait.until(EC.visibility_of_element_located((By.NAME, "xc_message")))
            textarea.send_keys(post_content)
            time.sleep(2)
            
            # زر النشر
            submit_btn = driver.find_element(By.NAME, "view_post")
            driver.execute_script("arguments[0].click();", submit_btn)
            
            print(f"🚀 تم النشر بنجاح: {post_content}")
        except Exception as e:
            print(f"❌ فشل العثور على صندوق الكتابة. الرابط الحالي: {driver.current_url}")
            # حفظ صورة للخطأ عشان نشوفها في GitHub Artifacts
            driver.save_screenshot("error_post.png")
            raise e
            
    except Exception as e:
        print(f"❌ حدث خطأ: {e}")
        driver.save_screenshot("final_error.png")
    finally:
        driver.quit()

if __name__ == "__main__":
    start_posting()
