import os, requests, random, sys, glob
from datetime import datetime
import numpy as np
import moviepy.editor as mp
from moviepy.video.fx.all import loop 
from PIL import Image, ImageFont, ImageDraw

# ================== إعدادات الأبعاد ==================
WIDTH = 1080
HEIGHT = 1920
LOG_FILE = "daily_log.txt"

def today_str():
    return datetime.utcnow().strftime("%Y-%m-%d")

def is_uploaded_today():
    if not os.path.exists(LOG_FILE): return False
    with open(LOG_FILE, "r", encoding="utf-8") as f:
        return f.read().strip() == today_str()

def mark_uploaded_today():
    with open(LOG_FILE, "w", encoding="utf-8") as f:
        f.write(today_str())

# ================== إعدادات الشيوخ والخطوط ==================
RECITERS = ['ar.alafasy', 'ar.husary', 'ar.minshawi']
AUDIO_EDITION = random.choice(RECITERS)
FONT_PATH_AR = "ArabicFont.ttf" 
FONT_PATH_EN = "Roboto-Regular.ttf"

def safe_wrap(text, width):
    words = text.split()
    lines, current_line, current_length = [], [], 0
    for word in words:
        if current_length + len(word) <= width:
            current_line.append(word)
            current_length += len(word) + 1
        else:
            if current_line: lines.append(" ".join(current_line))
            current_line, current_length = [word], len(word) + 1
    if current_line: lines.append(" ".join(current_line))
    return lines

def facebook_upload(video_path, description):
    print("📡 جاري الرفع لفيسبوك (الطريقة المباشرة)...")
    PAGE_ID = os.environ.get("FB_PAGE_ID")
    ACCESS_TOKEN = os.environ.get("FB_ACCESS_TOKEN")
    
    if not PAGE_ID or not ACCESS_TOKEN:
        raise Exception("❌ مفاتيح فيسبوك غير متوفرة!")

    url = f"https://graph.facebook.com/v19.0/{PAGE_ID}/videos"
    payload = {'description': description, 'access_token': ACCESS_TOKEN}
    
    try:
        with open(video_path, 'rb') as f:
            files = {'source': f}
            response = requests.post(url, data=payload, files=files)
            res = response.json()
        if 'id' in res:
            print(f"✅ تم الرفع للفيسبوك بنجاح! ID: {res['id']}")
        else:
            print(f"❌ فشل الرفع: {res}")
            raise Exception("Facebook Upload Failed")
    except Exception as e:
        print(f"⚠️ خطأ أثناء الاتصال بفيسبوك: {e}")
        raise e

def fetch_quran_chunk():
    MAX_DURATION = 58.0
    print("⏳ جاري البحث عن مقطع قرآني...")
    while True:
        s_id = random.randint(1, 114)
        try:
            res_audio = requests.get(f"http://api.alquran.cloud/v1/surah/{s_id}/{AUDIO_EDITION}").json()['data']
            res_text_ar = requests.get(f"http://api.alquran.cloud/v1/surah/{s_id}/quran-simple").json()['data']
            res_en = requests.get(f"http://api.alquran.cloud/v1/surah/{s_id}/en.sahih").json()['data']
        except: continue
            
        s_name, total_ayahs = res_audio['name'], len(res_audio['ayahs'])
        start_idx = random.randint(0, total_ayahs - 1)
        audio_clips, text_parts_ar, text_parts_en, current_duration = [], [], [], 0
        
        for i in range(start_idx, total_ayahs):
            a_audio, a_ar, a_en = res_audio['ayahs'][i], res_text_ar['ayahs'][i], res_en['ayahs'][i]
            ar_text = a_ar['text']
            if s_id != 1 and i == 0: ar_text = ar_text.replace("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ", "")
            
            f_path = f"temp_{i}.mp3"
            with open(f_path, 'wb') as f: f.write(requests.get(a_audio['audio']).content)
            clip = mp.AudioFileClip(f_path).fx(mp.afx.audio_fadein, 0.02).fx(mp.afx.audio_fadeout, 0.02)
            
            if current_duration + clip.duration > MAX_DURATION:
                clip.close(); os.remove(f_path); break
            else:
                audio_clips.append(clip); text_parts_ar.append(ar_text)
                text_parts_en.append(a_en['text']); current_duration += clip.duration
        if audio_clips:
            return audio_clips, text_parts_ar, text_parts_en, current_duration, s_name, start_idx + 1, start_idx + len(audio_clips)

def build_shorts_video():
    print("🚀 [1/4] تحضير الموارد...")
    audio_clips, text_parts_ar, text_parts_en, dur, s_name, start_ayah, end_ayah = fetch_quran_chunk()
    final_audio = mp.concatenate_audioclips(audio_clips).fx(mp.afx.audio_fadein, 1.0).fx(mp.afx.audio_fadeout, 1.0)
    
    starts = [0.0]
    for clip in audio_clips[:-1]: starts.append(starts[-1] + clip.duration)

    print("🎬 [2/4] اختيار خلفية...")
    PEXELS_API_KEY = os.environ.get("PEXELS_API_KEY")
    query = random.choice(['nature clouds', 'starry sky', 'mountain landscape', 'ocean aerial', 'desert nature'])
    
    # تحسين منطق جلب الفيديو لتجنب خطأ الـ index
    v_res = requests.get(f'https://api.pexels.com/videos/search?query={query}&orientation=portrait&per_page=30', headers={'Authorization': PEXELS_API_KEY}).json()
    videos = v_res.get('videos', [])
    
    if not videos:
        raise Exception(f"لم يتم العثور على أي فيديوهات للبحث: {query}")

    # محاولة إيجاد فيديو أطول من الصوت، ولو مفيش ناخد أطول واحد متاح
    valid_videos = [v for v in videos if v.get('duration', 0) >= dur]
    selected_video = random.choice(valid_videos) if valid_videos else max(videos, key=lambda x: x.get('duration', 0))
    
    v_url = selected_video['video_files'][0]['link']
    with open("bg_v.mp4", "wb") as f: f.write(requests.get(v_url).content)
    
    print("⚙️ [3/4] المونتاج...")
    bg = loop(mp.VideoFileClip("bg_v.mp4").resize(height=HEIGHT).crop(x1=0, y1=0, width=WIDTH, height=HEIGHT), duration=dur).subclip(0, dur)
    dark = mp.ColorClip(size=(WIDTH, HEIGHT), color=(0,0,0), duration=dur).set_opacity(0.35) 

    text_clips = []
    for i in range(len(audio_clips)):
        c_start, c_end = starts[i], starts[i+1] if i < len(starts)-1 else dur
        img = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        count, f_size = len(text_parts_ar[i]), 110
        if count > 60: f_size = 90
        if count > 140: f_size = 75
        
        ar_lines = safe_wrap(text_parts_ar[i], width=35 if count < 60 else 45)
        en_lines = safe_wrap(text_parts_en[i], width=40)
        y_off = 600
        for line in ar_lines:
            d.text((WIDTH/2, y_off), line, font=ImageFont.truetype(FONT_PATH_AR, f_size), fill="white", anchor="mm", stroke_width=4, stroke_fill="black", direction="rtl")
            y_off += f_size + 30
        y_off += 50
        for line in en_lines:
            d.text((WIDTH/2, y_off), line, font=ImageFont.truetype(FONT_PATH_EN, int(f_size*0.45)), fill="#E0E0E0", anchor="mm", stroke_width=2, stroke_fill="black")
            y_off += 40
        text_clips.append(mp.ImageClip(np.array(img)).set_start(c_start).set_end(c_end))

    title_img = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
    t_text = f"{s_name}\nالآيات {start_ayah} - {end_ayah}" if start_ayah != end_ayah else f"{s_name}\nآية {start_ayah}"
    ImageDraw.Draw(title_img).multiline_text((WIDTH/2, 220), t_text, font=ImageFont.truetype(FONT_PATH_AR, 110), fill="#FFD700", anchor="mm", align="center", spacing=30, stroke_width=4, stroke_fill="black", direction="rtl")
    
    final = mp.CompositeVideoClip([bg, dark, mp.ImageClip(np.array(title_img)).set_duration(dur)] + text_clips).set_audio(final_audio)
    print("⏳ [4/4] رندر...")
    final.write_videofile("final.mp4", fps=24, codec="libx264", audio_codec="aac", bitrate="8000k", preset="ultrafast", logger=None, threads=4)

    for f in glob.glob("temp_*.mp3"): os.remove(f)
    if os.path.exists("bg_v.mp4"): os.remove("bg_v.mp4")

    ayah_range = f"{start_ayah}-{end_ayah}" if start_ayah != end_ayah else f"{start_ayah}"
    reciter = {'ar.alafasy': 'مشاري العفاسي', 'ar.husary': 'الحصري', 'ar.minshawi': 'المنشاوي'}.get(AUDIO_EDITION, "الشيخ")
    caption = f"تلاوة خاشعة من سورة {s_name} (آية {ayah_range}) بصوت الشيخ {reciter} 🤍 #قرآن #reels #quran"
    facebook_upload("final.mp4", caption)

if __name__ == "__main__":
    if not is_uploaded_today() or os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch':
        try:
            build_shorts_video()
            mark_uploaded_today()
        except Exception as e:
            print("🔥 خطأ:", e); sys.exit(1)
    
