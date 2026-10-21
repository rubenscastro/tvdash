import subprocess,re,time,json
TV='10.0.10.149:5555'
def sh(c): return subprocess.run(['adb','-s',TV,'shell',c],capture_output=True).stdout.decode('utf-8','replace').replace('\r','')
def read():
    sh('uiautomator dump /sdcard/ui.xml'); texts=re.findall(r'text="([^"]+)"',sh('cat /sdcard/ui.xml'))
    ids=re.findall(r'onChannelRetuned\(content://android.media.tv/channel/(\d+)\)',sh('logcat -d -s TvInputManagerService'))
    return (ids[-1] if ids else None), texts
open('antenna_scan.jsonl','w').close()
sh('am force-stop com.tcl.tv'); time.sleep(1)
sh('am start -a android.intent.action.VIEW -d content://android.media.tv/channel/246 -n com.tcl.tv/com.tcl.player.TVActivity'); time.sleep(8)
seen=set(); start=time.time()
for i in range(150):
    sh('logcat -c'); sh('input keyevent KEYCODE_CHANNEL_UP'); time.sleep(2.2)
    cid,texts=read(); tries=0
    while (cid is None or not texts or not re.fullmatch(r'\d+([.-]\d+)?',texts[0])) and tries<3:
        time.sleep(1.3); cid,texts=read(); tries+=1
    row={'i':i,'id':cid,'num':texts[0] if texts else '','name':texts[1] if len(texts)>1 else '','prog':texts[2] if len(texts)>2 else ''}
    open('antenna_scan.jsonl','a').write(json.dumps(row,ensure_ascii=False)+'\n')
    if cid is None: continue
    key=(cid,row['num'])
    if key in seen: break
    seen.add(key)
    try:
        if float(row['num'].replace('-','.'))>=100: break   # left the antenna list (internet channels start at 3xxx / 1xx)
    except: pass
open('antenna_scan.done','w').write(str(time.time()-start))
