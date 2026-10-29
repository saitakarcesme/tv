"""Build with Python 3, JDK 17+ and Android SDK platform 34 / build-tools 36."""
from pathlib import Path
import os,subprocess,zipfile,struct,shutil
root=Path(__file__).resolve().parent;build=root/'build';build.mkdir(exist_ok=True)
sdk=Path(os.environ['ANDROID_SDK_ROOT']);bt=Path(os.environ.get('TV_BUILD_TOOLS',sdk/'build-tools/36.0.0'))
android=Path(os.environ.get('TV_ANDROID_JAR',sdk/'platforms/android-34/android.jar'))
java=Path(os.environ['JAVA_HOME'])/'bin'
def run(*args):subprocess.run([str(a) for a in args],check=True)
for d in ['generated','classes','dex']:(build/d).mkdir(exist_ok=True)
run(bt/'aapt2','compile','--dir',root/'res','-o',build/'resources.zip')
run(bt/'aapt2','link','-o',build/'resources.apk','-I',android,'--manifest',root/'AndroidManifest.xml','--java',build/'generated',build/'resources.zip')
run(java/'javac','-J-Xmx128m','-encoding','UTF-8','--release','8','-classpath',android,'-d',build/'classes',*root.glob('src/**/*.java'),*build.glob('generated/**/*.java'))
d8=bt/'lib/d8.jar'
if not d8.exists():d8=bt/'d8.jar'
run(java/'java','-Xmx128m','-cp',d8,'com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',build/'dex',*build.glob('classes/**/*.class'))
apk=build/'tv.apk'
with zipfile.ZipFile(apk,'w') as z:
 def put(name,data,kind=zipfile.ZIP_STORED):
  info=zipfile.ZipInfo(name);info.compress_type=kind
  if kind==zipfile.ZIP_STORED:
   n=(-(z.fp.tell()+30+len(name.encode())+4))%4;info.extra=struct.pack('<HH',0xffff,n)+b'\0'*n
  z.writestr(info,data)
 with zipfile.ZipFile(build/'resources.apk') as source:
  for i in source.infolist():put(i.filename,source.read(i.filename),i.compress_type)
 put('classes.dex',(build/'dex/classes.dex').read_bytes())
 for p in (root/'assets').rglob('*'):
  if p.is_file() and '__pycache__' not in str(p):put('assets/'+str(p.relative_to(root/'assets')),p.read_bytes())
 for p in (root/'lib').rglob('*.so'):put(str(p.relative_to(root)),p.read_bytes(),zipfile.ZIP_STORED if p.name.endswith('.zip.so') else zipfile.ZIP_DEFLATED)
run(bt/'zipalign','-c','4',apk)
key=build/'debug.p12'
if not key.exists():run(java/'keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','tv-debug','-dname','CN=TV Local Development','-keyalg','RSA','-keysize','2048','-validity','3650')
signer=bt/'lib/apksigner.jar'
if not signer.exists():signer=bt/'apksigner.jar'
run(java/'java','-Xmx128m','-jar',signer,'sign','--ks',key,'--ks-pass','pass:android',apk)
run(java/'java','-Xmx128m','-jar',signer,'verify',apk)
print(apk)
