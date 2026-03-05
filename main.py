import os
import time
import random
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.keys import Keys
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.chrome.service import Service
from groq import Groq

FB_EMAIL = os.environ.get("FB_EMAIL")
FB_PASS = os.environ.get("FB_PASS")
GROQ_KEY = os.environ.get("GROQ_KEY")

def human_type(element, text):
    """كتابة النص حرف حرف كأنك إنسان"""
    for char in text:
        element.send_keys(char)
        time.sleep(random.uniform(0.1, 0.3))

def get_ai_content():
    try:
        client = Groq(api_key=GROQ_KEY.strip()) # تنظيف المفتاح من أي مسافات
        completion = client.chat.completions.create(
            model="llama3-8b-8192",
            messages=[{"role": "user", "content": "اكتب ذكر أو نصيحة إسلامية قصيرة جداً للفيسبوك مع هاشتاج."}]
        )
        return completion.choices[0].message.content
    except:
        return random.choice(["سبحان الله وبحمده. #ذكر", "صلوا على النبي. #محمد", "الحمد لله دائماً. #دعاء"])

def start_posting():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    # استخدام User-Agent لمتصفح حقيقي على ويندوز
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
    
    driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=chrome_options)
    wait = WebDriverWait(driver, 30)
    
    try:
        print("🌐 الدخول لصفحة تسجيل الدخول...")
        driver.get("https://www.facebook.com/") # الدخول من الواجهة الرئيسية أضمن
        time.sleep(5)

        print("🔑 إدخال البيانات ببطء...")
        email_el = wait.until(EC.presence_of_element_located((By.ID, "email")))
        human_type(email_el, FB_EMAIL)
        
        pass_el = driver.find_element(By.ID, "pass")
        human_type(pass_el, FB_PASS)
        
        time.sleep(1)
        pass_el.send_keys(Keys.ENTER)
        
        print("⏳ انتظر 15 ثانية للتحميل وتخطي الحماية...")
        time.sleep(15)

        # التحويل لنسخة الموبايل البسيطة بعد الدخول
        driver.get("https://mbasic.facebook.com/composer/")
        time.sleep(5)

        if "login" in driver.current_url:
            print("❌ فيسبوك رفض الدخول. جرب فتح الحساب من متصفح موبايلك العادي وتأكد من البيانات.")
            driver.save_screenshot("failed_login.png")
            return

        print("✍️ كتابة المنشور...")
        post_text = get_ai_content()
        
        textarea = wait.until(EC.presence_of_element_located((By.NAME, "xc_message")))
        human_type(textarea, post_content := post_text)
        
        print("🚀 نشر...")
        submit_btn = driver.find_element(By.NAME, "view_post")
        driver.execute_script("arguments[0].click();", submit_btn)
        
        time.sleep(5)
        print(f"✅ تمت العملية! المنشور: {post_content}")
            
    except Exception as e:
        print(f"❌ خطأ: {e}")
        driver.save_screenshot("error.png")
    finally:
        driver.quit()

if __name__ == "__main__":
    start_posting()
