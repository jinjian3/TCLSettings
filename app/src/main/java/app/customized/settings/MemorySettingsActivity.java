package app.customized.settings;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 内存清理设置：本应用通过电视自己的网络 ADB（默认 127.0.0.1:5555）执行 am force-stop，
 * 结束指定进程（默认是 TCL 的 T-UI：com.tcl.cyberui）。
 *
 * 手动按钮和自动功能都只在“目标进程不是默认桌面”时才会结束它。
 */
public class MemorySettingsActivity extends Activity {

    private static final String TAG = "CleanupUi";

    private CleanupSettings settings;
    private EditText hostEdit;
    private EditText portEdit;
    private EditText pkgEdit;
    private Switch autoSwitch;
    private Button testBtn;
    private Button killBtn;
    private TextView resultLog;
    private TextView autoLog;

    private final StringBuilder logText = new StringBuilder();
    private volatile boolean busy = false;

    private interface AdbJob {
        void run(AdbRunner runner) throws IOException, InterruptedException;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new CleanupSettings(this);

        Ui.Page page = new Ui.Page(this);
        page.root.addView(Ui.title(this, "内存清理设置"));
        page.root.addView(Ui.note(this, "原理：本应用通过电视自带的网络 ADB 执行 am force-stop。"
                + "第一次连接时电视会弹出“允许调试”，请点允许（建议勾选“一律允许”）。"));

        hostEdit = Ui.edit(this, settings.getHost(), true);
        portEdit = Ui.numberEdit(this, String.valueOf(settings.getPort()));
        pkgEdit = Ui.edit(this, settings.getPkg(), false);
        page.root.addView(Ui.formRow(this, "本机地址", hostEdit, 300, "（默认 127.0.0.1）"), Ui.matchWrap(this, 12));
        page.root.addView(Ui.formRow(this, "端口", portEdit, 300, "（默认 5555）"));
        page.root.addView(Ui.formRow(this, "要结束的进程", pkgEdit, 460, "（包名）"));

        testBtn = Ui.button(this, "连接测试", v -> runConnectTest());
        killBtn = Ui.button(this, "结束上面的进程（手动）", v -> runKill());
        page.root.addView(Ui.buttonRow(this, testBtn, killBtn), Ui.matchWrap(this, 20));
        page.root.addView(Ui.note(this, "（结束进程只在上面的进程不是默认桌面时才会执行）"));

        autoSwitch = Ui.toggle(this, "启动后自动结束上面的进程");
        autoSwitch.setChecked(settings.isAuto());
        autoSwitch.setOnCheckedChangeListener((b, on) -> {
            saveFields();
            settings.setAuto(on);
            EventLog.add(this, on ? "自动结束：已开启。" : "自动结束：已关闭。");
            refreshAutoLog();
        });
        page.root.addView(autoSwitch, Ui.matchWrap(this, 16));
        page.root.addView(Ui.note(this, "开启后：每次开机、待机唤醒后等 " + (Cleanup.DELAY_MS / 1000)
                + " 秒再执行；同样只在上面的进程不是默认桌面时执行。"
                + "如果它此刻正在前台显示，每 " + (Cleanup.RETRY_MS / 1000) + " 秒重试一次，最多 "
                + Cleanup.MAX_RETRIES + " 次。"));

        page.root.addView(Ui.section(this, "结果日志"), Ui.matchWrap(this, 24));
        resultLog = Ui.result(this, "（点上面的按钮后，结果显示在这里）");
        page.root.addView(resultLog);

        page.root.addView(Ui.section(this, "自动执行记录（最近 40 条，新的在上）"), Ui.matchWrap(this, 24));
        Button refresh = Ui.button(this, "刷新", v -> refreshAutoLog());
        Button clear = Ui.button(this, "清除记录", v -> {
            EventLog.clear(this);
            refreshAutoLog();
        });
        page.root.addView(Ui.buttonRow(this, refresh, clear), Ui.matchWrap(this, 8));
        autoLog = Ui.result(this, "");
        page.root.addView(autoLog, Ui.matchWrap(this, 8));

        page.show();
        testBtn.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAutoLog();
    }

    @Override
    protected void onPause() {
        saveFields();
        super.onPause();
    }

    // ---------------- 行为 ----------------

    private void runConnectTest() {
        runAdb("连接测试", runner -> {
            LocalAdb.Result r = runner.exec("id");
            say("id → " + r.output + "（退出码 " + r.exitCode + "）");
            String home = CleanupLogic.defaultHomePackage(runner);
            say("当前默认桌面：" + (home == null ? "查不出来" : home));
        });
    }

    private void runKill() {
        final String pkg = pkgEdit.getText().toString().trim();
        runAdb("结束进程 " + pkg, runner -> {
            CleanupLogic.Outcome o = CleanupLogic.run(runner, pkg, false, 1500);
            say(o.message);
        });
    }

    /** 在后台线程里执行；同一时间只允许一个任务。 */
    private void runAdb(final String title, final AdbJob job) {
        if (busy) {
            Toast.makeText(this, "上一个操作还没结束", Toast.LENGTH_SHORT).show();
            return;
        }
        final String host = currentHost();
        final int port = currentPort();
        if (port <= 0) {
            Toast.makeText(this, "端口不对", Toast.LENGTH_LONG).show();
            return;
        }
        saveFields();

        busy = true;
        setButtonsEnabled(false);
        say("—— " + title + " ——");
        final AdbRunner runner = new AdbRunner(host, port, getFilesDir(), this::say);
        new Thread(() -> {
            try {
                job.run(runner);
                say("完成");
            } catch (Exception e) {
                say("失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                Log.w(TAG, "job failed: " + title, e);
            } finally {
                busy = false;
                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    refreshAutoLog();
                });
            }
        }, "local-adb").start();
    }

    private void setButtonsEnabled(boolean on) {
        testBtn.setEnabled(on);
        killBtn.setEnabled(on);
    }

    private void refreshAutoLog() {
        String lines = EventLog.lines(this);
        autoLog.setText(lines.isEmpty() ? "（还没有记录）" : lines);
    }

    /** 追加一行结果（可在任意线程调用），同时写日志。 */
    private void say(final String msg) {
        Log.i(TAG, msg);
        final String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + msg;
        runOnUiThread(() -> {
            if (logText.length() > 0) logText.append('\n');
            logText.append(line);
            if (logText.length() > 5000) {
                logText.delete(0, logText.length() - 4000);
            }
            resultLog.setText(logText.toString());
        });
    }

    // ---------------- 小工具 ----------------

    private String currentHost() {
        String h = hostEdit.getText().toString().trim();
        return h.isEmpty() ? CleanupSettings.DEFAULT_HOST : h;
    }

    private int currentPort() {
        try {
            int v = Integer.parseInt(portEdit.getText().toString().trim());
            return (v > 0 && v < 65536) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void saveFields() {
        int port = currentPort();
        settings.setConnection(currentHost(), port > 0 ? port : CleanupSettings.DEFAULT_PORT,
                pkgEdit.getText().toString().trim());
    }
}
