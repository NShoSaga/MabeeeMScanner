package either.com.mabeeemscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import either.com.mabeeemscanner.BleService;

/**
 * Created by sagashou on 2018/03/08.
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(new Intent(context, BleService.class));
            } else {
                context.startService(new Intent(context, BleService.class));
            }
        }
    }
}
