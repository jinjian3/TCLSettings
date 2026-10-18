package app.customized.settings;

import java.io.File;
import java.io.IOException;

/**
 * 执行单条 adb shell 命令。每条命令都新开一个连接：
 * 这台电视的 adbd 在同一个连接里只接受第一条命令（实测第二条会被拒绝）。
 * 本机回环连接很快，所以这样做没有明显代价。
 */
final class AdbRunner implements CleanupLogic.Cmd {

    private final String host;
    private final int port;
    private final File keyDir;
    private LocalAdb.Listener firstListener;

    /** @param firstListener 只有第一次连接才会收到过程提示（例如“请在电视上点允许”）；可为 null */
    AdbRunner(String host, int port, File keyDir, LocalAdb.Listener firstListener) {
        this.host = host;
        this.port = port;
        this.keyDir = keyDir;
        this.firstListener = firstListener;
    }

    @Override
    public LocalAdb.Result exec(String command) throws IOException {
        LocalAdb.Listener l = firstListener;
        firstListener = null;
        LocalAdb adb = LocalAdb.connect(host, port, keyDir, l);
        try {
            return adb.shell(command);
        } finally {
            adb.close();
        }
    }
}
