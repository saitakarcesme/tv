package local.tv.cardhome;
import android.content.Context;
import android.graphics.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
final class AmbientScreen extends NatureScreen {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    AmbientScreen(Context c){super(c);p.setShadowLayer(6,0,2,Color.BLACK);}
    @Override protected void drawContent(Canvas c){
        float w=getWidth(),h=getHeight();ZonedDateTime now=ZonedDateTime.now();
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setColor(Color.WHITE);p.setTextSize(h*.19f);
        c.drawText(now.format(DateTimeFormatter.ofPattern("HH:mm")),w/2,h*.51f,p);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(h*.029f);p.setColor(0xffe1e7ef);
        c.drawText(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d",Locale.ENGLISH)),w/2,h*.60f,p);
    }
}
