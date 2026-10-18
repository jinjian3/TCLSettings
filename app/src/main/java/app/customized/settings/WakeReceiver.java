package app.customized.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 待机唤醒完成后，TCL 系统会发出 android.intent.action.STR_RESUME_COMPLETED。
 * 收到后交给 Cleanup 处理（开关关闭时只记一行记录）。
 */
public class WakeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "null" : String.valueOf(intent.getAction());
        String prefix = "android.intent.action.";
        if (action.startsWith(prefix)) action = action.substring(prefix.length());
        String flags = intent == null ? "?" : "0x" + Integer.toHexString(intent.getFlags());
        Cleanup.onTrigger(context, "待机唤醒（" + action + "，flags=" + flags
                + "，pid=" + android.os.Process.myPid() + "）");
    }
}
