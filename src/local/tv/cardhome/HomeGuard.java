package local.tv.cardhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.io.*;

/** Home routing using public AlarmManager and accessibility APIs.
 * Alarm approach informed by humblemuzzu/dumbtv (MIT; notice in third-party/).
 * The log reader matches only the Home-key policy line; no log contents are stored.
 */
public final class HomeGuard extends AccessibilityService {
    private static final String STOCK="com.amazon.tv.launcher";
    private static final String STOCK_HOME="com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static HomeGuard instance;
    private static boolean stockAllowed;
    private static int ambientColor=0xff282c31;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private View cover;
    private volatile boolean stopped;
    private java.lang.Process logReader;
    private long lastKey;
    private boolean launchPending;
    private boolean stockVisible;
    private final Runnable uncover=()->{launchPending=false;if(cover!=null){cover.animate().cancel();cover.setVisibility(View.GONE);cover.setAlpha(1f);}};
    private final Runnable launch=()->schedule(this);
    private final BroadcastReceiver wake=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_ON.equals(i.getAction())&&stockVisible&&!stockAllowed)requestHome("wake");
        }
    };
    private static PendingIntent operation(Context c){
        Intent i=new Intent(c,MainActivity.class).setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(c,71,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE,
            ActivityOptions.makeCustomAnimation(c,0,0).toBundle());
    }
    static void schedule(Context c){
        ((AlarmManager)c.getSystemService(ALARM_SERVICE)).setExact(
            AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime(),operation(c));
    }
    static void arrived(Context c){
        stockAllowed=false;
        ((AlarmManager)c.getSystemService(ALARM_SERVICE)).cancel(operation(c));
        if(instance!=null){instance.launchPending=false;instance.ui.removeCallbacks(instance.launch);}
    }
    static void setAmbientColor(int color){
        ambientColor=color;
        if(instance!=null&&instance.cover!=null)instance.cover.setBackgroundColor(color);
    }
    static void drawn(){
        if(instance!=null){
            instance.ui.removeCallbacks(instance.uncover);
            if(instance.cover!=null&&instance.cover.getVisibility()==View.VISIBLE)
                instance.cover.animate().alpha(0f).setDuration(130).withEndAction(instance.uncover).start();
            else instance.uncover.run();
        }
        Log.i("EvHome","home drawn");
    }
    static void openStock(Context c,boolean settings){
        arrived(c);stockAllowed=true;
        if(instance!=null){instance.ui.removeCallbacks(instance.launch);instance.uncover.run();}
        Intent i=settings?new Intent(Settings.ACTION_SETTINGS):new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME).setPackage(STOCK);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{c.startActivity(i);}catch(RuntimeException e){
            if(settings)c.startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else{stockAllowed=false;throw e;}
        }
    }
    @Override protected void onServiceConnected(){
        instance=this;stopped=false;
        cover=new View(this);cover.setBackgroundColor(ambientColor);cover.setVisibility(View.GONE);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);
        lp.windowAnimations=0;((WindowManager)getSystemService(WINDOW_SERVICE)).addView(cover,lp);
        registerReceiver(wake,new IntentFilter(Intent.ACTION_SCREEN_ON));
        new Thread(this::watchHome,"Ev-Home-key").start();
        Log.i("EvHome","service connected");
    }
    private void watchHome(){
        while(!stopped){
            try{
                logReader=new ProcessBuilder("logcat","-b","system","-v","brief","-T","1","ContextImpl:W","*:S").start();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(logReader.getInputStream()))){
                    String line;
                    while(!stopped&&(line=reader.readLine())!=null){
                        if(line.contains("FireTVKeyPolicyManager.handleShortPressOnHome"))ui.post(()->{
                            long now=SystemClock.uptimeMillis();
                            if(now-lastKey<150)return;
                            lastKey=now;stockAllowed=false;requestHome("key");
                        });
                    }
                }
            }catch(IOException e){Log.w("EvHome","Home-key listener restarting");}
            if(!stopped)try{Thread.sleep(2000);}catch(InterruptedException e){return;}
        }
    }
    private void requestHome(String reason){
        if(launchPending)return;
        launchPending=true;
        Log.i("EvHome","home request "+reason);
        if(cover!=null){cover.animate().cancel();cover.setAlpha(1f);cover.setVisibility(View.VISIBLE);}
        ui.removeCallbacks(uncover);ui.postDelayed(uncover,1800);
        ui.removeCallbacks(launch);ui.postDelayed(launch,60);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getPackageName()==null)return;
        String pkg=e.getPackageName().toString();
        if(STOCK.equals(pkg)){
            stockVisible=e.getClassName()!=null&&STOCK_HOME.contentEquals(e.getClassName());
            if(stockVisible&&!stockAllowed)requestHome("window");
        }else if(!pkg.equals("android")&&!pkg.equals("com.android.systemui")
            &&!pkg.equals("com.amazon.tv.settings.v2")){
            stockVisible=false;
            if(!pkg.equals(getPackageName())){
                stockAllowed=false;
                ((AlarmManager)getSystemService(ALARM_SERVICE)).cancel(operation(this));
                ui.removeCallbacks(launch);uncover.run();
            }
        }
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){
        stopped=true;if(logReader!=null)logReader.destroy();
        ui.removeCallbacksAndMessages(null);
        try{unregisterReceiver(wake);}catch(IllegalArgumentException ignored){}
        if(cover!=null)((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(cover);
        if(instance==this)instance=null;
        super.onDestroy();
    }
}
