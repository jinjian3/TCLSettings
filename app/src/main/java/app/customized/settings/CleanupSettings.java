package app.customized.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** 内存清理的设置：本机 adb 地址/端口、要结束的进程、是否自动结束。 */
final class CleanupSettings {
    static final String DEFAULT_HOST = "127.0.0.1";
    static final int DEFAULT_PORT = 5555;
    static final String DEFAULT_PKG = "com.tcl.cyberui";

    private static final String FILE = "cleanup";
    private static final String K_HOST = "host";
    private static final String K_PORT = "port";
    private static final String K_PKG = "pkg";
    private static final String K_AUTO = "auto";

    private final SharedPreferences sp;

    CleanupSettings(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    String getHost() { return sp.getString(K_HOST, DEFAULT_HOST); }
    int getPort() { return sp.getInt(K_PORT, DEFAULT_PORT); }
    String getPkg() { return sp.getString(K_PKG, DEFAULT_PKG); }
    boolean isAuto() { return sp.getBoolean(K_AUTO, false); }

    void setConnection(String host, int port, String pkg) {
        sp.edit().putString(K_HOST, host).putInt(K_PORT, port).putString(K_PKG, pkg).apply();
    }

    void setAuto(boolean v) { sp.edit().putBoolean(K_AUTO, v).apply(); }
}
