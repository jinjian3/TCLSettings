package app.customized.settings;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 按 TCL 自带电视应用 (com.tcl.tv) 自己的方式切换信源：
 * 启动 com.tcl.tv.TVActivity，并带上整数 extra "targetSourceId"，
 * 其值是 EnTCLInputSource 枚举的序号。
 */
final class HdmiLauncher {
    private static final String TAG = "HdmiSwitch";
    static final String TV_PACKAGE = "com.tcl.tv";
    static final String TV_ACTIVITY = "com.tcl.tv.TVActivity";
    static final String EXTRA_SOURCE_ID = "targetSourceId";

    private HdmiLauncher() {}

    /** @return 是否成功发出了启动请求 */
    static boolean launch(Context context, int sourceId) {
        if (sourceId < 0) {
            Log.w(TAG, "source id not set, skip");
            return false;
        }
        try {
            Intent intent = new Intent();
            intent.setClassName(TV_PACKAGE, TV_ACTIVITY);
            intent.putExtra(EXTRA_SOURCE_ID, sourceId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.i(TAG, "launched TVActivity, targetSourceId=" + sourceId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "launch failed", e);
            return false;
        }
    }
}
