import subprocess,re,time,json
TV='10.0.10.149:5555'
def sh(c): return subprocess.run(['adb','-s',TV,'shell',c],capture_output=True).stdout.decode('utf-8','replace').replace('\r','')
def read():
    sh('uiautomator dump /sdcard/ui.xml'); texts=re.findall(r'text="([^"]+)"',sh('cat /sdcard/ui.xml'))
    ids=re.findall(r'onChannelRetuned\(content://android.media.tv/channel/(\d+)\)',sh('logcat -d -s TvInputManagerService'))
    return (ids[-1] if ids else None), texts
seen=set(); start=time.time()
# resume from where we are: DumDum (3040)
for i in range(400):
    sh('logcat -c'); sh('input keyevent KEYCODE_CHANNEL_UP'); time.sleep(1.5)
    cid,texts=read()
    tries=0
    while (cid is None or not texts or not re.fullmatch(r'\d+',texts[0] if texts else '')) and tries<3:
        time.sleep(1.2); cid,texts=read(); tries+=1
    row={'i':i,'id':cid,'num':texts[0] if texts else '','name':texts[1] if len(texts)>1 else '','prog':texts[2] if len(texts)>2 else ''}
    open('ip_scan3.jsonl','a').write(json.dumps(row,ensure_ascii=False)+'\n')
    if cid is None: continue
    key=(cid,row['num'])
    if key in seen: break
    seen.add(key)
open('ip_scan3.done','w').write(str(time.time()-start))
