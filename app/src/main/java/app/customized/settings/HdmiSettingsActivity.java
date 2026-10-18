package app.customized.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** HDMI 设置：立即进入 HDMI 1 / HDMI 2，以及两个信源序号。 */
public class HdmiSettingsActivity extends Activity {

    private Settings settings;
    private EditText id1Edit;
    private EditText id2Edit;
    private TextView resultView;

    // ---- 暂时停用的界面控件（见 onCreate 里被注释掉的那一段）----
    // private Switch enableSwitch;
    // private RadioButton radio1;
    // private RadioButton radio2;
    // private EditText delayEdit;
    // private EditText probeEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);

        Ui.Page page = new Ui.Page(this);
        page.root.addView(Ui.title(this, "HDMI 设置"));

        // ---- 立即进入 ----
        Button go1 = Ui.button(this, "现在进入 HDMI 1", v -> switchNow(1));
        Button go2 = Ui.button(this, "现在进入 HDMI 2", v -> switchNow(2));
        page.root.addView(Ui.buttonRow(this, go1, go2), Ui.matchWrap(this, 16));

        // ---- 信源序号 ----
        page.root.addView(Ui.section(this, "信源序号（EnTCLInputSource 枚举序号）"), Ui.matchWrap(this, 24));
        id1Edit = Ui.numberEdit(this, idText(settings.getId1()));
        id2Edit = Ui.numberEdit(this, idText(settings.getId2()));
        page.root.addView(Ui.formRow(this, "HDMI 1 的序号", id1Edit, 200, "（默认 8）"));
        page.root.addView(Ui.formRow(this, "HDMI 2 的序号", id2Edit, 200, "（默认 9）"));

        Button save = Ui.button(this, "保存设置", v -> save());
        page.root.addView(save, Ui.matchWrap(this, 16));

        resultView = Ui.result(this, "");
        page.root.addView(resultView, Ui.matchWrap(this, 16));

        /*
         * ===== 暂时停用：开机自动进入 HDMI =====
         * 原因：TCL 自带设置里的“记忆开关机”已经能满足大部分场景。
         * 要恢复，需要同时：
         *   1. 取消下面这段的注释（并取消上面几个控件声明的注释）；
         *   2. 取消 BootReceiver.java、SwitchReceiver.java 里相关代码的注释；
         *   3. 取消 AndroidManifest.xml 里 SwitchReceiver 的注释。
         *
         * 开关与选项：
         *   enableSwitch = new Switch(this);
         *   enableSwitch.setText("开机自动进入 HDMI");
         *   enableSwitch.setChecked(settings.isEnabled());
         *   enableSwitch.setOnCheckedChangeListener((b, on) -> settings.setEnabled(on));
         *   page.root.addView(enableSwitch);
         *
         *   RadioGroup group = new RadioGroup(this);   // 进入哪个 HDMI 口：radio1 = HDMI 1, radio2 = HDMI 2
         *   group.setOnCheckedChangeListener((g, id) -> settings.setTarget(id == radio2.getId() ? 2 : 1));
         *
         *   delayEdit = Ui.numberEdit(this, String.valueOf(settings.getDelaySeconds()));  // 开机后延迟几秒再切换
         *   // 保存时：settings.setDelaySeconds(parse(delayEdit, 8, 0, 120));
         *
         * ===== 暂时停用：找序号 =====
         *   probeEdit = Ui.numberEdit(this, "0");
         *   Button probe = Ui.button(this, "试一下", v -> HdmiLauncher.launch(this, parse(probeEdit, 0, 0, 999)));
         *   // 输入一个数字，点“试一下”，看电视切到哪个信源，用来找 HDMI 序号。
         */

        page.show();
        go1.requestFocus();
    }

    // ---------------- 行为 ----------------

    /** 保存两个序号，并把结果显示在屏幕上。 */
    private void save() {
        settings.setId1(parse(id1Edit, Settings.UNSET, Settings.UNSET, 999));
        settings.setId2(parse(id2Edit, Settings.UNSET, Settings.UNSET, 999));
        String msg = "已保存：HDMI 1 序号 " + describeId(settings.getId1())
                + "，HDMI 2 序号 " + describeId(settings.getId2()) + "。";
        if (settings.getId1() < 0 || settings.getId2() < 0) {
            msg += "（序号为空的那个口无法切换）";
        }
        resultView.setText(msg);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    private void switchNow(int which) {
        save();
        int id = which == 2 ? settings.getId2() : settings.getId1();
        if (id < 0) {
            String msg = "请先填好 HDMI " + which + " 的序号。";
            resultView.setText(msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }
        boolean ok = HdmiLauncher.launch(this, id);
        resultView.setText(ok
                ? "已发出切换到 HDMI " + which + " 的请求（序号 " + id + "）。"
                : "切换请求发送失败，请查看日志（标签 HdmiSwitch）。");
    }

    // ---------------- 小工具 ----------------

    private static String idText(int v) {
        return v < 0 ? "" : String.valueOf(v);
    }

    private static String describeId(int v) {
        return v < 0 ? "（空）" : String.valueOf(v);
    }

    private int parse(EditText e, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(e.getText().toString().trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
