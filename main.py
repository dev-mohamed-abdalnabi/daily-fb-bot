import os, requests, random, json, base64, sys, glob
from datetime import datetime
import numpy as np
import moviepy.editor as mp
from moviepy.video.fx.all import loop 
from PIL import Image, ImageFont, ImageDraw
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

# ================== إعدادات الأبعاد ==================
WIDTH = 1080
HEIGHT = 1920

LOG_FILE = "daily_log.txt"

def today_str():
    return datetime.utcnow().strftime("%Y-%m-%d")

def is_uploaded_today():
    if not os.path.exists(LOG_FILE):
        return False
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
    lines = []
    current_line = []
    current_length = 0
    for word in words:
        if current_length + len(word) <= width:
            current_line.append(word)
            current_length += len(word) + 1
        else:
            if current_line: lines.append(" ".join(current_line))
            current_line = [word]
            current_length = len(word) + 1
    if current_line: lines.append(" ".join(current_line))
    return lines

def youtube_authenticate():
    TOKEN_B64 = os.environ.get("TOKEN_BASE64")
    token_data = json.loads(base64.b64decode(TOKEN_B64).decode('utf-8'))
    creds = Credentials.from_authorized_user_info(token_data)
    return build('youtube', 'v3', credentials=creds)

def facebook_upload(video_path, description):
    print("📡 جاري الرفع لصفحة فيسبوك (Reels)...")
    PAGE_ID = os.environ.get("FB_PAGE_ID")
    ACCESS_TOKEN = os.environ.get("FB_ACCESS_TOKEN")
    
    if not PAGE_ID or not ACCESS_TOKEN:
        raise Exception("❌ مفاتيح فيسبوك غير متوفرة!")

    url_init = f"https://graph.facebook.com/v19.0/{PAGE_ID}/video_reels"
    params_init = {'upload_phase': 'start', 'access_token': ACCESS_TOKEN}
    res_init = requests.post(url_init, data=params_init).json()
    
    if 'video_id' not in res_init:
        print(f"❌ فشل البدء: {res_init}")
        raise Exception("Facebook Reel Init Failed")
    
    video_id = res_init['video_id']

    url_upload = f"https://graph.facebook.com/v19.0/{video_id}"
    files = {'video_file_chunk': open(video_path, 'rb')}
    params_upload = {'upload_phase': 'upload', 'access_token': ACCESS_TOKEN, 'start_offset': '0'}
    res_upload = requests.post(url_upload, params=params_upload, files=files).json()

    params_publish = {
        'upload_phase': 'finish',
        'video_id': video_id,
        'description': description,
        'video_state': 'PUBLISHED',
        'access_token': ACCESS_TOKEN
    }
    res_publish = requests.post(url_init, data=params_publish).json()
    
    if res_publish.get('success') or 'id' in res_publish:
        print(f"✅ تم الرفع والنشر على فيسبوك بنجاح!")
    else:
        print(f"❌ خطأ في النشر النهائي: {res_publish}")
        raise Exception("Facebook Reel Publishing Failed")

def fetch_quran_chunk():
    MAX_DURATION = 58.0
    print("⏳ جاري البحث عن مقطع قرآني...")
    while True:
        s_id = random.randint(1, 114)
        try:
            res_audio = requests.get(f"http://api.alquran.cloud/v1/surah/{s_id}/{AUDIO_EDITION}").json()['data']
            res_text_ar = requests.get(f"http://api.alquran.cloud/v1/surah/{s_id}/quran-simple").json()['data']
            res_en = requests.get(f"http://api.alquran.cloud/v1/surah/{s_id}/en.sahih").json()['data']
        except Exception:
            continue
            
        s_name = res_audio['name']
        total_ayahs = len(res_audio['ayahs'])
        start_idx = random.randint(0, total_ayahs - 1)
        audio_clips, text_parts_ar, text_parts_en, current_duration = [], [], [], 0
        
        for i in range(start_idx, total_ayahs):
            a_audio, a_ar, a_en = res_audio['ayahs'][i], res_text_ar['ayahs'][i], res_en['ayahs'][i]
            ar_text = a_ar['text']
            basmala = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ "
            if s_id != 1 and i == 0 and ar_text.startswith(basmala):
                ar_text = ar_text.replace(basmala, "")
            
            f_path = f"temp_{i}.mp3"
            with open(f_path, 'wb') as f: f.write(requests.get(a_audio['audio']).content)
            clip = mp.AudioFileClip(f_path).fx(mp.afx.audio_fadein, 0.02).fx(mp.afx.audio_fadeout, 0.02)
            
            if current_duration + clip.duration > MAX_DURATION:
                clip.close(); os.remove(f_path); break
            else:
                audio_clips.append(clip); text_parts_ar.append(ar_text)
                text_parts_en.append(a_en['text']); current_duration += clip.duration
                
        if len(audio_clips) > 0:
            return audio_clips, text_parts_ar, text_parts_en, current_duration, s_name, start_idx + 1, start_idx + len(audio_clips)
        continue

def build_shorts_video():
    print("🚀 [1/4] تحضير الموارد...")
    audio_clips, text_parts_ar, text_parts_en, dur, s_name, start_ayah, end_ayah = fetch_quran_chunk()
    final_audio = mp.concatenate_audioclips(audio_clips).fx(mp.afx.audio_fadein, 1.0).fx(mp.afx.audio_fadeout, 1.0)
    
    starts = [0.0]
    for clip in audio_clips[:-1]: starts.append(starts[-1] + clip.duration)

    print("🎬 [2/4] اختيار خلفية...")
    PEXELS_API_KEY = os.environ.get("PEXELS_API_KEY")
    headers = {'Authorization': PEXELS_API_KEY}
    query = random.choice(['empty desert nature', 'clouds in sky', 'dark starry night sky', 'mountain landscape empty'])
    v_res = requests.get(f'https://api.pexels.com/videos/search?query={query}&orientation=portrait&per_page=30', headers=headers).json()
    v_url = random.choice([v for v in v_res.get('videos', []) if v.get('duration', 0) >= dur])['video_files'][0]['link']
    with open("bg_v.mp4", "wb") as f: f.write(requests.get(v_url).content)
    
    print("⚙️ [3/4] المونتاج...")
    bg = loop(mp.VideoFileClip("bg_v.mp4").resize(height=HEIGHT).crop(x1=0, y1=0, width=WIDTH, height=HEIGHT), duration=dur).subclip(0, dur)
    dark = mp.ColorClip(size=(WIDTH, HEIGHT), color=(0,0,0), duration=dur).set_opacity(0.35) 

    text_clips = []
    for i in range(len(audio_clips)):
        c_start, c_end = starts[i], starts[i+1] if i < len(starts)-1 else dur
        img = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        count = len(text_parts_ar[i])
        f_size, w_wrap, y_space = (110, 35, 140) if count < 60 else (90, 40, 115) if count < 140 else (75, 45, 95)
        
        ar_lines = safe_wrap(text_parts_ar[i], width=w_wrap)
        en_lines = safe_wrap(text_parts_en[i], width=w_wrap)
        y_off = max(400, (HEIGHT - ((len(ar_lines)*y_space)+50+(len(en_lines)*40))) / 2)
        
        for line in ar_lines:
            d.text((WIDTH/2, y_off), line, font=ImageFont.truetype(FONT_PATH_AR, f_size), fill="white", anchor="mm", stroke_width=4, stroke_fill="black", direction="rtl")
            y_off += y_space
        y_off += 50
        for line in en_lines:
            d.text((WIDTH/2, y_off), line, font=ImageFont.truetype(FONT_PATH_EN, int(f_size*0.45)), fill="#E0E0E0", anchor="mm", stroke_width=2, stroke_fill="black")
            y_off += int(f_size*0.45) + 15
        text_clips.append(mp.ImageClip(np.array(img)).set_start(c_start).set_end(c_end))

    title_img = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
    t_text = f"{s_name}\nالآيات {start_ayah} - {end_ayah}" if start_ayah != end_ayah else f"{s_name}\nآية {start_ayah}"
    ImageDraw.Draw(title_img).multiline_text((WIDTH/2, 220), t_text, font=ImageFont.truetype(FONT_PATH_AR, 110), fill="#FFD700", anchor="mm", align="center", spacing=30, stroke_width=4, stroke_fill="black", direction="rtl")
    
    final = mp.CompositeVideoClip([bg, dark, mp.ImageClip(np.array(title_img)).set_duration(dur)] + text_clips).set_audio(final_audio)
    print("⏳ [4/4] رندر...")
    final.write_videofile("final.mp4", fps=24, codec="libx264", audio_codec="aac", bitrate="8000k", preset="ultrafast", logger=None, threads=4)

    for f in glob.glob("temp_*.mp3"): os.remove(f)
    if os.path.exists("bg_v.mp4"): os.remove("bg_v.mp4")

    # نصوص الرفع
    ayah_range_str = f"{start_ayah}-{end_ayah}" if start_ayah != end_ayah else f"{start_ayah}"
    reciter_names = {'ar.alafasy': 'مشاري العفاسي', 'ar.husary': 'محمود خليل الحصري', 'ar.minshawi': 'محمد صديق المنشاوي'}
    current_reciter = reciter_names.get(AUDIO_EDITION, "الشيخ")
    v_title = f"تلاوة خاشعة تريح القلب 🤍 {s_name} (آية {ayah_range_str}) #shorts #quran"
    v_desc = f"تلاوة من سورة {s_name} بصوت الشيخ {current_reciter}.\n\n#قرآن #تلاوة #quran #راحة_نفسية"

    # الرفع
    try:
        youtube = youtube_authenticate()
        body = {'snippet': {'title': v_title, 'description': v_desc, 'categoryId': '22'}, 'status': {'privacyStatus': 'public'}}
        youtube.videos().insert(part="snippet,status", body=body, media_body=MediaFileUpload("final.mp4", chunksize=-1, resumable=True)).execute()
        print("✅ يوتيوب تم")
    except Exception as e: print(f"⚠️ يوتيوب فشل: {e}")

    try:
        facebook_upload("final.mp4", f"{v_title}\n\n{v_desc}")
        print("✅ فيسبوك تم")
    except Exception as e: print(f"⚠️ فيسبوك فشل: {e}"); raise e

if __name__ == "__main__":
    if not is_uploaded_today() or os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch':
        try:
            build_shorts_video()
            mark_uploaded_today()
        except Exception as e:
            print("🔥 خطأ:", e); sys.exit(1)
        
