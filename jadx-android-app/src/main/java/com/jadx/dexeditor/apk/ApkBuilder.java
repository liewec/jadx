package com.jadx.dexeditor.apk;

import com.android.apksig.ApkSigner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * APK 处理工具：
 * - 解包 APK 到目录
 * - 修改 / 替换文件
 * - 重打包为未签名 APK
 * - v1+v2+v3 签名
 *
 * <p>设计参考 Apktool（Apache License 2.0）的解包/重打包流程，
 * 但完全自实现，避免引入 Apktool 的依赖。
 */
public final class ApkBuilder {

    private ApkBuilder() {
    }

    /** 解包 APK 到指定目录（保留所有原始条目，包括 META-INF） */
    public static void unpack(File apk, File outDir) throws IOException {
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Cannot create dir: " + outDir);
        }
        try (ZipFile zf = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                File out = new File(outDir, e.getName());
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Cannot create dir: " + parent);
                }
                try (InputStream in = zf.getInputStream(e);
                     OutputStream out2 = new FileOutputStream(out)) {
                    copy(in, out2);
                }
            }
        }
    }

    /** 列出 APK 内所有条目 */
    public static List<String> listEntries(File apk) throws IOException {
        List<String> result = new ArrayList<>();
        try (ZipFile zf = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory()) result.add(e.getName());
            }
        }
        return result;
    }

    /** 读取 APK 中某个条目的字节 */
    public static byte[] readEntry(File apk, String entryName) throws IOException {
        try (ZipFile zf = new ZipFile(apk)) {
            ZipEntry e = zf.getEntry(entryName);
            if (e == null) return null;
            try (InputStream in = zf.getInputStream(e)) {
                return readAll(in);
            }
        }
    }

    /** 把目录打包为未签名 APK（跳过旧的 META-INF 签名块） */
    public static File pack(File srcDir, File outApk) throws IOException {
        if (srcDir == null) throw new IllegalArgumentException("srcDir is null");
        if (outApk.exists()) outApk.delete();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outApk))) {
            List<File> files = new ArrayList<>();
            collectFiles(srcDir, srcDir, files);
            for (File f : files) {
                String name = relativePath(srcDir, f);
                // 跳过旧签名
                if (name.startsWith("META-INF/") && (
                        name.endsWith(".SF") || name.endsWith(".RSA") ||
                                name.endsWith(".DSA") || name.endsWith(".EC") ||
                                name.equals("META-INF/MANIFEST.MF"))) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(name);
                entry.setSize(f.length());
                zos.putNextEntry(entry);
                try (InputStream in = new FileInputStream(f)) {
                    copy(in, zos);
                }
                zos.closeEntry();
            }
        }
        return outApk;
    }

    /** 替换 APK 中某个条目（其他条目保持不变），输出新 APK */
    public static File replaceEntry(File apk, String entryName, byte[] newData,
                                    File outApk) throws IOException {
        if (outApk.exists()) outApk.delete();
        boolean exists = false;
        try (ZipFile zf = new ZipFile(apk);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outApk))) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                // 跳过旧签名
                if (e.getName().startsWith("META-INF/") && (
                        e.getName().endsWith(".SF") || e.getName().endsWith(".RSA") ||
                                e.getName().endsWith(".DSA") || e.getName().endsWith(".EC") ||
                                e.getName().equals("META-INF/MANIFEST.MF"))) {
                    continue;
                }
                ZipEntry ne = new ZipEntry(e.getName());
                zos.putNextEntry(ne);
                if (e.getName().equals(entryName)) {
                    exists = true;
                    zos.write(newData);
                } else {
                    try (InputStream in = zf.getInputStream(e)) {
                        copy(in, zos);
                    }
                }
                zos.closeEntry();
            }
            // 如果要替换的条目原本不存在，追加
            if (!exists) {
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(newData);
                zos.closeEntry();
            }
        }
        return outApk;
    }

    /** 使用内置测试签名对 APK 进行 v2+v3 签名（不生成 v1 / JAR 签名） */
    public static File signWithBuiltinKey(File inputApk, File outputApk) throws Exception {
        KeyStore ks = BuiltinKey.loadKeyStore();
        KeyStore.PrivateKeyEntry pke = (KeyStore.PrivateKeyEntry)
                ks.getEntry(BuiltinKey.ALIAS, new KeyStore.PasswordProtection(BuiltinKey.PASSWORD));
        PrivateKey key = pke.getPrivateKey();
        java.security.cert.Certificate[] chain = pke.getCertificateChain();
        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate c : chain) {
            if (c instanceof X509Certificate) certs.add((X509Certificate) c);
        }
        return sign(inputApk, outputApk, key, certs);
    }

    /** 使用指定密钥对 APK 进行 v2+v3 签名（不生成 v1 / JAR 签名） */
    public static File sign(File inputApk, File outputApk, PrivateKey key,
                            List<X509Certificate> certs) throws Exception {
        if (outputApk.exists()) outputApk.delete();
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                "dex-editor", key, certs).build();
        ApkSigner apkSigner = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setV4SigningEnabled(false)
                .build();
        apkSigner.sign();
        return outputApk;
    }

    private static void collectFiles(File root, File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collectFiles(root, f, out);
            else out.add(f);
        }
    }

    private static String relativePath(File root, File f) {
        String rp = root.getAbsolutePath();
        String fp = f.getAbsolutePath();
        if (fp.startsWith(rp)) {
            String rel = fp.substring(rp.length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            return rel.replace('\\', '/');
        }
        return f.getName();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        copy(in, bo);
        return bo.toByteArray();
    }
}
