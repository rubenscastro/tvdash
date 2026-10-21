import subprocess,re,time,json
TV='10.0.10.149:5555'
def sh(c): return subprocess.run(['adb','-s',TV,'shell',c],capture_output=True).stdout.decode('utf-8','replace').replace('\r','')
sh('am force-stop com.tcl.tv'); time.sleep(1)
sh('am start -a android.intent.action.VIEW -n com.tcl.tv/com.tcl.player.TVActivity -d content://android.media.tv/channel/3301'); time.sleep(4)
seen=set(); start=time.time()
for i in range(700):
    sh('logcat -c'); sh('input keyevent KEYCODE_CHANNEL_UP'); time.sleep(1.4)
    sh('uiautomator dump /sdcard/ui.xml'); texts=re.findall(r'text="([^"]+)"',sh('cat /sdcard/ui.xml'))
    ids=re.findall(r'onChannelRetuned\(content://android.media.tv/channel/(\d+)\)',sh('logcat -d -s TvInputManagerService'))
    cid=ids[-1] if ids else None
    row={'i':i,'id':cid,'num':texts[0] if texts else '','name':texts[1] if len(texts)>1 else '','prog':texts[2] if len(texts)>2 else ''}
    open('ip_scan.jsonl','a').write(json.dumps(row,ensure_ascii=False)+'\n')
    key=(cid,row['num'])
    if key in seen and i>5: break
    seen.add(key)
open('ip_scan.done','w').write(str(time.time()-start))
