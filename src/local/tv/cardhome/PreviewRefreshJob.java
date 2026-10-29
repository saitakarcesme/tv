package local.tv.cardhome;

import android.app.job.*;
import android.content.*;
import android.os.Process;
import android.util.Log;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

/** Independent, network-constrained downloader process. UI never initializes Python. */
public final class PreviewRefreshJob extends JobService {
    private volatile java.lang.Process child;
    private volatile boolean stopped;
    private static final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean();
    static void schedule(Context c){
        if(!new File(c.getApplicationInfo().nativeLibraryDir,"libpython.so").exists())return;
        JobScheduler jobs=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);
        ComponentName component=new ComponentName(c,PreviewRefreshJob.class);
        android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putInt("schema",2);
        if(jobs.getPendingJob(2501)==null||jobs.getPendingJob(2501).getExtras().getInt("schema",0)!=2)jobs.schedule(new JobInfo.Builder(2501,component).setExtras(extras).setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24*60*60*1000L).setPersisted(true).build());
        jobs.cancel(2502);
    }
    @Override public boolean onStartJob(JobParameters params){
        // Give Android and the launcher time to settle after a cold boot.
        if(android.os.SystemClock.elapsedRealtime()<180000L){new android.os.Handler(getMainLooper()).post(()->jobFinished(params,true));return true;}
        if(!running.compareAndSet(false,true))return false;
        stopped=false;
        new Thread(()->{
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            boolean retry=false;
            try{
                File root=new File(getNoBackupFilesDir(),"preview-runtime-2");root.mkdirs();
                File lib=new File(getApplicationInfo().nativeLibraryDir);
                File ready=new File(root,"ready");
                if(!ready.exists()){
                    unzip(new FileInputStream(new File(lib,"libpython.zip.so")),new File(root,"python"));
                    unzip(new FileInputStream(new File(lib,"libffmpeg.zip.so")),new File(root,"ffmpeg"));
                    unzip(getAssets().open("refresh/ejs.zip"),new File(root,"extra"));
                    copy(getAssets().open("refresh/yt-dlp"),new File(root,"yt-dlp"));
                    copy(getAssets().open("refresh/refresh.py"),new File(root,"refresh.py"));
                    ready.createNewFile();
                }
                if(!new File(root,"node-ready").exists()){
                    unzip(new FileInputStream(new File(lib,"libnode.zip.so")),new File(root,"node"));new File(root,"node-ready").createNewFile();
                }
                copy(getAssets().open("refresh/refresh.py"),new File(root,"refresh.py"));
                if(stopped)return;
                File bin=new File(root,"bin");bin.mkdirs();
                for(String name:new String[]{"ffmpeg","ffprobe"}){
                    File link=new File(bin,name);java.nio.file.Files.deleteIfExists(link.toPath());android.system.Os.symlink(new File(lib,"lib"+name+".so").getPath(),link.getPath());
                }
                File previews=new File(getExternalFilesDir(null),"previews");previews.mkdirs();
                ProcessBuilder builder=new ProcessBuilder(new File(lib,"libpython.so").getPath(),new File(root,"refresh.py").getPath(),root.getPath(),lib.getPath(),previews.getPath());
                Map<String,String> env=builder.environment();
                env.put("LD_LIBRARY_PATH",new File(root,"node/usr/lib").getPath()+":"+new File(root,"python/usr/lib").getPath()+":"+new File(root,"ffmpeg/usr/lib").getPath());
                env.put("PYTHONHOME",new File(root,"python/usr").getPath());env.put("PYTHONPATH",new File(root,"extra").getPath());
                env.put("SSL_CERT_FILE",new File(root,"python/usr/etc/tls/cert.pem").getPath());env.put("TMPDIR",getCacheDir().getPath());
                builder.redirectErrorStream(true);builder.redirectOutput(new File(previews,"refresh.log"));
                Log.i("EvRefresh","TV-native refresh starting; no computer/server required");
                child=builder.start();
                if(!child.waitFor(8,TimeUnit.MINUTES)){child.destroy();if(!child.waitFor(3,TimeUnit.SECONDS))child.destroyForcibly();throw new IOException("Refresh timed out");}
                if(child.exitValue()!=0)throw new IOException("Refresh exit "+child.exitValue());
                getSharedPreferences("tv-preview-refresh",0).edit().putBoolean("seeded",true).apply();
                Log.i("EvRefresh","TV-native refresh completed");
            }catch(Exception error){retry=true;Log.w("EvRefresh","Retaining cached clips",error);}
            finally{child=null;running.set(false);if(!stopped)jobFinished(params,retry);}
        },"Ev-Preview-refresh").start();return true;
    }
    @Override public boolean onStopJob(JobParameters p){stopped=true;java.lang.Process process=child;if(process!=null)process.destroy();return true;}
    private static void copy(InputStream stream,File target)throws IOException{try(InputStream in=stream;OutputStream out=new FileOutputStream(target)){byte[] buf=new byte[32768];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);}}
    private void unzip(InputStream stream,File folder)throws Exception{
        org.json.JSONObject links=new org.json.JSONObject();
        if(folder.getName().equals("python")||folder.getName().equals("ffmpeg")||folder.getName().equals("node")){
            try(InputStream input=getAssets().open("refresh/"+folder.getName()+"-links.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=input.read(b))>=0)bytes.write(b,0,n);links=new org.json.JSONObject(bytes.toString("UTF-8"));
            }
        }
        folder.mkdirs();String prefix=folder.getCanonicalPath()+File.separator;
        try(ZipInputStream zip=new ZipInputStream(stream)){ZipEntry e;while((e=zip.getNextEntry())!=null){
            File f=new File(folder,e.getName());if(!f.getCanonicalPath().startsWith(prefix))throw new IOException("Invalid archive path");
            if(e.isDirectory()){f.mkdirs();continue;}f.getParentFile().mkdirs();
            if(links.has(e.getName())){
                String target=links.getString(e.getName());
                if(!new File(f.getParentFile(),target).getCanonicalPath().startsWith(prefix))throw new IOException("Invalid symlink");
                java.nio.file.Files.deleteIfExists(f.toPath());android.system.Os.symlink(target,f.getPath());continue;
            }
            try(OutputStream out=new FileOutputStream(f)){byte[] b=new byte[32768];int n;while((n=zip.read(b))>=0)out.write(b,0,n);}
        }}
    }
}
