package app.customized.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * 第一级菜单：HDMI 设置 / 无线网络设置 / 内存清理设置。
 * Emotn UI 等应用的“设置”按钮（android.settings.SETTINGS）也会打开这一页。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ui.Page page = new Ui.Page(this);
        page.root.addView(Ui.title(this, "Customized Settings"));
        page.root.addView(Ui.note(this, "请选择要设置的项目（用遥控器上下键选择，确定键进入，返回键回到上一级）。"));

        Button hdmi = Ui.button(this, "HDMI 设置", v -> open(HdmiSettingsActivity.class));
        Button wifi = Ui.button(this, "无线网络设置", v -> open(WifiSettingsActivity.class));
        Button memory = Ui.button(this, "内存清理设置", v -> open(MemorySettingsActivity.class));

        page.root.addView(hdmi, params(28));
        page.root.addView(wifi, params(12));
        page.root.addView(memory, params(12));

        page.show();
        hdmi.requestFocus();
    }

    private void open(Class<? extends Activity> target) {
        startActivity(new Intent(this, target));
    }

    private LinearLayout.LayoutParams params(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                Ui.dp(this, 560), LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, topDp);
        return p;
    }
}
