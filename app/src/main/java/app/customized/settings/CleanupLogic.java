package app.customized.settings;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * “结束进程”的判断与执行流程（不依赖 Android，便于在电脑上用模拟的 adbd 测试）。
 *
 * 步骤：
 *  1. 检查包名格式和禁止名单；
 *  2. 查出当前默认桌面；目标就是默认桌面、或默认桌面查不出来时，一律不执行；
 *  3. 目标进程不存在则无需处理；
 *  4. （自动模式）目标正在前台显示则暂不处理，由调用方稍后重试；
 *  5. am force-stop，等一会儿再确认进程是否真的没了。
 */
final class CleanupLogic {

    interface Cmd {
        LocalAdb.Result exec(String command) throws IOException;
    }

    enum Status {
        /** 已结束。 */
        KILLED,
        /** 进程本来就没有运行。 */
        NOT_RUNNING,
        /** 目标就是默认桌面，没有执行。 */
        SKIPPED_DEFAULT_HOME,
        /** 查不出默认桌面（例如还没有设置默认桌面），为安全起见没有执行。 */
        SKIPPED_UNKNOWN_HOME,
        /** 目标正在前台显示，暂不执行（可以稍后重试）。 */
        FOREGROUND,
        /** 已执行结束命令，但进程还在。 */
        STILL_RUNNING,
        /** 包名不合法或在禁止名单里。 */
        REFUSED
    }

    static final class Outcome {
        final Status status;
        final String message;

        Outcome(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        /** 是否值得稍后再试一次。 */
        boolean shouldRetry() {
            return status == Status.FOREGROUND;
        }
    }

    /** 关键系统组件、本应用等：不允许在这里结束。 */
    static final Set<String> NEVER_KILL = new HashSet<>(Arrays.asList(
            "android", "system", "com.tcl.tv", "com.tcl.guard",
            "com.oversea.aslauncher", "app.customized.settings", "app.hdmi1"));

    private static final Pattern PACKAGE_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*");

    private CleanupLogic() {}

    /**
     * @param skipIfForeground true（自动模式）：目标在前台时不结束，返回 FOREGROUND
     * @param settleMs         force-stop 之后等多久再确认（正式使用 1500）
     */
    static Outcome run(Cmd cmd, String pkg, boolean skipIfForeground, long settleMs)
            throws IOException, InterruptedException {
        if (pkg == null || !PACKAGE_NAME.matcher(pkg).matches()) {
            return new Outcome(Status.REFUSED, "拒绝：包名格式不对（" + pkg + "）。");
        }
        if (NEVER_KILL.contains(pkg)) {
            return new Outcome(Status.REFUSED, "拒绝：" + pkg + " 在禁止结束的名单里。");
        }

        String home = defaultHomePackage(cmd);
        if (home == null || "android".equals(home)) {
            return new Outcome(Status.SKIPPED_UNKNOWN_HOME,
                    "跳过：查不出当前默认桌面（可能还没有设置默认桌面），为安全起见不执行。");
        }
        if (home.equals(pkg)) {
            return new Outcome(Status.SKIPPED_DEFAULT_HOME,
                    "跳过：" + pkg + " 就是当前默认桌面，不结束。");
        }

        String before = pids(cmd, pkg);
        if (before.isEmpty()) {
            return new Outcome(Status.NOT_RUNNING, "无需处理：" + pkg + " 当前没有运行。（默认桌面：" + home + "）");
        }

        if (skipIfForeground && isForeground(cmd, pkg)) {
            return new Outcome(Status.FOREGROUND, pkg + " 正在前台显示，暂不结束。");
        }

        LocalAdb.Result r = cmd.exec("am force-stop " + pkg);
        Thread.sleep(settleMs);
        String after = pids(cmd, pkg);
        String exit = r.exitCode == 0 ? "" : "，force-stop 退出码 " + r.exitCode
                + (r.output.isEmpty() ? "" : "，输出：" + r.output);
        if (after.isEmpty()) {
            return new Outcome(Status.KILLED, "已结束 " + pkg + "（pid " + before + " → 已退出）。默认桌面：" + home + exit);
        }
        return new Outcome(Status.STILL_RUNNING, "已执行结束命令，但 " + pkg + " 仍在运行（pid " + after + "）" + exit + "。");
    }

    /** 当前默认桌面的包名；查不出来时返回 null。 */
    static String defaultHomePackage(Cmd cmd) throws IOException {
        LocalAdb.Result r = cmd.exec(
                "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
        String[] lines = r.output.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i].trim();
            int slash = l.indexOf('/');
            if (slash > 0 && l.indexOf(' ') < 0) {
                return l.substring(0, slash);
            }
        }
        return null;
    }

    private static String pids(Cmd cmd, String pkg) throws IOException {
        return cmd.exec("pidof " + pkg).output.trim();
    }

    /** 目标包的界面是否是当前正在前台显示（resumed）的。 */
    private static boolean isForeground(Cmd cmd, String pkg) throws IOException {
        LocalAdb.Result r = cmd.exec("dumpsys activity activities | grep mResumedActivity");
        return r.output.contains(" " + pkg + "/");
    }
}
