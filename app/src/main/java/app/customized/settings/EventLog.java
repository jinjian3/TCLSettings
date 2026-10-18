package app.customized.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 自动执行的记录本：收到开机/唤醒广播、安排、结束结果、重试……都记在这里，
 * 在“内存清理设置”页面底部显示。保存在 SharedPreferences，进程被回收后依然在。
 */
final class EventLog {

    private static final String TAG = "Cleanup";
    private static final String PREFS = "eventlog";
    private static final String K_LINES = "lines";
    private static final int MAX_LINES = 40;

    private EventLog() {}

    static synchronized void add(Context c, String what) {
        Log.i(TAG, what);
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String time = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        String line = time + "  " + what;

        String old = p.getString(K_LINES, "");
        String all = old.isEmpty() ? line : line + "\n" + old; // 新的在最上面
        String[] parts = all.split("\n");
        if (parts.length > MAX_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_LINES; i++) {
                if (i > 0) sb.append('\n');
                sb.append(parts[i]);
            }
            all = sb.toString();
        }
        // 接收器返回后进程可能立刻被回收，所以同步写入
        p.edit().putString(K_LINES, all).commit();
    }

    /** 每行一条，新的在最上面；没有记录时返回空串。 */
    static String lines(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(K_LINES, "");
    }

    static synchronized void clear(Context c) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }
}
