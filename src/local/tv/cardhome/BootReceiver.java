package local.tv.cardhome;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())){PreviewRefreshJob.schedule(c);HomeGuard.schedule(c);}
    }
}
