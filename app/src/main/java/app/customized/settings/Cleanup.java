package app.customized.settings;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import java.io.IOException;

/**
 * 开机 / 待机唤醒后的“自动结束进程”。
 *
 * 流程：收到开机或唤醒广播 -> 开关打开的话，30 秒后执行 -> 执行时如果目标进程正在前台，
 * 每 30 秒重试一次，最多 10 次。开关关闭时什么都不做。
 */
final class Cleanup {

    static final long DELAY_MS = 30_000L;
    static final long RETRY_MS = 30_000L;
    static final int MAX_RETRIES = 10;

    private static final int ALARM_REQUEST_CODE = 4711;

    private Cleanup() {}

    /** 收到开机/唤醒广播时调用（在主线程，要快）。 */
    static void onTrigger(Context context, String reason) {
        Context c = context.getApplicationContext();
        EventLog.add(c, "收到：" + reason);
        if (!new CleanupSettings(c).isAuto()) {
            EventLog.add(c, "自动结束未开启，不处理。");
            return;
        }
        schedule(c, DELAY_MS, 0);
        EventLog.add(c, "已安排 " + (DELAY_MS / 1000) + " 秒后检查并结束 " + new CleanupSettings(c).getPkg() + "。");
    }

    /** 定一个闹钟；同一时间只保留最新的一个。 */
    static void schedule(Context c, long delayMs, int attempt) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, CleanupReceiver.class);
        i.putExtra(CleanupReceiver.EXTRA_ATTEMPT, attempt);
        PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        long at = SystemClock.elapsedRealtime() + delayMs;
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } else {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        }
    }

    /** 闹钟到点后执行（在后台线程里调用）。 */
    static void runAuto(Context context, int attempt) {
        Context c = context.getApplicationContext();
        CleanupSettings s = new CleanupSettings(c);
        if (!s.isAuto()) {
            EventLog.add(c, "自动结束：开关已关闭，不执行。");
            return;
        }
        AdbRunner runner = new AdbRunner(s.getHost(), s.getPort(), c.getFilesDir(), null);
        try {
            CleanupLogic.Outcome o = CleanupLogic.run(runner, s.getPkg(), true, 1500);
            EventLog.add(c, "自动结束：" + o.message);
            if (o.shouldRetry()) {
                retryLater(c, attempt, "目标正在前台");
            }
        } catch (LocalAdb.AuthException e) {
            // 没授权：重试只会反复弹窗，没有意义
            EventLog.add(c, "自动结束失败：" + e.getMessage()
                    + " 请先在“内存清理设置”里点“连接测试”，并在电视上点允许。");
        } catch (IOException e) {
            EventLog.add(c, "自动结束失败：" + e.getMessage());
            retryLater(c, attempt, "连接失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void retryLater(Context c, int attempt, String why) {
        if (attempt >= MAX_RETRIES) {
            EventLog.add(c, "已重试 " + MAX_RETRIES + " 次仍未完成，放弃（" + why + "）。");
            return;
        }
        schedule(c, RETRY_MS, attempt + 1);
        EventLog.add(c, why + "，" + (RETRY_MS / 1000) + " 秒后重试（第 " + (attempt + 1) + "/" + MAX_RETRIES + " 次）。");
    }
}
