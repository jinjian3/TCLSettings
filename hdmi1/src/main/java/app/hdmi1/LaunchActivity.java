package app.hdmi1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * 只有一个功能：点击图标就进入 HDMI 1，然后立刻退出（没有任何界面）。
 *
 * 做法和 TCL 自带电视应用切换信源的方式一致：启动 com.tcl.tv.TVActivity，
 * 并带上整数 extra "targetSourceId"，值是 EnTCLInputSource 枚举的序号。
 * 本机实测 HDMI1 = 8（HDMI2 = 9, HDMI3 = 10, HDMI4 = 11）。
 *
 * 这个序号是写死的。如果换了电视、序号不同，只需要改下面的 HDMI1_SOURCE_ID 重新编译。
 */
public class LaunchActivity extends Activity {

    private static final String TAG = "Hdmi1";

    static final int HDMI1_SOURCE_ID = 8;

    private static final String TV_PACKAGE = "com.tcl.tv";
    private static final String TV_ACTIVITY = "com.tcl.tv.TVActivity";
    private static final String EXTRA_SOURCE_ID = "targetSourceId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent intent = new Intent();
            intent.setClassName(TV_PACKAGE, TV_ACTIVITY);
            intent.putExtra(EXTRA_SOURCE_ID, HDMI1_SOURCE_ID);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.i(TAG, "launched TVActivity, targetSourceId=" + HDMI1_SOURCE_ID);
        } catch (Exception e) {
            Log.e(TAG, "launch failed", e);
            Toast.makeText(this, "无法切换到 HDMI1：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish(); // Theme.NoDisplay 的 Activity 必须在 onCreate 里结束
    }
}
