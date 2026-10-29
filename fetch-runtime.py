"""Optional ARMv7 runtime bootstrap. Downloads only from pinned upstream distributions.
Requires Python 3 and tar (bsdtar on macOS). Does not connect to a TV.
"""
from pathlib import Path
import hashlib,io,json,subprocess,tempfile,urllib.request,zipfile
root=Path(__file__).resolve().parent;lib=root/'lib/armeabi-v7a';lib.mkdir(parents=True,exist_ok=True);assets=root/'assets/refresh';assets.mkdir(parents=True,exist_ok=True)
def get(url):
 with urllib.request.urlopen(url,timeout=90) as r:return r.read()
def checked(url,sha):
 b=get(url)
 if hashlib.sha256(b).hexdigest()!=sha.strip().split()[0]:raise ValueError('Checksum mismatch: '+url)
 return b
for component in ['library','ffmpeg']:
 url=f'https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/{component}/0.18.1/{component}-0.18.1.aar'
 data=checked(url,get(url+'.sha256').decode())
 with zipfile.ZipFile(io.BytesIO(data)) as z:
  for name in z.namelist():
   if name.startswith('jni/armeabi-v7a/') and name.endswith('.so'):(lib/Path(name).name).write_bytes(z.read(name))
# Verified Termux package versions, hashes and dependency list are committed.
packages=json.loads((root/'third-party/native-preview-runtime/termux-node-packages.json').read_text())
with tempfile.TemporaryDirectory(prefix='tv-runtime-') as tmp:
 tmp=Path(tmp);files={}
 for name,p in packages.items():
  dest=tmp/name;dest.mkdir();archive=dest/'package.deb'
  archive.write_bytes(checked('https://packages.termux.dev/apt/termux-main/'+p['Filename'],p['SHA256']))
  subprocess.run(['tar','-xf',str(archive),'-C',str(dest)],check=True)
  data=next(dest.glob('data.tar.*'));subprocess.run(['tar','-xf',str(data),'-C',str(dest)],check=True)
  prefix=dest/'data/data/com.termux/files'
  if name=='nodejs-lts':(lib/'libnode.so').write_bytes((prefix/'usr/bin/node').read_bytes())
  for f in (prefix/'usr/lib').glob('*'):
   if f.is_file() and '.so' in f.name:files[f.name]=f.resolve()
 links={};seen={}
 with zipfile.ZipFile(lib/'libnode.zip.so','w',zipfile.ZIP_DEFLATED) as z:
  for name,f in sorted(files.items()):
   data=f.read_bytes();sha=hashlib.sha256(data).hexdigest();path='usr/lib/'+name
   if sha in seen:links[path]=seen[sha];data=seen[sha].encode()
   else:seen[sha]=name
   z.writestr(path,data)
 (assets/'node-links.json').write_text(json.dumps(links))
# Generate exact symlink manifests from upstream archives.
for name in ['python','ffmpeg']:
 links={}
 with zipfile.ZipFile(lib/f'lib{name}.zip.so') as z:
  for i in z.infolist():
   if (i.external_attr>>16)&0o170000==0o120000:links[i.filename]=z.read(i.filename).decode()
 (assets/f'{name}-links.json').write_text(json.dumps(links))
sha=next(x.split()[0] for x in (assets/'SHA2-256SUMS').read_text().splitlines() if x.endswith('  yt-dlp'))
(assets/'yt-dlp').write_bytes(checked('https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.26/yt-dlp',sha))
meta=json.loads(get('https://pypi.org/pypi/yt-dlp-ejs/0.8.0/json'));wheel=next(x for x in meta['urls'] if x['filename'].endswith('.whl'))
(assets/'ejs.zip').write_bytes(checked(wheel['url'],wheel['digests']['sha256']))
print('ARMv7 runtime ready. Rebuild to enable TV-native refresh.')
