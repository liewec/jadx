package com.jadx.dexeditor.apk;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * 极简自签名 X.509 v3 证书生成器（无 BouncyCastle / sun.security 依赖）。
 * <p>
 * 仅使用 JDK 标准 API + 手写 DER 编码，适用于 Android 平台。
 * 仅生成最小可用字段：serial, subject/issuer (相同), validity, SubjectPublicKeyInfo,
 * SHA256withRSA 签名。
 */
final class SelfSignedCertGen {
    private static final String TAG = "SelfSignedCertGen";

    private SelfSignedCertGen() {
    }

    static X509Certificate generate(KeyPair kp, String subject, long validDays) throws Exception {
        PrivateKey priv = kp.getPrivate();
        PublicKey pub = kp.getPublic();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 24L * 3600 * 1000);
        Date notAfter = new Date(now + validDays * 24L * 3600 * 1000);

        byte[] tbs = encodeTbsCertificate(pub, subject, notBefore, notAfter);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv);
        sig.update(tbs);
        byte[] signature = sig.sign();

        byte[] certDer = encodeCertificate(tbs, signature);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(certDer));
    }

    // ===== DER encoding helpers =====

    private static byte[] encodeCertificate(byte[] tbs, byte[] signature) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30); // SEQUENCE
        byte[] body = concat(
                tbs,
                algIdSha256Rsa(),
                wrapBitString(signature)
        );
        writeLength(out, body.length);
        out.write(body);
        return out.toByteArray();
    }

    private static byte[] encodeTbsCertificate(PublicKey pub, String subject,
                                                Date notBefore, Date notAfter) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // version [0] EXPLICIT INTEGER (v3 = 2)
        byte[] version = derInteger(BigInteger.valueOf(2));
        byte[] versionExplicit = derTag(0xA0, version);
        // serial
        byte[] serial = derInteger(BigInteger.valueOf(System.currentTimeMillis()));
        // signature alg
        byte[] sigAlg = algIdSha256Rsa();
        // issuer = subject
        byte[] name = encodeName(subject);
        // validity
        byte[] validity = derSequence(
                derUTCTime(notBefore),
                derUTCTime(notAfter)
        );
        // subject public key info (SubjectPublicKeyInfo - JDK 提供)
        byte[] spki = pub.getEncoded();

        byte[] body = concat(
                versionExplicit,
                serial,
                sigAlg,
                name,
                validity,
                name, // subject == issuer
                spki
        );
        // Wrap in SEQUENCE
        out.write(0x30);
        writeLength(out, body.length);
        out.write(body);
        return out.toByteArray();
    }

    private static byte[] encodeName(String dn) {
        // Name ::= SEQUENCE OF RelativeDistinguishedName
        // RelativeDistinguishedName ::= SET OF AttributeTypeAndValue   (SET)
        // AttributeTypeAndValue ::= SEQUENCE { type OID, value }       (SEQUENCE)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30); // SEQUENCE (Name)
        ByteArrayOutputStream nameBody = new ByteArrayOutputStream();
        for (String rdn : dn.split(",")) {
            String[] kv = rdn.trim().split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            String val = kv[1].trim();
            byte[] oid = oidForKey(key);
            if (oid == null) continue;
            // 先 SEQUENCE{OID, value} 再用 SET 包起来
            byte[] atv = derSequence(oid, derUTF8String(val));
            byte[] rdnSet = derSet(atv);
            nameBody.write(rdnSet, 0, rdnSet.length);
        }
        byte[] body = nameBody.toByteArray();
        writeLength(out, body.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static byte[] oidForKey(String key) {
        switch (key.toUpperCase()) {
            case "CN": return derOid(new long[]{2, 5, 4, 3});
            case "O":  return derOid(new long[]{2, 5, 4, 10});
            case "OU": return derOid(new long[]{2, 5, 4, 11});
            case "C":  return derOid(new long[]{2, 5, 4, 6});
            case "ST": return derOid(new long[]{2, 5, 4, 8});
            case "L":  return derOid(new long[]{2, 5, 4, 7});
            default: return null;
        }
    }

    private static byte[] algIdSha256Rsa() {
        // OID 1.2.840.113549.1.1.11 (sha256WithRSAEncryption), NULL params
        byte[] oid = derOid(new long[]{1, 2, 840, 113549, 1, 1, 11});
        return derSequence(concat(oid, derNull()));
    }

    // ===== DER primitives =====

    private static byte[] derInteger(BigInteger v) {
        byte[] content = v.toByteArray();
        return derTag(0x02, content);
    }

    private static byte[] derNull() {
        return new byte[]{0x05, 0x00};
    }

    private static byte[] derUTCTime(Date d) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.ROOT);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return derTag(0x17, sdf.format(d).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static byte[] derUTF8String(String s) {
        return derTag(0x0C, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] derSet(byte[]... items) {
        return derTag(0x31, concat(items));
    }

    private static byte[] derSequence(byte[]... items) {
        return derTag(0x30, concat(items));
    }

    private static byte[] derSequence(byte[] body) {
        return derTag(0x30, body);
    }

    private static byte[] derOid(long[] arcs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x06); // OBJECT IDENTIFIER
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write((int) (40 * arcs[0] + arcs[1]));
        for (int i = 2; i < arcs.length; i++) {
            writeBase128(body, arcs[i]);
        }
        byte[] bodyBytes = body.toByteArray();
        writeLength(out, bodyBytes.length);
        out.write(bodyBytes, 0, bodyBytes.length);
        return out.toByteArray();
    }

    private static void writeBase128(ByteArrayOutputStream out, long v) {
        if (v < 0x80) {
            out.write((int) v);
            return;
        }
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        tmp.write((int) (v & 0x7f));
        v >>= 7;
        while (v != 0) {
            tmp.write((int) ((v & 0x7f) | 0x80));
            v >>= 7;
        }
        byte[] arr = tmp.toByteArray();
        // reverse
        for (int i = arr.length - 1; i >= 0; i--) out.write(arr[i]);
    }

    private static byte[] wrapBitString(byte[] data) {
        // BIT STRING: 1 byte unused bits = 0, then data
        byte[] content = new byte[data.length + 1];
        content[0] = 0;
        System.arraycopy(data, 0, content, 1, data.length);
        return derTag(0x03, content);
    }

    private static byte[] derTag(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        writeLength(out, content.length);
        out.write(content, 0, content.length);
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int len) {
        if (len < 0x80) {
            out.write(len);
        } else if (len < 0x100) {
            out.write(0x81);
            out.write(len);
        } else if (len < 0x10000) {
            out.write(0x82);
            out.write(len >> 8);
            out.write(len & 0xff);
        } else if (len < 0x1000000) {
            out.write(0x83);
            out.write(len >> 16);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        } else {
            out.write(0x84);
            out.write(len >> 24);
            out.write((len >> 16) & 0xff);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, off, a.length);
            off += a.length;
        }
        return out;
    }
}
