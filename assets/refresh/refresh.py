"""Runs entirely on TV. Fetch public official trailers into three bounded local slots."""
import os,sys,json,time,subprocess,fcntl,signal
import urllib.request,xml.etree.ElementTree as ET
from pathlib import Path
root,lib,cache=map(Path,sys.argv[1:4]);cache.mkdir(exist_ok=True)
lock=(cache/'refresh.lock').open('w')
try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except BlockingIOError:sys.exit(0)
statepath=cache/'tv-refresh-hd-state.json'
try:state=json.loads(statepath.read_text())
except (OSError,ValueError):state={}
base=[str(lib/'libpython.so'),str(root/'yt-dlp'),'--cache-dir',str(root/'extractor-cache'),'--socket-timeout','12','--retries','1','--extractor-retries','1','--js-runtimes','node:'+str(lib/'libnode.so'),'--ffmpeg-location',str(root/'bin')]
children=[]
def stop(*args):
 for p in children:
  try:os.killpg(p.pid,signal.SIGTERM)
  except ProcessLookupError:pass
 sys.exit(1)
signal.signal(signal.SIGTERM,stop)
def run(args,timeout):
 p=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,start_new_session=True);children.append(p)
 try:
  out,err=p.communicate(timeout=timeout)
  if p.returncode:raise RuntimeError(err[-1200:])
  return out
 except subprocess.TimeoutExpired:
  os.killpg(p.pid,signal.SIGTERM);p.communicate();raise
 finally:children.remove(p)
channels={'youtube':'YouTube','prime':'PrimeVideo','netflix':'Netflix','disney':'DisneyPlus','google':'GoogleEarth','playstation':'PlayStation'}
success=0;attempted=0
for app,channel in channels.items():
 prior=state.get(app,{})
 if time.time()-prior.get('checked',0)<20*3600 and (app!='playstation' or prior.get('content_policy')=='gameplay-v1'):success+=1;continue
 attempted+=1
 try:
  if app=='playstation':
   with urllib.request.urlopen('https://www.youtube.com/feeds/videos.xml?channel_id=UC-2Y8dQb0S6DtpxNgAKoJKA',timeout=20) as response:xml=ET.fromstring(response.read(1000000))
   ns={'a':'http://www.w3.org/2005/Atom','yt':'http://www.youtube.com/xml/schemas/2015'}
   entries=[{'id':e.findtext('yt:videoId',namespaces=ns),'title':e.findtext('a:title',default='',namespaces=ns)} for e in xml.findall('a:entry',ns)]
   entries=[e for e in entries if e['id'] and 'gameplay' in e['title'].lower()]
   entries.append({'id':'KsvYQd-axrw','title':'Gran Turismo 7 - Deep Forest Raceway Gameplay | PS5, PS4'})
  else:
   feed=json.loads(run(base+['--flat-playlist','--playlist-end','12','--dump-single-json','https://www.youtube.com/@'+channel+'/videos'],45))
   entries=[e for e in feed.get('entries',[]) if e.get('id') and (e.get('duration') or 0)>=25]
   if app in ('prime','netflix','disney'):entries=[e for e in entries if 'trailer' in e.get('title','').lower()]
  known=prior.get('ids',[]);pending=[e for e in entries if e['id'] not in known]
  if pending:
   e=pending[0];vid=e['id']
   if len(vid)!=11 or not all(c.isalnum() or c in '_-' for c in vid):raise ValueError('Invalid video id')
   folder=cache/app;folder.mkdir(exist_ok=True);staging=folder/'incoming';staging.mkdir(exist_ok=True);incoming=staging/'clip.mp4'
   # Stream-copy 18 seconds of video-only H264: no TV re-encoding workload.
   run(base+['--no-playlist','--force-overwrites','--no-part','--max-filesize','32M','-f','bv[height>=1080][height<=1080][vcodec^=avc1][ext=mp4]','--download-sections','*8-26','-o',str(incoming),'https://www.youtube.com/watch?v='+vid],90)
   if not incoming.exists() or incoming.stat().st_size<10000 or incoming.stat().st_size>40000000:raise ValueError('Clip size outside limit')
   probe=json.loads(run([str(root/'bin/ffprobe'),'-v','error','-show_streams','-show_format','-of','json',str(incoming)],15))
   streams=probe.get('streams',[])
   if any(x.get('codec_type')=='audio' for x in streams) or not any(x.get('codec_type')=='video' and x.get('codec_name')=='h264' for x in streams):raise ValueError('Expected silent H264')
   if not any(x.get('codec_type')=='video' and x.get('width',0)>=1920 and x.get('height',0)>=1080 for x in streams):raise ValueError('1080p source required')
   if not 5<float(probe.get('format',{}).get('duration',0))<45:raise ValueError('Invalid clip duration')
   slot=(prior.get('slot',-1)+1)%3;incoming.replace(folder/('hd-slot'+str(slot)+'.mp4'))
   if app=='playstation' and prior.get('content_policy')!='gameplay-v1':
    for old in folder.glob('*.mp4'):
     if old.name!='hd-slot'+str(slot)+'.mp4':old.unlink()
    prior['content_policy']='gameplay-v1'
   prior.update(slot=slot,ids=(known+[vid])[-60:],title=e['title'],url='https://www.youtube.com/watch?v='+vid,updated=time.time())
   print(app,'UPDATED',e['title'],flush=True)
  else:print(app,'No new trailer',flush=True)
  prior['checked']=time.time();state[app]=prior
  temp=statepath.with_suffix('.pending');temp.write_text(json.dumps(state,ensure_ascii=False,indent=2));temp.replace(statepath);success+=1
 except Exception as e:print(app,'KEEP CACHE:',str(e)[-1200:],flush=True)
print('COMPLETE',success,'of',len(channels),flush=True)
sys.exit(0 if success==len(channels) else 1)
