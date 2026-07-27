import com.jadx.dexeditor.axml.BinaryXml;
import com.jadx.dexeditor.axml.BinaryXml.Document;
import com.jadx.dexeditor.axml.BinaryXml.Element;
import com.jadx.dexeditor.axml.BinaryXml.Attribute;
import com.android.apksig.ApkSigner;
import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.*;

/**
 * 端到端测试：构造 manifest → 编码 → 替换进真实 APK → 用 apksig 签名
 * 验证修复 "Unable to determine APK's minimum supported Android platform version:
 *           malformed binary resource: AndroidManifest.xml" 错误。
 */
public class test_sign_e2e {
    public static void main(String[] args) throws Exception {
        String androidNs = "http://schemas.android.com/apk/res/android";

        Element manifest = new Element();
        manifest.name = "manifest";
        manifest.lineNumber = 1;
        addAttr(manifest, null, "package", "com.test", BinaryXml.TYPE_STRING, 0);
        addAttr(manifest, androidNs, "versionCode", "1", BinaryXml.TYPE_INT_DEC, 1);
        addAttr(manifest, androidNs, "versionName", "1.0", BinaryXml.TYPE_STRING, 0);

        Element usesSdk = new Element();
        usesSdk.name = "uses-sdk";
        usesSdk.lineNumber = 2;
        addAttr(usesSdk, androidNs, "minSdkVersion", "26", BinaryXml.TYPE_INT_DEC, 26);
        addAttr(usesSdk, androidNs, "targetSdkVersion", "34", BinaryXml.TYPE_INT_DEC, 34);
        manifest.children.add(usesSdk);

        Element application = new Element();
        application.name = "application";
        application.lineNumber = 3;
        addAttr(application, androidNs, "label", "Test", BinaryXml.TYPE_STRING, 0);
        manifest.children.add(application);

        Document doc = new Document();
        doc.roots.add(manifest);

        byte[] manifestBin = BinaryXml.encode(doc);
        System.out.println("Encoded AndroidManifest.xml: " + manifestBin.length + " bytes");

        File templateApk = new File("/workspace/jadx-android-app/build/outputs/apk/debug/dex-editor-debug.apk");
        File outDir = new File("/tmp/test_sign");
        outDir.mkdirs();
        File editedApk = new File(outDir, "test_edited.apk");
        File signedApk = new File(outDir, "test_signed.apk");

        // 替换 AndroidManifest.xml
        replaceEntry(templateApk, "AndroidManifest.xml", manifestBin, editedApk);
        System.out.println("Edited APK: " + editedApk.length() + " bytes");

        // 从 keytool 生成的密钥库加载密钥
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream("/tmp/test_sign/test.jks")) {
            ks.load(fis, "test1234".toCharArray());
        }
        KeyStore.PrivateKeyEntry pke = (KeyStore.PrivateKeyEntry)
                ks.getEntry("test", new KeyStore.PasswordProtection("test1234".toCharArray()));
        PrivateKey key = pke.getPrivateKey();
        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate c : pke.getCertificateChain()) {
            if (c instanceof X509Certificate) certs.add((X509Certificate) c);
        }

        // 用 apksig 签名（v2 + v3，不生成 v1）—— 与 ApkBuilder.sign 配置一致
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                "dex-editor", key, certs).build();
        ApkSigner apkSigner = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(editedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setV4SigningEnabled(false)
                .build();

        try {
            apkSigner.sign();
            System.out.println("=== SIGN SUCCESS ===");
            System.out.println("Signed APK: " + signedApk.length() + " bytes");
            System.out.println("Signed APK path: " + signedApk.getAbsolutePath());
        } catch (Throwable e) {
            System.out.println("=== SIGN FAILED ===");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static void addAttr(Element e, String ns, String name, String rawVal, int typedType, int typedValue) {
        Attribute a = new Attribute();
        a.ns = ns;
        a.name = name;
        a.rawValue = rawVal;
        a.typedType = typedType;
        a.typedValue = typedValue;
        e.attributes.add(a);
    }

    static void replaceEntry(File apk, String entryName, byte[] newData, File outApk) throws IOException {
        if (outApk.exists()) outApk.delete();
        try (ZipFile zf = new ZipFile(apk);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outApk))) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                if (e.getName().startsWith("META-INF/") && (
                        e.getName().endsWith(".SF") || e.getName().endsWith(".RSA") ||
                        e.getName().endsWith(".DSA") || e.getName().endsWith(".EC") ||
                        e.getName().equals("META-INF/MANIFEST.MF"))) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(e.getName()));
                if (e.getName().equals(entryName)) {
                    zos.write(newData);
                } else {
                    try (InputStream in = zf.getInputStream(e)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) zos.write(buf, 0, n);
                    }
                }
                zos.closeEntry();
            }
        }
    }
}
