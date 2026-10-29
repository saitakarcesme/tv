package local.tv.cardhome;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.File;
import java.util.*;

/** One hardware video decoder, local silent clips, released whenever Home leaves foreground. */
final class MotionPreview extends FrameLayout implements TextureView.SurfaceTextureListener {
    interface Branding {void draw(Canvas canvas,int index,float width,float height);}
    private static final String[] FILES={"youtube","prime","netflix","disney","google","playstation"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final TextureView texture;
    private final View overlay;
    private final ImageView hold;
    private Bitmap held;
    private boolean rendered;
    private final Branding branding;
    private MediaPlayer player;
    private Surface surface;
    private boolean active;
    private int selected,index;
    private final int[] cursor=new int[6];
    private final Runnable begin=this::play;
    private final Runnable rotate=()->{if(!active)return;freeze();handler.removeCallbacks(begin);handler.post(begin);};
    private float radius=38;
    MotionPreview(Context context,Branding branding){
        super(context);this.branding=branding;for(int i=0;i<6;i++)cursor[i]=context.getSharedPreferences("preview-rotation",0).getInt(FILES[i],0);setAlpha(0);setClipToOutline(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,getWidth(),getHeight(),radius);}});
        texture=new TextureView(context);texture.setSurfaceTextureListener(this);texture.setOpaque(true);
        addView(texture,new LayoutParams(-1,-1));
        hold=new ImageView(context);hold.setScaleType(ImageView.ScaleType.FIT_XY);hold.setVisibility(INVISIBLE);addView(hold,new LayoutParams(-1,-1));
        overlay=new View(context){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            private Shader gradient;
            @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){gradient=new LinearGradient(0,h*.35f,0,h,new int[]{0x0010151b,0xd9080a10},null,Shader.TileMode.CLAMP);}
            @Override protected void onDraw(Canvas c){paint.setShader(gradient);c.drawRect(0,0,getWidth(),getHeight(),paint);MotionPreview.this.branding.draw(c,index,getWidth(),getHeight());}
        };
        addView(overlay,new LayoutParams(-1,-1));
    }
    void radius(float value){radius=value;invalidateOutline();}
    void choose(int value){
        selected=value;handler.removeCallbacks(begin);handler.removeCallbacks(rotate);animate().cancel();
        freeze();
        if(active)handler.postDelayed(begin,120);
    }
    void resume(int value){active=true;choose(value);}
    void pause(){active=false;handler.removeCallbacks(begin);handler.removeCallbacks(rotate);animate().cancel();setAlpha(0);release();clearHold();rendered=false;}
    private void freeze(){
        if(!rendered||!texture.isAvailable())return;
        if(hold.getVisibility()==VISIBLE){hold.animate().cancel();hold.setAlpha(1);return;}
        try{Bitmap next=texture.getBitmap();if(next!=null){hold.animate().cancel();hold.setImageBitmap(next);hold.setAlpha(1);hold.setVisibility(VISIBLE);if(held!=null)held.recycle();held=next;Log.i("EvVideo","Held frame "+next.getWidth()+"x"+next.getHeight()+" view "+hold.getWidth()+"x"+hold.getHeight());}}catch(RuntimeException ignored){}
    }
    private void clearHold(){hold.animate().cancel();hold.setVisibility(INVISIBLE);hold.setImageDrawable(null);if(held!=null){held.recycle();held=null;}}
    private void release(){
        if(player!=null){player.setOnCompletionListener(null);player.setOnPreparedListener(null);player.setOnInfoListener(null);player.setOnErrorListener(null);player.release();player=null;}
        if(surface!=null){surface.release();surface=null;}
    }
    private void play(){
        if(!active||!texture.isAvailable())return;
        animate().cancel();release();index=selected;
        try{
            MediaPlayer mp=new MediaPlayer();player=mp;surface=new Surface(texture.getSurfaceTexture());mp.setSurface(surface);
            File directory=new File(getContext().getExternalFilesDir(null),"previews/"+FILES[index]);
            File[] cached=directory.listFiles((d,n)->n.endsWith(".mp4"));
            File[] hd=directory.listFiles((d,n)->n.startsWith("hd-slot")&&n.endsWith(".mp4"));
            boolean hasHd=hd!=null&&hd.length>0;if(hasHd)cached=hd;
            ArrayList<String> assets=new ArrayList<>();for(String n:getContext().getAssets().list("clips"))if(n.equals(FILES[index]+".mp4")||n.startsWith(FILES[index]+"-"))assets.add(n);
            if(hasHd)assets.clear();
            Collections.sort(assets);if(cached!=null)Arrays.sort(cached,Comparator.comparing(File::getName));
            int remote=cached==null?0:cached.length,total=remote+assets.size();if(total==0)throw new java.io.IOException("No local clips");int choice=Math.floorMod(cursor[index]++,total);getContext().getSharedPreferences("preview-rotation",0).edit().putInt(FILES[index],cursor[index]).apply();
            if(choice<remote)mp.setDataSource(cached[choice].getAbsolutePath());
            else try(AssetFileDescriptor file=getContext().getAssets().openFd("clips/"+assets.get(choice-remote))){mp.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());}
            Log.i("EvVideo","Clip "+FILES[index]+" "+(choice+1)+"/"+total);
            mp.setVolume(0,0);mp.setLooping(false);mp.setOnCompletionListener(p->{if(p==player&&active){handler.removeCallbacks(rotate);handler.post(rotate);}});
            mp.setOnPreparedListener(p->{
                if(p!=player||!active)return;
                crop(p.getVideoWidth(),p.getVideoHeight());p.start();handler.removeCallbacks(rotate);handler.postDelayed(rotate,22000);
                Log.i("EvVideo","Playing "+FILES[index]+" "+p.getVideoWidth()+"x"+p.getVideoHeight()+" muted");
            });
            mp.setOnInfoListener((p,what,extra)->{
                if(p==player&&active&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){
                    rendered=true;texture.setAlpha(1);overlay.invalidate();animate().alpha(1).setDuration(160).start();hold.animate().alpha(0).setDuration(160).withEndAction(this::clearHold).start();Log.i("EvVideo","First frame "+FILES[index]);
                }
                return false;
            });
            mp.setOnErrorListener((p,what,extra)->{Log.w("EvVideo","Preview unavailable "+FILES[index]+" "+what+":"+extra);release();handler.removeCallbacks(rotate);if(active)handler.postDelayed(rotate,3000);return true;});
            mp.prepareAsync();
        }catch(Exception error){release();Log.w("EvVideo","Preview unavailable: "+error.getClass().getSimpleName());}
    }
    private void crop(int videoW,int videoH){
        if(videoW<=0||videoH<=0||getWidth()==0||getHeight()==0)return;
        float scale=Math.max(getWidth()/(float)videoW,getHeight()/(float)videoH);
        Matrix matrix=new Matrix();matrix.setScale(videoW*scale/getWidth(),videoH*scale/getHeight(),getWidth()/2f,getHeight()/2f);texture.setTransform(matrix);
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){if(active){handler.removeCallbacks(begin);handler.post(begin);}}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){if(player!=null)crop(player.getVideoWidth(),player.getVideoHeight());}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){release();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s){}
}
