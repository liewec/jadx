package com.jadx.dexeditor.apk;

import android.util.Log;

import com.jadx.dexeditor.PathConfig;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * 内置测试签名密钥（用于 APK 重打包后自动签名）。
 * <p>
 * 首次使用时生成一个 RSA 2048 自签名证书，缓存到 PathConfig.getKeyStoreDir()/dex-editor.p12。
 * 后续直接复用，避免每次重新签名都需要外部密钥库。
 * <p>
 * 注意：自签名证书仅用于本地测试 / 二次开发，安装时需要先信任本应用。
 */
public final class BuiltinKey {
    private static final String TAG = "BuiltinKey";
    public static final String ALIAS = "dex-editor";
    public static final char[] PASSWORD = "dex-editor".toCharArray();
    private static final String KEYSTORE_FILE = "dex-editor.p12";
    private static final String KEYSTORE_TYPE = "PKCS12";

    private static volatile KeyStore cached;

    private BuiltinKey() {
    }

    public static synchronized KeyStore loadKeyStore() throws Exception {
        if (cached != null) return cached;
        File ksFile = getPath();
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        if (ksFile.exists()) {
            try (FileInputStream in = new FileInputStream(ksFile)) {
                ks.load(in, PASSWORD);
            }
            if (ks.containsAlias(ALIAS)) {
                cached = ks;
                return ks;
            }
        }
        // 生成新密钥
        Log.i(TAG, "Generating new self-signed RSA 2048 keypair at " + ksFile);
        ks.load(null, PASSWORD);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();
        X509Certificate cert = SelfSignedCertGen.generate(kp, "CN=Dex Editor, O=52pojie, C=CN", 30 * 365L);
        ks.setKeyEntry(ALIAS, kp.getPrivate(), PASSWORD, new Certificate[]{cert});
        File parent = ksFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create keystore dir: " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(ksFile)) {
            ks.store(out, PASSWORD);
        }
        cached = ks;
        return ks;
    }

    private static File getPath() {
        File dir = PathConfig.get().getKeyStoreDir();
        return new File(dir, KEYSTORE_FILE);
    }
}
