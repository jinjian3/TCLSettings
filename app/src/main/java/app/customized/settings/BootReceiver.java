package app.customized.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机广播。目前只用来触发“自动结束进程”（开关关闭时只记一行记录）。
 *
 * 原来的“开机自动进入 HDMI”已暂时停用（TCL 自带设置里的“记忆开关机”已能满足大部分场景）。
 * 要恢复：取消下面注释掉的代码，并取消 HdmiSettingsActivity、SwitchReceiver、AndroidManifest.xml 里相关的注释。
 */
public class BootReceiver extends BroadcastReceiver {
    // private static final String TAG = "HdmiSwitch";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "null" : String.valueOf(intent.getAction());
        Cleanup.onTrigger(context, "开机（" + action + "）");

        /*
        Settings s = new Settings(context);
        if (!s.isEnabled()) {
            Log.i(TAG, "boot: auto switch disabled");
            return;
        }
        if (s.currentTargetId() < 0) {
            Log.w(TAG, "boot: source id not set");
            return;
        }
        long delayMs = Math.max(0, s.getDelaySeconds()) * 1000L;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0, new Intent(context, SwitchReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs, pi);
        Log.i(TAG, "boot: scheduled switch in " + delayMs + " ms");
        */
    }
}
