package app.customized.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** 所有设置都放在这里，界面和开机接收器共用。 */
final class Settings {
    static final int UNSET = -1;

    // 由 TCL 电视的 EnTCLInputSource 枚举序号决定：本机实测 HDMI1=8, HDMI2=9, HDMI3=10, HDMI4=11
    static final int DEFAULT_HDMI1_ID = 8;
    static final int DEFAULT_HDMI2_ID = 9;

    private static final String FILE = "settings";
    private static final String K_ENABLED = "enabled";
    private static final String K_TARGET = "target";      // 1 = HDMI1, 2 = HDMI2
    private static final String K_DELAY = "delay";        // 秒
    private static final String K_ID1 = "id1";
    private static final String K_ID2 = "id2";

    private final SharedPreferences sp;

    Settings(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean isEnabled() { return sp.getBoolean(K_ENABLED, false); }
    void setEnabled(boolean v) { sp.edit().putBoolean(K_ENABLED, v).apply(); }

    int getTarget() { return sp.getInt(K_TARGET, 1); }
    void setTarget(int v) { sp.edit().putInt(K_TARGET, v).apply(); }

    int getDelaySeconds() { return sp.getInt(K_DELAY, 8); }
    void setDelaySeconds(int v) { sp.edit().putInt(K_DELAY, v).apply(); }

    int getId1() { return sp.getInt(K_ID1, DEFAULT_HDMI1_ID); }
    void setId1(int v) { sp.edit().putInt(K_ID1, v).apply(); }

    int getId2() { return sp.getInt(K_ID2, DEFAULT_HDMI2_ID); }
    void setId2(int v) { sp.edit().putInt(K_ID2, v).apply(); }

    /** 当前选择的 HDMI 口对应的序号，未设置时返回 UNSET。 */
    int currentTargetId() { return getTarget() == 2 ? getId2() : getId1(); }
}
