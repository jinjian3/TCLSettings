package app.customized.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 闹钟到点：在后台线程里执行一次“检查并结束进程”。只接收本应用自己定的闹钟。 */
public class CleanupReceiver extends BroadcastReceiver {

    static final String EXTRA_ATTEMPT = "attempt";

    @Override
    public void onReceive(Context context, Intent intent) {
        final int attempt = intent == null ? 0 : intent.getIntExtra(EXTRA_ATTEMPT, 0);
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Cleanup.runAuto(app, attempt);
            } finally {
                pending.finish();
            }
        }, "cleanup-auto").start();
    }
}
