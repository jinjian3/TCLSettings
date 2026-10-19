Customized Settings + HDMI1（TCL V8M Pro / Android 9）
=====================================================

本工程包含两个应用：
  app    -> Customized Settings   包名 app.customized.settings   （设置：HDMI / 无线网络 / 内存清理）
  hdmi1  -> HDMI1                 包名 app.hdmi1                 （点一下就进入 HDMI1，没有界面）

一、构建
 1. 用 Android Studio 打开本文件夹（File > Open），等 Gradle 同步完成。
 2. Build > Build Bundle(s)/APK(s) > Build APK(s)，两个应用会一起构建出来：
      app/build/outputs/apk/debug/app-debug.apk       （Customized Settings）
      hdmi1/build/outputs/apk/debug/hdmi1-debug.apk   （HDMI1）

二、安装
 adb install -r app-debug.apk
 adb install -r hdmi1-debug.apk
 安装后必须各自手动打开一次，系统才会允许它们接收广播。
 （可选）开机自启白名单：adb shell dumpsys deviceidle whitelist +app.customized.settings

三、注意事项
 1. 无线网络：旧应用创建的网络，新应用没有权限修改或删除（系统限制）。
    要改用新应用管理时，请在电视上操作：
      TCL 设置里对这个无线网络选择“忽略” -> 打开 Emotn UI 的 WiFi 按钮（会进入新应用的“无线网络设置”）
      -> 填写并“保存并连接（静态 IP）”。
    这一步会让网络短暂断开，所以请在电视上操作，不要用无线 adb。
    新应用保存好网络后，再卸载旧应用。（我不确定卸载旧应用时系统会不会连带删除它创建的网络，
    所以不要在重建网络之前卸载。）
 2. 本机 ADB 的授权是按应用保存的：新应用第一次“连接测试”时，电视会再弹一次“允许调试”。

四、Customized Settings 使用说明
 第一级菜单：HDMI 设置 / 无线网络设置 / 内存清理设置。遥控器返回键回到上一级。
 Emotn UI 的“设置”按钮（android.settings.SETTINGS）打开第一级菜单；
 Emotn UI 的“WiFi”按钮（android.settings.WIFI_SETTINGS）直接打开“无线网络设置”。
 被选中的按钮、输入框、开关会显示为亮琥珀色（或琥珀色粗边框），很容易看出焦点在哪里。

 1) HDMI 设置
    - “现在进入 HDMI 1 / HDMI 2”：按序号切换信源（默认 8 / 9，本机实测）。
    - 修改序号后点“保存设置”，结果显示在屏幕下方。
    - 暂时停用（代码保留为注释）：开机自动进入 HDMI、选择口/延迟秒数、找序号。
      TCL 自带设置里的“记忆开关机”已经能满足大部分场景。恢复方法见 BootReceiver.java 开头的说明。

 2) 无线网络设置
    - 先在 TCL 自带设置里对该网络选择“忽略”，再在这里填写并保存。
    - 密码在修改本应用已保存的网络时可以留空，沿用原密码。
    - “填入当前网络的值”、“改回自动获取（DHCP）”、“删除本应用保存的这个网络”、“打开 TCL 系统设置”。
    - 若提示反射/隐藏接口相关错误，执行一次：
        adb shell settings put global hidden_api_policy_p_apps 1
        adb shell settings put global hidden_api_policy_pre_p_apps 1

 3) 内存清理设置
    - 通过电视自带的网络 ADB（默认 127.0.0.1:5555）执行 am force-stop，结束指定进程（默认 com.tcl.cyberui）。
    - 第一次“连接测试”时电视会弹出“允许调试”，请点允许（建议勾选“一律允许”）。
    - “结束上面的进程（手动）”：只在该进程不是当前默认桌面时才执行。查不出默认桌面时也不会执行。
    - “启动后自动结束上面的进程”开关（默认关闭）：
        * 开机、待机唤醒（TCL 发出的 STR_RESUME_COMPLETED 广播）后等 30 秒再执行；
        * 同样只在该进程不是默认桌面时执行；
        * 如果该进程此刻正在前台显示（例如拔电源冷启动后停在 T-UI），每 30 秒重试一次，最多 10 次；
          你按 HOME 切到 Emotn UI 之后，下一次重试就会把它结束；
        * 没有授权（电视上没点允许）时不会反复重试，只记一条记录；
        * 每一步都会写入页面底部的“自动执行记录”，也可用 adb logcat -s Cleanup 查看。
    - 安全措施：包名只允许合法格式；以下进程不允许结束：android、system、com.tcl.tv、com.tcl.guard、
      com.oversea.aslauncher、app.customized.settings、app.hdmi1。
    - 这台电视的 adbd 在同一个连接里只接受第一条命令，所以程序对每条命令都新开一个连接。

五、HDMI1 小应用
 - 名字 HDMI1，图标是白色 “HDMI” + 琥珀色 “1”；同时提供了电视用的横幅图。
 - 点击后直接切到 HDMI1，然后立刻退出，没有任何界面。
 - 序号写死为 8。若换了电视或序号不同，修改 hdmi1/src/main/java/app/hdmi1/LaunchActivity.java 里的
   HDMI1_SOURCE_ID 后重新编译。
 - 想放到 Emotn UI 桌面上，在 Emotn UI 的“添加应用”里选 HDMI1 即可。
