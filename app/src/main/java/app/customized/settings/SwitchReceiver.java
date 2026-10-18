package app.customized.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 暂时停用：开机自动进入 HDMI 的“延迟到点，真正执行切换”。
 * 当前没有在 AndroidManifest.xml 里登记，所以不会被系统调用；代码保留在下面的注释里，方便以后恢复。
 */
public class SwitchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        /*
        Settings s = new Settings(context);
        if (!s.isEnabled()) {
            Log.i("HdmiSwitch", "alarm: disabled meanwhile, skip");
            return;
        }
        HdmiLauncher.launch(context, s.currentTargetId());
        */
    }
}
