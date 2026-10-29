package local.tv.cardhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Offline cinematic home: fixed cards, blended artwork and ambient color. */
public final class MainActivity extends Activity {
    private static final String[] PACKAGES = {"com.amazon.firetv.youtube", "com.amazon.firebat",
        "com.netflix.ninja", "com.disney.disneyplus", "com.amazon.cloud9", "local.tv.ps4hdmi1"};
    private static final String[] LABELS = {"YouTube", "Prime Video", "Netflix", "Disney+", "Google", "PlayStation"};
    private static final int[] BACKGROUNDS = {R.drawable.bg_youtube,R.drawable.bg_prime,R.drawable.bg_netflix,
        R.drawable.bg_disney,R.drawable.bg_google,R.drawable.bg_playstation};
    private static final int[] LOGOS = {R.drawable.logo_youtube,R.drawable.logo_prime,R.drawable.logo_netflix,
        R.drawable.logo_disney,R.drawable.logo_google,R.drawable.logo_playstation};
    private static final int[] TINTS = {0xffed2848,0xff00a6e8,0xffda193d,0xff00a59c,0xff3685e8,0xff376cff};
    private final Panel[] panels = new Panel[6];
    private final float[] weights = new float[6];
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Stage stage;
    private Hero hero;
    private Dock dock;
    private Header header;
    private MotionPreview preview;

    private View saver;
    private boolean showcase;
    private boolean resumed, waking;
    private final Runnable idle = () -> showSaver();
    private void armIdle(){clockHandler.removeCallbacks(idle);if(resumed)clockHandler.postDelayed(idle,120000);}
    private void showSaver(){if(!resumed||saver!=null)return;preview.pause();saver=new AmbientScreen(this);stage.addView(saver);stage.requestLayout();}
    private void hideSaver(){if(saver!=null){stage.removeView(saver);saver=null;preview.resume(selected);}armIdle();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(saver!=null||waking){if(e.getAction()==KeyEvent.ACTION_DOWN){waking=true;hideSaver();}else if(e.getAction()==KeyEvent.ACTION_UP)waking=false;return true;}armIdle();return super.dispatchKeyEvent(e);}
    @Override public boolean dispatchTouchEvent(MotionEvent e){if(saver!=null||waking){if(e.getAction()==MotionEvent.ACTION_DOWN){waking=true;hideSaver();}else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)waking=false;return true;}armIdle();return super.dispatchTouchEvent(e);}
    private final Paint videoLogoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint videoLogoText=new Paint(Paint.ANTI_ALIAS_FLAG);
    private UtilityButton settingsButton, stockHomeButton;
    private ValueAnimator movement;
    private int selected;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if(header!=null)header.invalidate();if(saver!=null)saver.invalidate();
            clockHandler.postDelayed(this,1000-System.currentTimeMillis()%1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);showcase=true;PreviewRefreshJob.schedule(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        selected = Math.max(0, Math.min(5, saved != null ? saved.getInt("selected", 0)
            : getPreferences(MODE_PRIVATE).getInt("selected",0)));
        weights[selected]=1;
        HomeGuard.setAmbientColor(0xff080b12);

        stage=new Stage();
        for(int i=0;i<panels.length;i++) {
            final int index=i;
            Panel panel=new Panel(i);
            panels[i]=panel;
            panel.setId(View.generateViewId());
            panel.setFocusable(true);
            panel.setFocusableInTouchMode(true);
            panel.setClickable(true);
            panel.setContentDescription(i==5 ? "PlayStation, HDMI 1" : LABELS[i]);
            panel.setOnFocusChangeListener((view,focused)->{if(focused) select(index,true);});
            panel.setOnClickListener(view->{if(index==4)openGoogle();else if(index==5)openHdmi();else launch(PACKAGES[index]);});
            panel.setOnKeyListener((view,code,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
                if(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if(index==5&&code==KeyEvent.KEYCODE_DPAD_RIGHT){settingsButton.requestFocus();return true;}
                    int next=Math.max(0,Math.min(5,index+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));
                    panels[next].requestFocus();
                    return true;
                }
                if(code==KeyEvent.KEYCODE_DPAD_DOWN){settingsButton.requestFocus();return true;}
                return code==KeyEvent.KEYCODE_DPAD_UP;
            });
            stage.addView(panel);
        }
        hero=new Hero();stage.addView(hero,0);
        preview=new MotionPreview(this,(canvas,index,width,height)->drawMark(canvas,panels[index],width*.05f,height*.54f,width*.24f,height*.20f,videoLogoPaint,videoLogoText));
        stage.addView(preview,1);dock=new Dock();stage.addView(dock,2);
        header=new Header();stage.addView(header);
        settingsButton=new UtilityButton(false);
        stockHomeButton=new UtilityButton(true);
        stage.addView(settingsButton);stage.addView(stockHomeButton);
        setContentView(stage);
        panels[selected].requestFocus();
    }
    private void select(int index, boolean animate) {
        if(index==selected) return;
        if(movement!=null) movement.cancel();
        selected=index;
        if(preview!=null)preview.choose(index);
        HomeGuard.setAmbientColor(0xff080b12);
        float[] start=weights.clone();
        if(!animate) {
            for(int i=0;i<6;i++) weights[i]=i==selected?1:0;
            stage.invalidate();hero.invalidate();if(dock!=null)dock.invalidate(); return;
        }
        movement=ValueAnimator.ofFloat(0,1);
        movement.setDuration(360);
        movement.setInterpolator(new DecelerateInterpolator(1.4f));
        movement.addUpdateListener(a->{
            float t=(float)a.getAnimatedValue();
            for(int i=0;i<6;i++) weights[i]=start[i]+((i==selected?1f:0f)-start[i])*t;
            stage.invalidate();hero.invalidate();if(dock!=null)dock.invalidate();
            for(Panel panel:panels) panel.invalidate();
        });
        movement.start();
    }
    @Override protected void onResume() {
        super.onResume();resumed=true;waking=false;armIdle();
        HomeGuard.arrived(this);
        if(panels[selected]!=null) panels[selected].requestFocus();
        clockHandler.removeCallbacks(clockTick);
        clockTick.run();
        preview.resume(selected);
        stage.postOnAnimation(()->stage.postOnAnimation(HomeGuard::drawn));
    }
    @Override protected void onPause() {
        resumed=false;clockHandler.removeCallbacks(idle);if(saver!=null){stage.removeView(saver);saver=null;}
        getPreferences(MODE_PRIVATE).edit().putInt("selected",selected).apply();
        if(preview!=null)preview.pause();
        clockHandler.removeCallbacks(clockTick);
        if(movement!=null) movement.cancel();
        for(int i=0;i<6;i++) weights[i]=i==selected?1:0;
        if(stage!=null){stage.invalidate();hero.invalidate();if(dock!=null)dock.invalidate();for(Panel panel:panels)panel.invalidate();}
        super.onPause();
    }
    @Override protected void onDestroy() {
        clockHandler.removeCallbacks(clockTick);
        if(movement!=null) movement.cancel();

        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("selected",selected);super.onSaveInstanceState(state);
    }
    @Override public boolean onKeyDown(int code,KeyEvent event) {
        if(code==KeyEvent.KEYCODE_MENU) {if(event.getRepeatCount()==0) toolsMenu();return true;}
        return super.onKeyDown(code,event);
    }
    @Override public void onBackPressed() {panels[selected].requestFocus();}
    private void openGoogle(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/")).setPackage("com.amazon.cloud9"));}
        catch(RuntimeException error){launch("com.amazon.cloud9");}
    }
    private void openHdmi(){
        Intent helper=getPackageManager().getLaunchIntentForPackage(PACKAGES[5]);
        if(helper!=null){startActivity(helper);return;}
        android.media.tv.TvInputManager manager=(android.media.tv.TvInputManager)getSystemService(TV_INPUT_SERVICE);
        ArrayList<android.media.tv.TvInputInfo> inputs=new ArrayList<>();
        for(android.media.tv.TvInputInfo input:manager.getTvInputList())if(input.getType()==android.media.tv.TvInputInfo.TYPE_HDMI)inputs.add(input);
        String saved=getPreferences(MODE_PRIVATE).getString("hdmi-input","");
        for(android.media.tv.TvInputInfo input:inputs)if(input.getId().equals(saved)){tune(input.getId());return;}
        if(inputs.isEmpty()){Toast.makeText(this,"No HDMI input exposed by this TV",Toast.LENGTH_LONG).show();return;}
        String[] labels=new String[inputs.size()];for(int i=0;i<labels.length;i++)labels[i]=inputs.get(i).loadLabel(this).toString();
        new AlertDialog.Builder(this).setTitle("Choose console input").setItems(labels,(d,i)->{String id=inputs.get(i).getId();getPreferences(MODE_PRIVATE).edit().putString("hdmi-input",id).apply();tune(id);}).show();
    }
    private void tune(String id){
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.media.tv.TvContract.buildChannelUriForPassthroughInput(id)));}
        catch(RuntimeException e){getPreferences(MODE_PRIVATE).edit().remove("hdmi-input").apply();Toast.makeText(this,"TV could not open this input",Toast.LENGTH_LONG).show();}
    }
    private void launch(String pkg) {
        Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if(intent==null) intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null) {
            new AlertDialog.Builder(this).setMessage("This app is not installed.")
                .setPositiveButton("Open store",(dialog,which)->{
                    try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("amzn://apps/android?p="+pkg)));}
                    catch(RuntimeException error){Toast.makeText(this,"Could not open store",Toast.LENGTH_SHORT).show();}
                }).setNegativeButton("Cancel",null).show();
            return;
        }
        try {startActivity(intent);} catch(RuntimeException error) {
            Toast.makeText(this,"Could not open app",Toast.LENGTH_SHORT).show();
        }
    }
    private void toolsMenu() {
        new AlertDialog.Builder(this).setItems(new String[]{"TV settings","All apps","Amazon Home","Ambient clock"},(dialog,item)->{
            try {
                if(item==0)openTvSettings();
                if(item==1)allApps();
                if(item==2)HomeGuard.openStock(this,false);
                if(item==3)showSaver();
            }catch(RuntimeException error){Toast.makeText(this,"Could not open screen",Toast.LENGTH_SHORT).show();}
        }).show();
    }
    private void openTvSettings(){HomeGuard.openStock(this,true);}
    private void allApps() {
        ArrayList<ResolveInfo> apps=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            for(ResolveInfo app:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))
                if(!app.activityInfo.packageName.equals(getPackageName())&&seen.add(app.activityInfo.packageName))apps.add(app);
        apps.sort(Comparator.comparing(a->a.loadLabel(getPackageManager()).toString(),String.CASE_INSENSITIVE_ORDER));
        String[] labels=new String[apps.size()];
        for(int i=0;i<apps.size();i++)labels[i]=apps.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("Apps").setItems(labels,
            (dialog,index)->launch(apps.get(index).activityInfo.packageName)).show();
    }

    private final class Header extends View {
        private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Locale locale=Locale.ENGLISH;
        private final DateTimeFormatter hour=DateTimeFormatter.ofPattern("HH:mm:ss",locale);
        private final DateTimeFormatter date=DateTimeFormatter.ofPattern("d MMMM yyyy · EEEE",locale);
        Header(){super(MainActivity.this);type.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));}
        private void label(Canvas c,String text,float x,float y,float size,int color){type.setTextSize(size);type.setColor(color);c.drawText(text,x,y,type);}
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(stage.unit,stage.unit);float width=getWidth()/stage.unit;
            Instant now=Instant.now();ZonedDateTime local=now.atZone(ZoneId.systemDefault());
            label(c,hour.format(local),36,46,30,0xfff4f6fb);
            type.setShadowLayer(4,0,1,Color.BLACK);
            type.setTextSize(26);String day=date.format(local);
            type.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            label(c,day,width-36-type.measureText(day),44,26,0xfff0f4fb);
            c.restore();
        }
    }
    private final class Stage extends FrameLayout {
        float unit=1;
        private final Paint ambient=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
        private final Shader[] edgeGlow=new Shader[6],cardGlow=new Shader[6];
        Stage(){super(MainActivity.this);setWillNotDraw(false);setClipChildren(false);}
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(w,h);unit=Math.min(w/1920f,h/1080f);
            int cardW=Math.round((w-108*unit-5*18*unit-152*unit)/6),cardH=Math.round(cardW*9f/16f);
            for(Panel panel:panels)panel.measure(MeasureSpec.makeMeasureSpec(cardW,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(cardH,MeasureSpec.EXACTLY));
            for(View view:new View[]{hero,preview})view.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
            dock.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
            header.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(64*unit),MeasureSpec.EXACTLY));
            if(saver!=null)saver.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
            for(View button:new View[]{settingsButton,stockHomeButton})button.measure(MeasureSpec.makeMeasureSpec(Math.round(58*unit),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(58*unit),MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            int x=0,y=0;
            for(View view:new View[]{hero,preview})view.layout(x,y,x+view.getMeasuredWidth(),y+view.getMeasuredHeight());
            if(saver!=null)saver.layout(0,0,getWidth(),getHeight());
            dock.layout(0,0,getWidth(),getHeight());
            preview.radius(0);header.layout(0,0,getWidth(),header.getMeasuredHeight());
            for(int i=0;i<6;i++){
                int px=Math.round(54*unit)+i*(panels[i].getMeasuredWidth()+Math.round(18*unit)),py=Math.round(892*unit);
                panels[i].layout(px,py,px+panels[i].getMeasuredWidth(),py+panels[i].getMeasuredHeight());
            }
            int size=Math.round(58*unit),by=Math.round(936*unit);
            int hx=getWidth()-Math.round(54*unit)-size,sx=hx-Math.round(70*unit);
            settingsButton.layout(sx,by,sx+size,by+size);stockHomeButton.layout(hx,by,hx+size,by+size);
        }
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
            for(int i=0;i<6;i++){
                int rgb=TINTS[i]&0xffffff;
                edgeGlow[i]=new RadialGradient(w*.68f,h*.48f,w*.36f,new int[]{0x94000000|rgb,0x25000000|rgb,0x00000000|rgb},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
                cardGlow[i]=new RadialGradient(w*(.087f+i*.151f),h*.9f,w*.20f,new int[]{0xb0000000|rgb,0x4a000000|rgb,0x10000000|rgb,0x00000000|rgb},new float[]{0,.28f,.70f,1},Shader.TileMode.CLAMP);
            }
        }
        @Override protected void onDraw(Canvas c){
            c.drawColor(0xff070a10);
            for(int i=0;i<6;i++)if(weights[i]>.001f){
                ambient.setAlpha(Math.round(weights[i]*255));ambient.setShader(edgeGlow[i]);c.drawRect(0,0,getWidth(),getHeight(),ambient);
                ambient.setShader(cardGlow[i]);c.drawRect(0,0,getWidth(),getHeight(),ambient);
            }
        }
    }
    private final class Dock extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Dock(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(stage.unit,stage.unit);float width=getWidth()/stage.unit;
            p.setShader(new LinearGradient(0,0,0,140,new int[]{0xaa000000,0x00000000},null,Shader.TileMode.CLAMP));c.drawRect(0,0,width,140,p);p.setShader(null);
            p.setColor(0xeb101720);c.drawRoundRect(32,874,width-32,1058,30,30,p);
            p.setColor(0x28ffffff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);c.drawRoundRect(32,874,width-32,1058,30,30,p);p.setStyle(Paint.Style.FILL);c.restore();
        }
    }
    private final class UtilityButton extends View {
        private final boolean home;
        private final Paint icon=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape=new Path();
        UtilityButton(boolean home){
            super(MainActivity.this);this.home=home;
            setId(View.generateViewId());setFocusable(true);setClickable(true);
            setContentDescription(home?"Amazon Home":"TV settings");
            setOnFocusChangeListener((v,f)->invalidate());
            setOnClickListener(v->{if(home)HomeGuard.openStock(MainActivity.this,false);else openTvSettings();});
            setOnKeyListener((v,key,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
                if(key==KeyEvent.KEYCODE_DPAD_UP){panels[selected].requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(home)settingsButton.requestFocus();else panels[5].requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){stockHomeButton.requestFocus();return true;}
                return key==KeyEvent.KEYCODE_DPAD_DOWN;
            });
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");
        }
        @Override protected void onDraw(Canvas c){
            c.save();c.scale(getWidth()/64f,getHeight()/64f);
            icon.setStyle(Paint.Style.FILL);icon.setColor(isFocused()?0xfff5f6f8:0x18f5f5f5);
            c.drawCircle(32,32,31,icon);
            icon.setColor(isFocused()?0xff202329:0xbbe0e2e5);icon.setStrokeWidth(2.7f);
            icon.setStrokeCap(Paint.Cap.ROUND);icon.setStrokeJoin(Paint.Join.ROUND);icon.setStyle(Paint.Style.STROKE);
            if(home){
                shape.reset();shape.moveTo(16,30);shape.lineTo(32,17);shape.lineTo(48,30);
                shape.moveTo(21,28);shape.lineTo(21,46);shape.lineTo(29,46);shape.lineTo(29,36);
                shape.lineTo(36,36);shape.lineTo(36,46);shape.lineTo(43,46);shape.lineTo(43,28);c.drawPath(shape,icon);
            }else{
                c.drawCircle(32,32,11,icon);c.drawCircle(32,32,4,icon);
                for(int k=0;k<8;k++){c.save();c.rotate(k*45,32,32);c.drawLine(32,16,32,21,icon);c.restore();}
            }
            c.restore();
        }
    }
    private final class Panel extends View {
        private final int index;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds=new RectF(),imageBounds=new RectF();
        private final Path clip=new Path();
        private final Bitmap background,logo,cardLogo;
        private android.graphics.drawable.Drawable official;
        private final ColorFilter white=new PorterDuffColorFilter(Color.WHITE,PorterDuff.Mode.SRC_IN);
        private float w,h;
        Panel(int index){
            super(MainActivity.this);this.index=index;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            background=null;
            logo=LOGOS[index]==0?null:BitmapFactory.decodeResource(getResources(),LOGOS[index],options);
            cardLogo=logo;
            try {
                if(index==4||index==5)official=null;
                else {official=getPackageManager().getApplicationBanner(PACKAGES[index]);if(official==null)official=getPackageManager().getApplicationIcon(PACKAGES[index]);}
            } catch(Exception ignored){}
            type.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");
            info.setSelected(index==selected);
        }
        @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
            w=width/stage.unit;h=height/stage.unit;
            bounds.set(0,0,w,h);clip.reset();clip.addRoundRect(bounds,26,26,Path.Direction.CW);

        }
        @Override protected void onDraw(Canvas c){
            if(w<=0||h<=0)return;
            float amount=weights[index];
            // Static card bounds: focus changes opacity, never triggers a relayout.
            c.save();c.scale(stage.unit,stage.unit);c.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);paint.setColorFilter(null);paint.setAlpha(255);
            // Draw the publisher's complete asset, including its own background.
            // No fabricated brand color, stretching or logo overlay.
            if(index>=4){
                RectF tile=new RectF(6,6,w-6,h-6);
                if(index==4)paint.setColor(Color.WHITE);
                else paint.setShader(new LinearGradient(6,6,w,h,new int[]{0xff007fc4,0xff00448c,0xff002b66},null,Shader.TileMode.CLAMP));
                c.drawRoundRect(tile,18,18,paint);paint.setShader(null);
                drawMark(c,this,w*.12f,h*.22f,w*.76f,h*.56f,paint,type);
                if(isFocused()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(Color.WHITE);c.drawRoundRect(2,2,w-2,h-2,22,22,paint);}
                paint.setStyle(Paint.Style.FILL);c.restore();return;
            }
            float aw=official==null?cardLogo.getWidth():Math.max(1,official.getIntrinsicWidth());
            float ah=official==null?cardLogo.getHeight():Math.max(1,official.getIntrinsicHeight());
            float scale=Math.min((w-12)/aw,(h-12)/ah),lw=aw*scale,lh=ah*scale;
            RectF tile=new RectF((w-lw)/2,(h-lh)/2,(w+lw)/2,(h+lh)/2);
            Path mask=new Path();mask.addRoundRect(tile,18,18,Path.Direction.CW);
            c.save();c.clipPath(mask);
            if(official!=null){official.setBounds(Math.round(tile.left),Math.round(tile.top),Math.round(tile.right),Math.round(tile.bottom));official.draw(c);}
            else c.drawBitmap(cardLogo,null,tile,paint);
            c.restore();
            if(isFocused()){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setColor(Color.WHITE);paint.setAlpha(240);
                c.drawRoundRect(tile.left-4,tile.top-4,tile.right+4,tile.bottom+4,22,22,paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);c.restore();
        }
    }
    private final class Hero extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG);
        Hero(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            // A neutral first-load surface, never the retired static posters.
            c.drawColor(0xff080d16);

        }
    }
    private static void crop(Bitmap image,float w,float h,int index,RectF out){
        float scale=Math.max(w/image.getWidth(),h/image.getHeight());
        float iw=image.getWidth()*scale,ih=image.getHeight()*scale;
        float x=index==0?.77f:index==1?.57f:.50f,y=index==0?.35f:.48f;
        float left=(w-iw)*x,top=(h-ih)*y;out.set(left,top,left+iw,top+ih);
    }
    private void drawMark(Canvas c,Panel panel,float x,float y,float maxW,float maxH,Paint p,Paint text){
        p.setShader(null);p.setAlpha(255);p.setStyle(Paint.Style.FILL);
        if(panel.logo!=null){
            float scale=Math.min(maxW/panel.logo.getWidth(),maxH/panel.logo.getHeight());
            float w=panel.logo.getWidth()*scale,h=panel.logo.getHeight()*scale;
            p.setColorFilter(null);
            c.save();c.translate(x+(maxW-w)/2,y+(maxH-h)/2);c.scale(scale,scale);
            c.drawBitmap(panel.logo,0,0,p);c.restore();p.setColorFilter(null);
        }else{
            text.setColor(Color.WHITE);text.setTextSize(maxH*.72f);text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            c.drawText(LABELS[panel.index],x+(maxW-text.measureText(LABELS[panel.index]))/2,y+maxH*.75f,text);
        }
    }
}
