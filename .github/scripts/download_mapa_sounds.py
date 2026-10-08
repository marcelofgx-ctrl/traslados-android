#!/usr/bin/env python3
"""Download the exact Pixabay sound IDs selected for Mapa Trayectos.

Source files are obtained at build time, not replaced with approximations.
Fail the build if a soundtrack cannot be verified.
"""
import html
import json
import re
import shutil
import urllib.request
from pathlib import Path
from html.parser import HTMLParser

DEST = Path("mapatrayectos/src/main/res/raw")
SOUNDS = {
    "sound_shift_start": ("607923", "https://pixabay.com/sound-effects/film-special-effects-ui-notification-607923/"),
    "sound_trip_start": ("443093", "https://pixabay.com/sound-effects/film-special-effects-notification-center-443093/"),
    "sound_uber": ("158193", "https://pixabay.com/sound-effects/film-special-effects-notification-7-158193/"),
    "sound_cancel": ("383749", "https://pixabay.com/sound-effects/film-special-effects-new-notification-027-383749/"),
    "sound_trip_end": ("607920", "https://pixabay.com/sound-effects/film-special-effects-ui-notification-004-607920/"),
}
HEADERS = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/125.0 Safari/537.36", "Accept": "text/html,application/xhtml+xml,*/*"}

class Scripts(HTMLParser):
    def __init__(self):
        super().__init__()
        self.json_scripts = []
        self.in_json = False
    def handle_starttag(self, tag, attrs):
        if tag == "script":
            self.in_json = dict(attrs).get("type") == "application/ld+json"
    def handle_data(self, data):
        if self.in_json: self.json_scripts.append(data)
    def handle_endtag(self, tag):
        if tag == "script": self.in_json = False

def request(url, accept=None):
    headers = dict(HEADERS)
    if accept: headers["Accept"] = accept
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=25) as response:
        return response.read()

def candidates(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("contentUrl", "src", "download_url", "audio_url") and isinstance(v, str) and ".mp3" in v:
                yield v
            yield from candidates(v)
    elif isinstance(obj, list):
        for item in obj: yield from candidates(item)

def audio_urls(page, html_text):
    found=[]
    parser=Scripts()
    parser.feed(html_text)
    for raw in parser.json_scripts:
        try:
            obj=json.loads(raw)
            found.extend(candidates(obj))
        except json.JSONDecodeError: pass
    found.extend(re.findall(r'https?://cdn\.pixabay\.com/(?:download/)?audio/[^"\'\s<>]+?\.mp3(?:\?[^"\'\s<>]*)?', html_text))
    boot=re.search(r'window\.__BOOTSTRAP_URL__\s*=\s*["\']([^"\']+)', html_text)
    if boot:
        try:
            path=html.unescape(boot.group(1))
            obj=json.loads(request("https://pixabay.com"+path,"application/json").decode("utf-8"))
            found.extend(candidates(obj))
        except Exception as e: print("Bootstrap not available:",e)
    cleaned=[]
    for url in found:
        u=html.unescape(url).replace("\\u002F","/").replace("\\/","/")
        if u.startswith("//"): u="https:"+u
        if u.startswith("http") and ".mp3" in u and u not in cleaned: cleaned.append(u)
    return cleaned

def main():
    DEST.mkdir(parents=True, exist_ok=True)
    for name,(pixabay_id,page) in SOUNDS.items():
        print("Retrieving exact Pixabay sound",pixabay_id,flush=True)
        body=request(page).decode("utf-8",errors="replace")
        urls=audio_urls(page,body)
        if not urls:
            raise RuntimeError("No verifiable MP3 URL found for Pixabay sound "+pixabay_id)
        ok=False
        for url in urls:
            try:
                data=request(url,"audio/mpeg,*/*")
                if len(data)<2048 or not (data.startswith(b"ID3") or data[0]==0xff):
                    continue
                target=DEST/(name+".mp3")
                target.write_bytes(data)
                print("Verified",target,"bytes",len(data),flush=True)
                ok=True
                break
            except Exception as e:
                print("Candidate unavailable:",str(e)[:160],flush=True)
        if not ok: raise RuntimeError("Could not download authentic MP3 for sound "+pixabay_id)
    shutil.copyfile(DEST/"sound_uber.mp3",DEST/"sound_cabify.mp3")
    print("All six sound resources verified (Cabify reuses the explicitly selected Uber sound).")

if __name__=="__main__":
    main()
