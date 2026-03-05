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
                {"role": "system", "content": "أنت صانع محتوى إسلامي حكيم. اكتب بوست قصير جداً ومؤثر للفيسبوك (آية، حديث، أو نصيحة) مع هاشتاج واحد فقط."},
                {"role": "user", "content": "اكتب بوست اليوم."}
            ]
        )
        return completion.choices[0].message.content
    except Exception as e:
        print(f"⚠️ خطأ في Groq: {e}")
        return "سبحان الله وبحمده، سبحان الله العظيم."

def start_posting():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    # لإيهام فيسبوك أننا متصفح عادي وليس بوت
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    wait = WebDriverWait(driver, 20) # انتظار ذكي لمدة 20 ثانية
    
    try:
        print("🌐 فتح فيسبوك...")
        driver.get("https://mbasic.facebook.com/")
        
        # 1. التعامل مع صفحة الكوكيز لو ظهرت
        try:
            cookie_btn = driver.find_elements(By.XPATH, "//button[@value='Only allow essential cookies' or @value='Accept All']")
            if cookie_btn:
                cookie_btn[0].click()
                print("✅ تم تخطي صفحة الكوكيز")
        except: pass

        # 2. تسجيل الدخول
        print("🔑 إدخال بيانات الدخول...")
        wait.until(EC.presence_of_element_id("m_login_email")).send_keys(FB_EMAIL)
        driver.find_element(By.NAME, "pass").send_keys(FB_PASS)
        
        # محاولة الضغط على زر الدخول بأكثر من طريقة
        login_selectors = [
            "//input[@name='login']",
            "//button[@name='login']",
            "//input[@type='submit']",
            "//*[contains(@value, 'Log In')]",
            "//*[contains(@value, 'تسجيل الدخول')]"
        ]
        
        logged_in = False
        for selector in login_selectors:
            try:
                btn = driver.find_element(By.XPATH, selector)
                btn.click()
                logged_in = True
                print(f"✅ تم الضغط على زر الدخول باستخدام: {selector}")
                break
            except: continue
            
        if not logged_in:
            raise Exception("لم يتم العثور على زر الدخول")

        time.sleep(5)

        # 3. تخطي صفحة "حفظ المتصفح" (ساعات بتظهر بعد الدخول مباشرة)
        if "checkpoint" in driver.current_url:
            print("⚠️ فيسبوك طلب تأكيد هوية! راجع موبايلك الآن.")
            return
        
        if "save-device" in driver.current_url or "login/device-based" in driver.current_url:
            print("⏩ تخطي صفحة حفظ الجهاز...")
            try:
                ok_btn = wait.until(EC.element_to_be_clickable((By.XPATH, "//a[contains(@href, 'checkpoint') or contains(text(), 'OK') or contains(text(), 'موافق')]")))
                ok_btn.click()
            except:
                driver.get("https://mbasic.facebook.com/") # العودة للرئيسية لو وقف

        # 4. كتابة المنشور
        print("✍️ محاولة كتابة المنشور...")
        post_text = get_ai_content()
        
        # الانتظار حتى يظهر صندوق الكتابة
        textarea = wait.until(EC.presence_of_element_id("composerInput"))
        textarea.send_keys(post_text)
        
        # الضغط على زر نشر
        submit_btn = driver.find_element(By.XPATH, "//input[@name='view_post']")
        submit_btn.click()
        print(f"🚀 تم النشر بنجاح: {post_text}")
        
    except Exception as e:
        print(f"❌ حدث خطأ: {e}")
        # تصوير الشاشة للديياج (اختياري لو عايز تشوف العطل فين)
        driver.save_screenshot("error_page.png")
    finally:
        driver.quit()

if __name__ == "__main__":
    start_posting()
