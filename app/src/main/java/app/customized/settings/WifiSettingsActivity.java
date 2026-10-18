package app.customized.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;

/**
 * 无线网络设置：手动设置 IP 地址、网关、DNS。
 * 同时响应 Emotn UI 的 WiFi 按钮发出的 android.settings.WIFI_SETTINGS。
 */
public class WifiSettingsActivity extends Activity {

    private static final String TAG = "WifiStaticIp";
    // 沿用旧版本的文件名，这样以后升级不会丢失已保存的网络信息
    private static final String PREFS = "wifi_static";

    private WifiManager wm;
    private SharedPreferences sp;
    private final Handler handler = new Handler();

    private EditText ssidEdit, pwdEdit, ipEdit, prefixEdit, gwEdit, dns1Edit, dns2Edit;
    private RadioButton openRadio, wpaRadio;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        Ui.Page page = new Ui.Page(this);
        LinearLayout root = page.root;

        root.addView(Ui.title(this, "无线网络设置"));
        root.addView(Ui.note(this, "先在 TCL 自带设置里选择“忽略”这个无线网络，再在这里填写并保存。"
                + "（系统只允许应用修改自己创建的无线网络配置。）"));

        ssidEdit = Ui.edit(this, sp.getString("ssid", ""), false);
        root.addView(Ui.formRow(this, "无线网络名称", ssidEdit, 360, "（SSID）"), Ui.matchWrap(this, 12));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        openRadio = Ui.radio(this, "开放");
        wpaRadio = Ui.radio(this, "WPA/WPA2 密码");
        group.addView(openRadio);
        group.addView(wpaRadio);
        if (sp.getInt("sec", WifiStaticIp.SEC_WPA_PSK) == WifiStaticIp.SEC_OPEN) openRadio.setChecked(true);
        else wpaRadio.setChecked(true);
        root.addView(Ui.formRow(this, "加密方式", group, 480, null));

        pwdEdit = Ui.edit(this, "", false);
        root.addView(Ui.formRow(this, "密码", pwdEdit, 360, "（修改本应用已保存的网络时可留空，沿用原密码）"));

        ipEdit = Ui.edit(this, sp.getString("ip", ""), true);
        root.addView(Ui.formRow(this, "IP 地址", ipEdit, 360, null));

        prefixEdit = Ui.edit(this, sp.getString("prefix", "24"), true);
        root.addView(Ui.formRow(this, "子网前缀长度", prefixEdit, 360, "（255.255.255.0 就是 24）"));

        gwEdit = Ui.edit(this, sp.getString("gw", ""), true);
        root.addView(Ui.formRow(this, "网关", gwEdit, 360, null));

        dns1Edit = Ui.edit(this, sp.getString("dns1", ""), true);
        root.addView(Ui.formRow(this, "DNS1", dns1Edit, 360, null));

        dns2Edit = Ui.edit(this, sp.getString("dns2", ""), true);
        root.addView(Ui.formRow(this, "DNS2", dns2Edit, 360, "（可留空）"));

        // 5 个按钮：前 4 个宽度一致（2x2），最后一个和上面一行两个按钮并排的总宽度相同
        Button fill = Ui.button(this, "填入当前网络的值", v -> fillCurrent());
        Button save = Ui.button(this, "保存并连接（静态 IP）", v -> apply(true));
        Button dhcp = Ui.button(this, "改回自动获取（DHCP）", v -> apply(false));
        Button remove = Ui.button(this, "删除本应用保存的这个网络", v -> removeMine());
        Button tcl = Ui.button(this, "打开 TCL 系统设置（连接 / 忽略网络）", v -> openTclSettings());
        root.addView(Ui.buttonRow(this, fill, save), Ui.matchWrap(this, 24));
        root.addView(Ui.buttonRow(this, dhcp, remove), Ui.matchWrap(this, 12));
        root.addView(tcl, Ui.matchWrap(this, 12));

        status = Ui.result(this, "");
        root.addView(status, Ui.matchWrap(this, 16));

        page.show();
        ssidEdit.requestFocus();
    }

    // ---------------- 行为 ----------------

    private void fillCurrent() {
        DhcpInfo d = wm.getDhcpInfo();
        if (d == null || d.ipAddress == 0) {
            say("当前没有连上无线网络，没有可填入的值。");
            return;
        }
        ipEdit.setText(intToIp(d.ipAddress));
        gwEdit.setText(intToIp(d.gateway));
        dns1Edit.setText(d.dns1 == 0 ? "" : intToIp(d.dns1));
        dns2Edit.setText(d.dns2 == 0 ? "" : intToIp(d.dns2));
        prefixEdit.setText(String.valueOf(d.netmask == 0 ? 24 : Integer.bitCount(d.netmask)));
        say("已填入当前网络的值，请按需要修改。");
    }

    private void apply(boolean staticMode) {
        String ssid = ssidEdit.getText().toString().trim();
        if (ssid.isEmpty()) { say("请填写无线网络名称。"); return; }

        int security = openRadio.isChecked() ? WifiStaticIp.SEC_OPEN : WifiStaticIp.SEC_WPA_PSK;
        int existing = sp.getString("ssid", "").equals(ssid) ? sp.getInt("netId", -1) : -1;
        String pwd = pwdEdit.getText().toString();
        if (security == WifiStaticIp.SEC_WPA_PSK && existing < 0
                && (pwd.length() < 8 || pwd.length() > 63)) {
            say("WPA 密码长度应为 8–63 位。");
            return;
        }
        if (security == WifiStaticIp.SEC_WPA_PSK && !pwd.isEmpty()
                && (pwd.length() < 8 || pwd.length() > 63)) {
            say("WPA 密码长度应为 8–63 位。");
            return;
        }

        InetAddress ip = null, gw = null, d1 = null, d2 = null;
        int prefix = 24;
        if (staticMode) {
            ip = WifiStaticIp.parseIpv4(ipEdit.getText().toString());
            gw = WifiStaticIp.parseIpv4(gwEdit.getText().toString());
            if (ip == null) { say("IP 地址格式不对，应为 a.b.c.d。"); return; }
            if (gw == null) { say("网关格式不对，应为 a.b.c.d。"); return; }
            try {
                prefix = Integer.parseInt(prefixEdit.getText().toString().trim());
            } catch (NumberFormatException e) { prefix = -1; }
            if (prefix < 1 || prefix > 32) { say("前缀长度应为 1–32。"); return; }
            String s1 = dns1Edit.getText().toString().trim();
            String s2 = dns2Edit.getText().toString().trim();
            if (!s1.isEmpty()) {
                d1 = WifiStaticIp.parseIpv4(s1);
                if (d1 == null) { say("DNS1 格式不对。"); return; }
            }
            if (!s2.isEmpty()) {
                d2 = WifiStaticIp.parseIpv4(s2);
                if (d2 == null) { say("DNS2 格式不对。"); return; }
            }
        }

        Log.i(TAG, "apply static=" + staticMode + " ssid=" + ssid
                + " sec=" + security + " existingNetId=" + existing + " ip=" + ip
                + "/" + prefix + " gw=" + gw + " dns=" + d1 + "," + d2
                + " wifiEnabled=" + wm.isWifiEnabled());
        if (!wm.isWifiEnabled()) {
            wm.setWifiEnabled(true);
        }
        try {
            int netId = WifiStaticIp.apply(wm, ssid, security, pwd, staticMode,
                    ip, prefix, gw, d1, d2, existing);
            sp.edit().putString("ssid", ssid).putInt("sec", security).putInt("netId", netId)
                    .putString("ip", ipEdit.getText().toString().trim())
                    .putString("prefix", prefixEdit.getText().toString().trim())
                    .putString("gw", gwEdit.getText().toString().trim())
                    .putString("dns1", dns1Edit.getText().toString().trim())
                    .putString("dns2", dns2Edit.getText().toString().trim())
                    .apply();
            say("已提交（netId=" + netId + "），正在连接，10 秒后显示实际结果……");
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(this::showActual, 10000);
        } catch (Throwable t) {
            Log.e(TAG, "apply failed", t);
            say("失败：" + describe(t));
        }
    }

    private void openTclSettings() {
        try {
            Intent i = new Intent();
            i.setClassName("com.tcl.settings", "com.tcl.settings.ui.MainActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            say("打不开 TCL 设置：" + describe(e) + "。请用遥控器上的设置键。");
        }
    }

    private void removeMine() {
        int netId = sp.getInt("netId", -1);
        if (netId < 0) { say("本应用还没有保存过无线网络。"); return; }
        boolean ok = wm.removeNetwork(netId);
        wm.saveConfiguration();
        if (ok) sp.edit().remove("netId").apply();
        say(ok ? "已删除本应用保存的网络，请到 TCL 设置里重新连接。" : "删除失败。");
    }

    private void showActual() {
        DhcpInfo d = wm.getDhcpInfo();
        if (d == null || d.ipAddress == 0) {
            say("还没有连上。请稍等，或检查密码、IP 是否正确。");
            return;
        }
        say("当前实际：IP " + intToIp(d.ipAddress) + "  网关 " + intToIp(d.gateway)
                + "  DNS " + intToIp(d.dns1) + (d.dns2 != 0 ? " / " + intToIp(d.dns2) : "")
                + "  前缀 " + Integer.bitCount(d.netmask));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------- 小工具 ----------------

    private void say(String s) {
        status.setText(s);
        Log.i(TAG, s);
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private static String intToIp(int i) {
        return (i & 0xff) + "." + ((i >> 8) & 0xff) + "." + ((i >> 16) & 0xff) + "." + ((i >> 24) & 0xff);
    }

    private static String describe(Throwable t) {
        if (t.getMessage() != null && t.getMessage().startsWith("步骤[")) return t.getMessage();
        if (t instanceof InvocationTargetException && t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : "：" + m);
    }
}
