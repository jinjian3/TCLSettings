package app.customized.settings;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;

/**
 * 给无线网络设置静态 IP / 网关 / DNS，或改回自动获取 (DHCP)。
 *
 * Android 9 的 WifiConfiguration.setIpConfiguration 以及 IpConfiguration、StaticIpConfiguration
 * 都是隐藏接口，所以全部用反射调用，并先尝试解除隐藏接口限制。
 *
 * 注意：Android 9 只允许应用修改“自己创建的”无线网络配置。
 * 因此这里总是由本应用自己添加（addNetwork）或更新（updateNetwork）一份配置。
 */
final class WifiStaticIp {
    private static final String TAG = "WifiStaticIp";

    static final int SEC_OPEN = 0;
    static final int SEC_WPA_PSK = 1;

    private static boolean exemptionTried = false;

    private WifiStaticIp() {}

    /** 解析 "a.b.c.d"，不做任何 DNS 查询。非法返回 null。 */
    static InetAddress parseIpv4(String s) {
        if (s == null) return null;
        String[] p = s.trim().split("\\.");
        if (p.length != 4) return null;
        byte[] b = new byte[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (p[i].isEmpty() || p[i].length() > 3) return null;
                int v = Integer.parseInt(p[i]);
                if (v < 0 || v > 255) return null;
                b[i] = (byte) v;
            }
            return InetAddress.getByAddress(b);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解除 Android 9/10 的隐藏接口限制（对 Android 11 及以上无效）。
     * 通过“反射的反射”调用 VMRuntime.setHiddenApiExemptions，失败也不影响后续流程。
     */
    static synchronized void exemptHiddenApis() {
        if (exemptionTried) return;
        exemptionTried = true;
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod(
                    "getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntimeCls = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(
                    vmRuntimeCls, new Object[]{"getRuntime", null});
            Method setExemptions = (Method) getDeclaredMethod.invoke(
                    vmRuntimeCls, new Object[]{"setHiddenApiExemptions", new Class[]{String[].class}});
            Object runtime = getRuntime.invoke(null);
            setExemptions.invoke(runtime, new Object[]{new String[]{"L"}});
            Log.i(TAG, "hidden api exemptions set");
        } catch (Throwable t) {
            Log.w(TAG, "hidden api exemption failed: " + t);
        }
    }

    private static Object newInstance(Class<?> cls) throws Exception {
        Constructor<?> c = cls.getDeclaredConstructor();
        c.setAccessible(true);
        return c.newInstance();
    }

    private static Exception fail(String step, Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) t = t.getCause();
        Log.e(TAG, "step failed: " + step, t);
        return new Exception("步骤[" + step + "]失败：" + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : " " + t.getMessage()), t);
    }

    /**
     * @param existingNetId 本应用此前创建的网络 id，没有则传 -1
     * @param staticMode    true = 静态；false = 自动获取 (DHCP)
     * @return 系统返回的 netId
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static int apply(WifiManager wm, String ssid, int security, String password,
                     boolean staticMode, InetAddress ip, int prefix,
                     InetAddress gateway, InetAddress dns1, InetAddress dns2,
                     int existingNetId) throws Exception {

        exemptHiddenApis();

        WifiConfiguration cfg = new WifiConfiguration();
        cfg.SSID = "\"" + ssid + "\"";
        if (existingNetId >= 0) cfg.networkId = existingNetId;

        if (security == SEC_OPEN) {
            cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            // 更新已有网络时，密码留空表示沿用原密码
            if (password != null && !password.isEmpty()) {
                cfg.preSharedKey = "\"" + password + "\"";
            }
        }

        Class<?> ipConfCls, assignCls, proxyCls;
        Object ipConf;
        String step = "加载 IpConfiguration 类";
        try {
            ipConfCls = Class.forName("android.net.IpConfiguration");
            assignCls = Class.forName("android.net.IpConfiguration$IpAssignment");
            proxyCls = Class.forName("android.net.IpConfiguration$ProxySettings");

            step = "创建 IpConfiguration";
            ipConf = newInstance(ipConfCls);

            step = "setIpAssignment";
            Method setAssign = ipConfCls.getMethod("setIpAssignment", assignCls);
            setAssign.invoke(ipConf, Enum.valueOf((Class) assignCls, staticMode ? "STATIC" : "DHCP"));

            step = "setProxySettings";
            Method setProxy = ipConfCls.getMethod("setProxySettings", proxyCls);
            setProxy.invoke(ipConf, Enum.valueOf((Class) proxyCls, "NONE"));
        } catch (Throwable t) {
            throw fail(step, t);
        }

        if (staticMode) {
            try {
                step = "加载 StaticIpConfiguration / LinkAddress 类";
                Class<?> sicCls = Class.forName("android.net.StaticIpConfiguration");
                Class<?> laCls = Class.forName("android.net.LinkAddress");

                step = "创建 StaticIpConfiguration";
                Object sic = newInstance(sicCls);

                step = "创建 LinkAddress";
                Constructor<?> laCtor = laCls.getDeclaredConstructor(InetAddress.class, int.class);
                laCtor.setAccessible(true);
                Object linkAddr = laCtor.newInstance(ip, prefix);

                step = "填写 IP / 网关 / DNS";
                sicCls.getField("ipAddress").set(sic, linkAddr);
                sicCls.getField("gateway").set(sic, gateway);
                ArrayList<InetAddress> dns =
                        (ArrayList<InetAddress>) sicCls.getField("dnsServers").get(sic);
                if (dns1 != null) dns.add(dns1);
                if (dns2 != null) dns.add(dns2);

                step = "setStaticIpConfiguration";
                Method setSic = ipConfCls.getMethod("setStaticIpConfiguration", sicCls);
                setSic.invoke(ipConf, sic);
            } catch (Throwable t) {
                throw fail(step, t);
            }
        }

        try {
            step = "WifiConfiguration.setIpConfiguration";
            Method setIpConfiguration =
                    WifiConfiguration.class.getMethod("setIpConfiguration", ipConfCls);
            setIpConfiguration.invoke(cfg, ipConf);
        } catch (Throwable t) {
            throw fail(step, t);
        }

        int netId = existingNetId >= 0 ? wm.updateNetwork(cfg) : wm.addNetwork(cfg);
        Log.i(TAG, "add/update returned netId=" + netId);
        if (netId < 0) {
            throw new IllegalStateException("系统拒绝保存（返回 -1）。"
                    + "如果这个无线网络是之前在 TCL 自带设置里保存的，请先在 TCL 设置里选择“忽略”它，再用本页保存。");
        }
        boolean saved = wm.saveConfiguration();
        boolean disc = wm.disconnect();
        boolean enabled = wm.enableNetwork(netId, true);
        boolean recon = wm.reconnect();
        Log.i(TAG, "save=" + saved + " disconnect=" + disc
                + " enableNetwork=" + enabled + " reconnect=" + recon);
        return netId;
    }
}
