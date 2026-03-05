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
        # المفتاح اللي أنت بعتهولي شغال، بس اتأكد إنك حطه صح في الـ Secrets
        client = Groq(api_key=GROQ_KEY)
        completion = client.chat.completions.create(
            model="llama3-8b-8192",
            messages=[
                {"role": "system", "content": "أنت صانع محتوى إسلامي حكيم. اكتب بوست قصير جداً ومؤثر للفيسبوك (آية أو حديث أو نصيحة) مع هاشتاج واحد فقط."},
                {"role": "user", "content": "اكتب بوست اليوم."}
            ]
        )
        return completion.choices[0].message.content
    except Exception as e:
        print(f"⚠️ خطأ في Groq (تأكد من الـ API Key): {e}")
        # نص احتياطي عشان السكربت ميفشلش لو الـ AI فيه مشكلة
        quotes = [
            "سبحان الله وبحمده، سبحان الله العظيم. #ذكر",
            "اللهم صل وسلم وبارك على نبينا محمد. #الجمعة",
            "استغفر الله العظيم واتوب إليه. #استغفار"
        ]
        import random
        return random.choice(quotes)

def start_posting():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    wait = WebDriverWait(driver, 30)
    
    try:
        print("🌐 فتح فيسبوك...")
        driver.get("https://mbasic.facebook.com/")
        
        print("🔑 تسجيل الدخول...")
        email_field = wait.until(EC.element_to_be_clickable((By.NAME, "email")))
        email_field.send_keys(FB_EMAIL)
        
        pass_field = driver.find_element(By.NAME, "pass")
        pass_field.send_keys(FB_PASS)
        pass_field.send_keys(Keys.ENTER)
        
        print("⏳ انتطار التحميل...")
        time.sleep(10)

        # فحص لو في قفل أمان
        if "checkpoint" in driver.current_url:
            print("⚠️ فيسبوك طلب تأكيد! افتح موبايلك دلوقتي ووافق.")
            return

        # الخطوة العبقرية: نروح للينك النشر مباشرة عشان منهربش من نسخة mbasic
        print("📝 الانتقال لصفحة النشر المباشرة...")
        driver.get("https://mbasic.facebook.com/composer/")
        time.sleep(5)

        post_content = get_ai_content()
        print(f"🤖 المحتوى المجهز: {post_content}")

        # محاولة الكتابة في صندوق النص
        try:
            # في صفحة الـ composer، الصندوق غالبا بيكون textarea
            textarea = wait.until(EC.presence_of_element_located((By.TAG_NAME, "textarea")))
            textarea.send_keys(post_content)
            print("✅ تم كتابة النص")
            
            # البحث عن زر النشر (بيكون مكتوب عليه "نشر" أو "Post")
            submit_btn = driver.find_element(By.XPATH, "//input[@type='submit' and (@name='view_post' or @value='Post' or @value='نشر')]")
            driver.execute_script("arguments[0].click();", submit_btn)
            
            print("🚀 تم الضغط على زر النشر!")
            time.sleep(5)
            print(f"🏁 المهمة تمت بنجاح.")
            
        except Exception as e:
            print(f"❌ فشل العثور على زر النشر. الرابط الحالي: {driver.current_url}")
            driver.save_screenshot("debug_post.png")
            raise e
            
    except Exception as e:
        print(f"❌ خطأ عام: {e}")
        driver.save_screenshot("final_error.png")
    finally:
        driver.quit()

if __name__ == "__main__":
    start_posting()
