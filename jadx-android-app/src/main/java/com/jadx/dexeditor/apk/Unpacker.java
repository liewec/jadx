package com.jadx.dexeditor.apk;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * APK 壳检测 / 脱壳工具。
 * <p>
 * 检测逻辑（特征识别）：
 * - manifest application 中是否声明已知壳的 Application 类
 * - APK 中是否含有已知壳的特征 so / dex
 * - 主 dex 字符串池中是否含有壳的特征字符串
 * <p>
 * 脱壳策略（无需 root，参考 blackdex 思路）：
 * 由于本项目运行在普通应用沙箱中，没有 root / hook 权限，
 * 这里实现"静态脱壳"：识别壳的特征 dex / so，尝试从 APK 中直接提取壳加载的真实 dex。
 * 对于"加壳即运行时解密 dex"的强壳（如梆梆、爱加密新版），普通应用无法静态提取，
 * 此时返回检测结果，提示用户用 blackdex 在 root 环境下脱壳后再次导入。
 * <p>
 * License 参考：blackdex (Apache License 2.0, codinggay/blackdex)。
 */
public final class Unpacker {
    private static final String TAG = "Unpacker";

    /** 已知壳特征表：壳名 → (application 类前缀 / 特征文件 / 特征字符串) */
    private static final PackerInfo[] PACKERS = {
            new PackerInfo("360加固", new String[]{"com.stub.StubApp", "com.qihoo.util"},
                    new String[]{"libjiagu.so", "libjiagu_a64.so", "libjiagu_x86.so"},
                    new String[]{"qihoo", "jiagu"}),
            new PackerInfo("腾讯乐固/TPS", new String[]{"com.tencent.StubShell.TxAppEntry", "com.tencent.bugly.lego"},
                    new String[]{"libshell.so", "libshella.so", "libshellx.so", "libBugly.so", "libtup.so"},
                    new String[]{"tencent.shell", "legu"}),
            new PackerInfo("爱加密", new String[]{"s.h.e.l.l.S", "com.ijm.shell"},
                    new String[]{"libexec.so", "libexecmain.so", "ijm_data/"},
                    new String[]{"ijiami", "ijm"}),
            new PackerInfo("梆梆加固", new String[]{"com.bangcle.apkprotect.AppApplication", "com.secapk.wrapper.ApplicationWrapper"},
                    new String[]{"libsecexe.so", "libsecmain.so", "libDexHelper.so", "libDexHelperX.so"},
                    new String[]{"bangcle", "secapk"}),
            new PackerInfo("百度加固", new String[]{"com.baidu.protect.StubApplication", "com.baidu.sapi.CoreAccess"},
                    new String[]{"libbaiduprotect.so", "baiduprotect.jar"},
                    new String[]{"baidu.protect", "baiduprotect"}),
            new PackerInfo("阿里聚安全", new String[]{"com.taobao.wireless.security.adapter"},
                    new String[]{"libmobisec.so", "libsgmainso.so", "libsgsecuritybody.so"},
                    new String[]{"mobisec", "taobao.security"}),
            new PackerInfo("娜迦(Nagain)", new String[]{"com.nagain.nagainhook"},
                    new String[]{"libnagain.so", "libnaga.so"},
                    new String[]{"nagain", "naga"}),
            new PackerInfo("顶象加固", new String[]{"com.dx.DxApplication"},
                    new String[]{"libx3g.so"},
                    new String[]{"dx.mobile", "dingxiang"}),
            new PackerInfo("通付盾加固", new String[]{"com.tongfudun.android.shell"},
                    new String[]{"libtup.so"},
                    new String[]{"tongfudun"}),
            new PackerInfo("海云安加固", new String[]{"com.haiyun.android"},
                    new String[]{"libitsec.so"},
                    new String[]{"haiyun"}),
            new PackerInfo("几维安全", new String[]{"com.kiwisec.shell"},
                    new String[]{"libkwscmm.so", "libkwscr.so", "libkwslinker.so"},
                    new String[]{"kiwisec"}),
            new PackerInfo("通付盾MTP", new String[]{"com.tongfudun.mtp"},
                    new String[]{"libmtp.so"},
                    new String[]{"tongfudun.mtp"}),
    };

    public static final class PackerInfo {
        public final String name;
        public final String[] appClasses;
        public final String[] featureFiles;
        public final String[] featureStrings;

        public PackerInfo(String name, String[] appClasses, String[] featureFiles, String[] featureStrings) {
            this.name = name;
            this.appClasses = appClasses;
            this.featureFiles = featureFiles;
            this.featureStrings = featureStrings;
        }
    }

    public static final class DetectResult {
        public final boolean packed;
        public final String packerName;
        public final List<String> evidences;

        public DetectResult(boolean packed, String packerName, List<String> evidences) {
            this.packed = packed;
            this.packerName = packerName;
            this.evidences = evidences;
        }

        @Override
        public String toString() {
            if (!packed) return "未检测到壳";
            StringBuilder sb = new StringBuilder();
            sb.append("已检测到壳：").append(packerName).append("\n");
            sb.append("证据：\n");
            for (String e : evidences) sb.append("  • ").append(e).append("\n");
            return sb.toString();
        }
    }

    public static final class UnpackResult {
        public final boolean success;
        public final String message;
        public final List<File> dexFiles;

        public UnpackResult(boolean success, String message, List<File> dexFiles) {
            this.success = success;
            this.message = message;
            this.dexFiles = dexFiles;
        }
    }

    private Unpacker() {
    }

    /** 检测 APK 是否有壳 */
    public static DetectResult detect(File apk) {
        List<String> evidences = new ArrayList<>();
        String matched = null;
        try {
            // 1. 列出所有条目
            Set<String> entries = new LinkedHashSet<>();
            try (ZipFile zf = new ZipFile(apk)) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (!e.isDirectory()) entries.add(e.getName().toLowerCase(Locale.ROOT));
                }
            }

            // 2. 检查特征文件
            for (PackerInfo p : PACKERS) {
                for (String ff : p.featureFiles) {
                    for (String entry : entries) {
                        if (entry.equals(ff.toLowerCase(Locale.ROOT)) || entry.endsWith("/" + ff.toLowerCase(Locale.ROOT))) {
                            evidences.add("特征文件：" + ff + "（" + p.name + "）");
                            if (matched == null) matched = p.name;
                        }
                    }
                }
            }

            // 3. 读 AndroidManifest.xml（二进制 AXML）扫描 application 类名特征
            byte[] manifestBytes = ApkBuilder.readEntry(apk, "AndroidManifest.xml");
            if (manifestBytes != null) {
                String manifestAscii = bytesToAscii(manifestBytes);
                for (PackerInfo p : PACKERS) {
                    for (String ac : p.appClasses) {
                        if (manifestAscii.contains(ac)) {
                            evidences.add("Application 类：" + ac + "（" + p.name + "）");
                            if (matched == null) matched = p.name;
                        }
                    }
                }
            }

            // 4. 读 classes.dex 扫描特征字符串
            byte[] classesDex = ApkBuilder.readEntry(apk, "classes.dex");
            if (classesDex != null) {
                String dexAscii = bytesToAscii(classesDex);
                for (PackerInfo p : PACKERS) {
                    for (String fs : p.featureStrings) {
                        if (dexAscii.contains(fs)) {
                            evidences.add("DEX 特征字符串：" + fs + "（" + p.name + "）");
                            if (matched == null) matched = p.name;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "detect failed", t);
        }
        return new DetectResult(matched != null, matched, evidences);
    }

    /**
     * 静态脱壳：尝试从 APK 中提取壳附带的 dex。
     * <p>
     * 仅能处理"把 dex 藏在 assets/zip 内"的弱壳；
     * 对于"运行时解密 dex"的强壳，返回 success=false 并提示用户。
     */
    public static UnpackResult unpack(File apk, File outDir) {
        if (!outDir.exists() && !outDir.mkdirs()) {
            return new UnpackResult(false, "无法创建输出目录：" + outDir, new ArrayList<>());
        }
        List<File> extracted = new ArrayList<>();
        try (ZipFile zf = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int idx = 0;
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".dex")) {
                    // 提取所有 dex
                    File out = new File(outDir, "extracted_" + idx + "_" + new File(e.getName()).getName());
                    try (InputStream in = zf.getInputStream(e);
                         OutputStream os = new FileOutputStream(out)) {
                        copy(in, os);
                    }
                    extracted.add(out);
                    idx++;
                } else if (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".apk")) {
                    // 壳可能把真实 dex 藏在 jar/zip/apk 里
                    File tmp = new File(outDir, "tmp_" + idx + "_" + new File(e.getName()).getName());
                    try (InputStream in = zf.getInputStream(e);
                         OutputStream os = new FileOutputStream(tmp)) {
                        copy(in, os);
                    }
                    List<File> inner = extractDexFromZip(tmp, outDir, idx);
                    extracted.addAll(inner);
                    // noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    idx++;
                }
            }
        } catch (Throwable t) {
            return new UnpackResult(false, "脱壳失败：" + t.getMessage(), extracted);
        }
        if (extracted.isEmpty()) {
            return new UnpackResult(false,
                    "静态脱壳未提取到 dex。\n该 APK 可能使用了运行时解密的强壳，请使用 blackdex（需 root）在真实环境脱壳后再导入。",
                    new ArrayList<>());
        }
        // 校验 dex 魔数
        List<File> valid = new ArrayList<>();
        for (File f : extracted) {
            if (isValidDex(f)) valid.add(f);
        }
        if (valid.isEmpty()) {
            return new UnpackResult(false, "提取到的文件都不是有效的 dex。", extracted);
        }
        return new UnpackResult(true, "已提取 " + valid.size() + " 个 dex 文件到 " + outDir, valid);
    }

    private static List<File> extractDexFromZip(File zip, File outDir, int baseIdx) {
        List<File> result = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int idx = 0;
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".dex")) {
                    File out = new File(outDir, "inner_" + baseIdx + "_" + idx + "_" + new File(e.getName()).getName());
                    try (InputStream in = zf.getInputStream(e);
                         OutputStream os = new FileOutputStream(out)) {
                        copy(in, os);
                    }
                    result.add(out);
                    idx++;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "extractDexFromZip failed for " + zip, t);
        }
        return result;
    }

    private static boolean isValidDex(File f) {
        if (f == null || !f.exists() || f.length() < 8) return false;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            byte[] magic = new byte[8];
            raf.readFully(magic);
            // dex\n035\0 / dex\n036\0 / dex\n037\0 / dex\n038\0 / dex\n039\0
            return magic[0] == 'd' && magic[1] == 'e' && magic[2] == 'x' && magic[3] == '\n';
        } catch (IOException e) {
            return false;
        }
    }

    private static String bytesToAscii(byte[] data) {
        // 提取所有可打印 ASCII 字符序列
        StringBuilder sb = new StringBuilder(data.length);
        for (byte b : data) {
            int c = b & 0xff;
            sb.append((char) c);
        }
        return sb.toString();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }
}
