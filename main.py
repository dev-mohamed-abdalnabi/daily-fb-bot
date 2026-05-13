import os
import requests
import random
from moviepy.editor import VideoFileClip, TextClip, CompositeVideoClip, AudioFileClip
from gtts import gTTS

# --- الإعدادات والمفاتيح (بتتسحب من جيت هب) ---
PEXELS_API_KEY = os.getenv('PEXELS_API_KEY')
FB_PAGE_ID = os.getenv('FB_PAGE_ID')
FB_ACCESS_TOKEN = os.getenv('FB_ACCESS_TOKEN')

def get_quran_verse():
    # سحب آية عشوائية من API القرآن
    try:
        verse_num = random.randint(1, 6236)
        res = requests.get(f"https://api.alquran.cloud/v1/ayah/{verse_num}/ar.alafasy").json()
        text = res['data']['text']
        audio_url = res['data']['audio']
        surah = res['data']['surah']['name']
        return text, audio_url, surah
    except:
        return "إِنَّ مَعَ الْعُسْرِ يُسْرًا", None, "الشرح"

def download_pexels_video():
    # تجيب فيديو خلفية مناسب من Pexels
    headers = {'Authorization': PEXELS_API_KEY}
    queries = ['nature', 'clouds', 'sea', 'mountains']
    query = random.choice(queries)
    url = f"https://api.pexels.com/videos/search?query={query}&per_page=1"
    res = requests.get(url, headers=headers).json()
    return res['videos'][0]['video_files'][0]['link']

def create_video(text, audio_url, bg_video_url, surah):
    # تحميل الملفات وعمل المونتاج
    open("quran_audio.mp3", "wb").write(requests.get(audio_url).content)
    open("bg_video.mp4", "wb").write(requests.get(bg_video_url).content)
    
    audio = AudioFileClip("quran_audio.mp3")
    video = VideoFileClip("bg_video.mp4").subclip(0, audio.duration).resize(height=1920) # أبعاد الريلز
    video = video.set_audio(audio)
    
    # كتابة النص (تأكد من وجود الخط Amiri-Regular.ttf في المشروع)
    txt_clip = TextClip(text, fontsize=70, color='white', font='Amiri-Regular.ttf', 
                        method='caption', size=(video.w*0.8, None)).set_duration(audio.duration).set_position('center')
    
    surah_clip = TextClip(f"سورة {surah}", fontsize=40, color='yellow', font='Amiri-Regular.ttf').set_duration(audio.duration).set_position(('center', 1500))
    
    final_video = CompositeVideoClip([video, txt_clip, surah_clip])
    final_video.write_videofile("output_reels.mp4", fps=24, codec="libx264")

def upload_to_facebook(video_path, caption):
    print("🚀 جاري الرفع لفيسبوك ريلز...")
    url = f"https://graph.facebook.com/v19.0/{FB_PAGE_ID}/videos"
    payload = {
        'description': caption,
        'access_token': FB_ACCESS_TOKEN
    }
    files = {'source': open(video_path, 'rb')}
    
    res = requests.post(url, data=payload, files=files)
    if res.status_code == 200:
        print("✅ تم النشر بنجاح على الفيسبوك!")
    else:
        print(f"❌ فشل الرفع: {res.text}")

# تشغيل البوت
if __name__ == "__main__":
    print("🎬 بدء العمل...")
    text, audio, surah = get_quran_verse()
    bg_url = download_pexels_video()
    create_video(text, audio, bg_url, surah)
    upload_to_facebook("output_reels.mp4", f"تلاوة خاشعة من سورة {surah} ✨ #قرآن #اسلام #reels")
    
