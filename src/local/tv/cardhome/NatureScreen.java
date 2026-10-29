package local.tv.cardhome;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.FrameLayout;

/** One muted local nature decoder; foreground remains legible even over a white frame. */
abstract class NatureScreen extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final TextureView texture;
    private MediaPlayer player;
    private Surface surface;
    private boolean visible=true;
    NatureScreen(Context context){
        super(context);setBackgroundColor(0xff080d13);setWillNotDraw(false);
        texture=new TextureView(context);texture.setSurfaceTextureListener(this);texture.setOpaque(false);
        addView(texture,new LayoutParams(-1,-1));
    }
    protected abstract void drawContent(Canvas c);
    @Override protected void dispatchDraw(Canvas c){super.dispatchDraw(c);c.drawColor(0xb3000000);drawContent(c);}
    void pauseMedia(){visible=false;release();}
    void resumeMedia(){visible=true;play();}
    private void release(){if(player!=null){player.release();player=null;}if(surface!=null){surface.release();surface=null;}}
    private void play(){
        if(!visible||!texture.isAvailable()||player!=null)return;
        try{
            MediaPlayer p=new MediaPlayer();player=p;surface=new Surface(texture.getSurfaceTexture());p.setSurface(surface);
            try(AssetFileDescriptor f=getContext().getAssets().openFd("ambient/nature.mp4")){p.setDataSource(f.getFileDescriptor(),f.getStartOffset(),f.getLength());}
            p.setLooping(true);p.setVolume(0,0);
            p.setOnPreparedListener(m->{if(m!=player||!visible)return;crop(m.getVideoWidth(),m.getVideoHeight());m.start();android.util.Log.i("EvNature","Playing "+m.getVideoWidth()+"x"+m.getVideoHeight()+" muted");});
            p.setOnErrorListener((m,w,e)->{release();return true;});p.prepareAsync();
        }catch(Exception e){release();}
    }
    private void crop(int w,int h){if(w<=0||h<=0||getWidth()==0||getHeight()==0)return;float scale=Math.max(getWidth()/(float)w,getHeight()/(float)h);Matrix m=new Matrix();m.setScale(w*scale/getWidth(),h*scale/getHeight(),getWidth()/2f,getHeight()/2f);texture.setTransform(m);}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);visible=visibility==VISIBLE;if(texture!=null){if(visible)play();else release();}}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){play();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){if(player!=null)crop(player.getVideoWidth(),player.getVideoHeight());}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s){release();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s){}
}
