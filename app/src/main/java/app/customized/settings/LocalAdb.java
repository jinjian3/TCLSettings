package app.customized.settings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * 一个很小的 ADB 客户端（纯 Java，不依赖任何第三方库），用来连接电视自己的 adbd
 * （默认 127.0.0.1:5555），然后执行 shell 命令。
 *
 * 只实现了需要的部分：CNXN 握手、AUTH 认证、OPEN "shell:" 并读取输出。
 *
 * 第一次连接时，电视会弹出“允许 USB 调试？”窗口，需要在电视上点“允许”（建议勾选“一律允许”）。
 * 本应用的密钥保存在应用私有目录里，勾选“一律允许”之后不会再弹窗。
 */
public final class LocalAdb implements Closeable {

    /** 过程提示（在后台线程里回调，界面要自己切回主线程）。 */
    public interface Listener {
        void onStatus(String message);
    }

    /** 认证没有通过（电视上没有点“允许”等）。这种情况重试没有意义。 */
    public static final class AuthException extends IOException {
        private static final long serialVersionUID = 1L;

        public AuthException(String message) {
            super(message);
        }
    }

    /** 一条命令的结果。 */
    public static final class Result {
        /** 命令输出（已把 \r\n 统一成 \n，并去掉末尾换行）。 */
        public final String output;
        /** 命令退出码；读不到时为 -1。 */
        public final int exitCode;

        Result(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }
    }

    // ---- ADB 协议常量 ----
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int A_VERSION = 0x01000001;
    private static final int MAX_PAYLOAD = 256 * 1024;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int HEADER_SIZE = 24;
    private static final int MAX_ACCEPTED_PAYLOAD = 1024 * 1024;

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    /** 等用户在电视上点“允许”的最长时间。 */
    private static final int USER_APPROVE_TIMEOUT_MS = 90000;
    private static final int SHELL_TIMEOUT_MS = 20000;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** DER 里 SHA-1 的 DigestInfo 前缀；ADB 的签名是“对 20 字节 token 当作已哈希值做 PKCS#1 v1.5 签名”。 */
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private static final String EXIT_MARK = "__EXIT__=";

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Keys keys;
    private final Listener listener;
    private int nextLocalId = 1;

    private LocalAdb(Socket socket, Keys keys, Listener listener) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = new BufferedOutputStream(socket.getOutputStream());
        this.keys = keys;
        this.listener = listener;
    }

    // =====================================================================
    // 对外接口
    // =====================================================================

    /**
     * 连接并完成认证。必须在后台线程调用。
     *
     * @param keyDir 保存密钥的目录（用 getFilesDir()）
     */
    public static LocalAdb connect(String host, int port, File keyDir, Listener listener)
            throws IOException {
        Keys keys;
        try {
            keys = Keys.loadOrCreate(keyDir);
        } catch (GeneralSecurityException e) {
            throw new IOException("密钥处理失败: " + e, e);
        }
        Socket s = new Socket();
        try {
            status(listener, "连接 " + host + ":" + port + " ...");
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            s.setSoTimeout(READ_TIMEOUT_MS);
            LocalAdb adb = new LocalAdb(s, keys, listener);
            adb.handshake();
            return adb;
        } catch (IOException | RuntimeException e) {
            closeQuietly(s);
            throw e;
        }
    }

    /** 执行一条 shell 命令（不使用 PTY；结尾会自动追加 echo 退出码）。 */
    public Result shell(String command) throws IOException {
        socket.setSoTimeout(SHELL_TIMEOUT_MS);
        int local = nextLocalId++;
        String full = command + "; echo " + EXIT_MARK + "$?";
        send(A_OPEN, local, 0, ("shell:" + full + "\0").getBytes(UTF8));

        Msg m = read();
        if (m.cmd == A_CLSE) {
            throw new IOException("设备拒绝执行命令（命令：" + command + "）");
        }
        if (m.cmd != A_OKAY) {
            throw new IOException("打开 shell 时收到意外的消息: " + cmdName(m.cmd) + "（命令：" + command + "）");
        }
        int remote = m.arg0;

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            m = read();
            if (m.cmd == A_WRTE) {
                if (m.arg1 != local) continue;
                buf.write(m.data, 0, m.data.length);
                send(A_OKAY, local, remote, null);
            } else if (m.cmd == A_CLSE) {
                if (m.arg1 != local) continue;
                try {
                    send(A_CLSE, local, remote, null);
                } catch (IOException ignored) {
                    // 对方已经关了，不影响结果
                }
                break;
            }
            // 其它消息（例如 OKAY）直接忽略
        }
        return parseResult(new String(buf.toByteArray(), UTF8));
    }

    @Override
    public void close() {
        closeQuietly(socket);
    }

    // =====================================================================
    // 握手与认证
    // =====================================================================

    private void handshake() throws IOException {
        send(A_CNXN, A_VERSION, MAX_PAYLOAD, "host::\0".getBytes(UTF8));
        Msg m = read();

        boolean signatureSent = false;
        boolean publicKeySent = false;
        while (m.cmd == A_AUTH) {
            if (m.arg0 != AUTH_TOKEN) {
                throw new IOException("认证过程中收到意外的消息类型 " + m.arg0);
            }
            if (!signatureSent) {
                status(listener, "发送认证签名 ...");
                send(A_AUTH, AUTH_SIGNATURE, 0, keys.sign(m.data));
                signatureSent = true;
            } else if (!publicKeySent) {
                status(listener, "这台电视还不认识本应用：请看电视屏幕，在“允许调试”窗口里点“允许”（建议勾选“一律允许”）...");
                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, keys.publicKeyMessage());
                publicKeySent = true;
                socket.setSoTimeout(USER_APPROVE_TIMEOUT_MS);
            } else {
                throw new AuthException("认证被拒绝（电视上没有点允许？）");
            }
            try {
                m = read();
            } catch (IOException e) {
                if (publicKeySent) {
                    throw new AuthException("认证没有通过（电视上没有点允许？）：" + e.getMessage());
                }
                throw e;
            }
        }
        if (m.cmd != A_CNXN) {
            throw new IOException("握手失败，收到 " + cmdName(m.cmd));
        }
        socket.setSoTimeout(SHELL_TIMEOUT_MS);
        status(listener, "已连接，认证通过");
    }

    // =====================================================================
    // 消息收发
    // =====================================================================

    private static final class Msg {
        int cmd;
        int arg0;
        int arg1;
        byte[] data;
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        ByteBuffer h = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd);
        h.putInt(arg0);
        h.putInt(arg1);
        h.putInt(len);
        h.putInt(checksum(data));
        h.putInt(cmd ^ 0xffffffff);
        out.write(h.array());
        if (len > 0) out.write(data);
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] hb = new byte[HEADER_SIZE];
        readFully(hb, 0, HEADER_SIZE);
        ByteBuffer h = ByteBuffer.wrap(hb).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = h.getInt();
        m.arg0 = h.getInt();
        m.arg1 = h.getInt();
        int len = h.getInt();
        h.getInt(); // data_check，新版本协议不校验
        int magic = h.getInt();
        if (magic != (m.cmd ^ 0xffffffff)) {
            throw new IOException("收到的不是 ADB 消息（magic 不对）");
        }
        if (len < 0 || len > MAX_ACCEPTED_PAYLOAD) {
            throw new IOException("消息长度异常: " + len);
        }
        m.data = new byte[len];
        if (len > 0) readFully(m.data, 0, len);
        return m;
    }

    private void readFully(byte[] buf, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(buf, off, len);
            if (n < 0) throw new EOFException("连接被对方关闭");
            off += n;
            len -= n;
        }
    }

    private static int checksum(byte[] data) {
        if (data == null) return 0;
        int sum = 0;
        for (byte b : data) sum += (b & 0xff);
        return sum;
    }

    private static String cmdName(int cmd) {
        byte[] b = new byte[4];
        b[0] = (byte) cmd;
        b[1] = (byte) (cmd >> 8);
        b[2] = (byte) (cmd >> 16);
        b[3] = (byte) (cmd >> 24);
        return new String(b, UTF8);
    }

    // =====================================================================
    // 输出解析
    // =====================================================================

    private static Result parseResult(String raw) {
        String text = raw.replace("\r\n", "\n");
        int exit = -1;
        int idx = text.lastIndexOf(EXIT_MARK);
        if (idx >= 0) {
            String tail = text.substring(idx + EXIT_MARK.length()).trim();
            try {
                exit = Integer.parseInt(tail);
            } catch (NumberFormatException ignored) {
                // 保持 -1
            }
            text = text.substring(0, idx);
        }
        while (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
        return new Result(text, exit);
    }

    private static void status(Listener l, String msg) {
        if (l != null) l.onStatus(msg);
    }

    private static void closeQuietly(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
            // ignore
        }
    }

    // =====================================================================
    // 密钥
    // =====================================================================

    /** RSA-2048 密钥对，私钥(PKCS#8)和公钥(X.509)分别存成文件。 */
    static final class Keys {
        final PrivateKey privateKey;
        final RSAPublicKey publicKey;

        private Keys(PrivateKey privateKey, RSAPublicKey publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        static Keys loadOrCreate(File dir) throws IOException, GeneralSecurityException {
            File privFile = new File(dir, "adbkey.pk8");
            File pubFile = new File(dir, "adbkey.x509");
            if (privFile.isFile() && pubFile.isFile()) {
                try {
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(privFile)));
                    RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(readAll(pubFile)));
                    return new Keys(priv, pub);
                } catch (GeneralSecurityException | IOException | RuntimeException e) {
                    // 文件损坏就重新生成（电视上会再弹一次允许）
                }
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("无法创建目录 " + dir);
            }
            writeAll(privFile, kp.getPrivate().getEncoded());
            writeAll(pubFile, kp.getPublic().getEncoded());
            return new Keys(kp.getPrivate(), (RSAPublicKey) kp.getPublic());
        }

        /** 对 adbd 发来的 20 字节 token 签名。 */
        byte[] sign(byte[] token) throws IOException {
            byte[] toSign = new byte[SHA1_DIGEST_INFO_PREFIX.length + token.length];
            System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, toSign, 0, SHA1_DIGEST_INFO_PREFIX.length);
            System.arraycopy(token, 0, toSign, SHA1_DIGEST_INFO_PREFIX.length, token.length);
            try {
                Signature s = Signature.getInstance("NONEwithRSA");
                s.initSign(privateKey);
                s.update(toSign);
                return s.sign();
            } catch (GeneralSecurityException first) {
                // 个别系统没有 NONEwithRSA，用裸 PKCS#1 填充代替（结果相同）
                try {
                    Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    c.init(Cipher.ENCRYPT_MODE, privateKey);
                    return c.doFinal(toSign);
                } catch (GeneralSecurityException second) {
                    throw new IOException("签名失败: " + first + " / " + second, second);
                }
            }
        }

        /** AUTH_RSAPUBLICKEY 的内容：base64(安卓格式公钥) + " 名字\0"。 */
        byte[] publicKeyMessage() throws IOException {
            String text = base64(encodeAndroidPublicKey(publicKey)) + " hdmiauto@tv\0";
            return text.getBytes(UTF8);
        }
    }

    /**
     * 安卓自己的公钥二进制格式（libcrypto_utils/android_pubkey.c）：
     * len(=64 个字) | n0inv | 模数(小端) | rr(小端) | 指数。
     */
    static byte[] encodeAndroidPublicKey(RSAPublicKey key) throws IOException {
        final int sizeBytes = 2048 / 8;
        final int sizeWords = sizeBytes / 4;
        BigInteger n = key.getModulus();
        BigInteger e = key.getPublicExponent();
        if (n.bitLength() != 2048) {
            throw new IOException("只支持 2048 位密钥");
        }
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(sizeBytes * 8).pow(2).mod(n);

        ByteBuffer b = ByteBuffer.allocate(4 + 4 + sizeBytes + sizeBytes + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(sizeWords);
        b.putInt(n0inv.intValue());
        b.put(toLittleEndian(n, sizeBytes));
        b.put(toLittleEndian(rr, sizeBytes));
        b.putInt(e.intValue());
        return b.array();
    }

    private static byte[] toLittleEndian(BigInteger v, int length) {
        byte[] be = v.toByteArray(); // 大端，可能多一个前导 0
        byte[] le = new byte[length];
        for (int i = 0; i < be.length; i++) {
            byte value = be[be.length - 1 - i];
            if (i < length) {
                le[i] = value;
            } else if (value != 0) {
                throw new IllegalArgumentException("数值太大");
            }
        }
        return le;
    }

    private static final String B64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    /** 标准 base64（带 = 补位、不换行）。自己写是为了不依赖 API 26 才有的 java.util.Base64。 */
    static String base64(byte[] d) {
        StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
        for (int i = 0; i < d.length; i += 3) {
            int b0 = d[i] & 0xff;
            int b1 = i + 1 < d.length ? d[i + 1] & 0xff : 0;
            int b2 = i + 2 < d.length ? d[i + 2] & 0xff : 0;
            sb.append(B64.charAt(b0 >> 2));
            sb.append(B64.charAt(((b0 & 3) << 4) | (b1 >> 4)));
            sb.append(i + 1 < d.length ? B64.charAt(((b1 & 15) << 2) | (b2 >> 6)) : '=');
            sb.append(i + 2 < d.length ? B64.charAt(b2 & 63) : '=');
        }
        return sb.toString();
    }

    private static byte[] readAll(File f) throws IOException {
        FileInputStream is = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            is.close();
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        try {
            os.write(data);
        } finally {
            os.close();
        }
    }
}
