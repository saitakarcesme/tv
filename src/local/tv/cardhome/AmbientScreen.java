package local.tv.cardhome;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
final class AmbientScreen extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 AmbientScreen(Context c){super(c);}
 @Override protected void onDraw(Canvas c){
  float w=getWidth(),h=getHeight();c.drawColor(0xff080b12);
  p.setShader(new RadialGradient(w*.5f,h*.65f,w*.6f,new int[]{0xff183d69,0xff080b12},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
  ZonedDateTime now=ZonedDateTime.now();p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-light",0));p.setColor(Color.WHITE);p.setTextSize(h*.19f);
  c.drawText(now.format(DateTimeFormatter.ofPattern("HH:mm")),w/2,h*.51f,p);
  p.setTextSize(h*.026f);p.setColor(0xffa9b8cd);c.drawText(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d",Locale.ENGLISH)),w/2,h*.59f,p);
 }
}
