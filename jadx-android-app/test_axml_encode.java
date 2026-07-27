import com.jadx.dexeditor.axml.BinaryXml;
import com.jadx.dexeditor.axml.BinaryXml.Document;
import com.jadx.dexeditor.axml.BinaryXml.Element;
import com.jadx.dexeditor.axml.BinaryXml.Attribute;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 验证 BinaryXml.encode 输出的二进制 AXML 格式是否符合 AOSP 标准。
 * 检查各 chunk 的 headerSize / size / attributeStart 是否正确。
 */
public class test_axml_encode {
    public static void main(String[] args) throws Exception {
        // 构造一个简单的 <manifest><uses-sdk android:minSdkVersion="26"/></manifest>
        Element manifest = new Element();
        manifest.name = "manifest";
        manifest.lineNumber = 1;
        Attribute pkg = new Attribute();
        pkg.name = "package";
        pkg.ns = null;
        pkg.rawValue = "com.test";
        pkg.typedType = BinaryXml.TYPE_STRING;
        manifest.attributes.add(pkg);

        Element usesSdk = new Element();
        usesSdk.name = "uses-sdk";
        usesSdk.lineNumber = 2;
        Attribute minSdk = new Attribute();
        minSdk.name = "minSdkVersion";
        minSdk.ns = "http://schemas.android.com/apk/res/android";
        minSdk.rawValue = "26";
        minSdk.typedType = BinaryXml.TYPE_INT_DEC;
        minSdk.typedValue = 26;
        usesSdk.attributes.add(minSdk);

        manifest.children.add(usesSdk);

        Document doc = new Document();
        doc.roots.add(manifest);

        byte[] bin = BinaryXml.encode(doc);
        System.out.println("Encoded size: " + bin.length + " bytes");

        // 解析各 chunk 并验证
        ByteBuffer b = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);

        // XML header
        int xmlType = b.getShort() & 0xffff;
        int xmlHeaderSize = b.getShort() & 0xffff;
        int xmlSize = b.getInt();
        System.out.printf("XML header: type=0x%04x headerSize=%d size=%d%n", xmlType, xmlHeaderSize, xmlSize);
        check("XML type", xmlType, 0x0003);
        check("XML headerSize", xmlHeaderSize, 8);

        // 遍历子 chunk
        while (b.remaining() >= 8) {
            int pos = b.position();
            int cType = b.getShort() & 0xffff;
            int cHeader = b.getShort() & 0xffff;
            int cSize = b.getInt();
            String typeName = chunkName(cType);
            System.out.printf("Chunk @%d: type=0x%04x(%s) headerSize=%d size=%d%n",
                    pos, cType, typeName, cHeader, cSize);

            switch (cType) {
                case 0x0001: // STRING_POOL
                    check("StringPool headerSize", cHeader, 28);
                    break;
                case 0x0180: // RESOURCE_MAP
                    check("ResourceMap headerSize", cHeader, 8);
                    break;
                case 0x0102: // START_ELEMENT
                    check("StartElement headerSize", cHeader, 36);
                    // 读取 attrStart
                    b.getInt(); // lineNumber
                    b.getInt(); // comment
                    b.getInt(); // ns
                    b.getInt(); // name
                    int attrStart = b.getShort() & 0xffff;
                    int attrSize = b.getShort() & 0xffff;
                    int attrCount = b.getShort() & 0xffff;
                    System.out.printf("  attrStart=%d attrSize=%d attrCount=%d%n", attrStart, attrSize, attrCount);
                    check("StartElement attrStart", attrStart, 36);
                    check("StartElement attrSize", attrSize, 20);
                    check("StartElement chunkSize", cSize, 36 + attrCount * 20);
                    break;
                case 0x0103: // END_ELEMENT
                    check("EndElement headerSize", cHeader, 24);
                    check("EndElement size", cSize, 24);
                    break;
                case 0x0104: // CDATA
                    check("CDATA headerSize", cHeader, 28);
                    check("CDATA size", cSize, 28);
                    break;
            }
            b.position(pos + cSize);
        }

        // 验证 round-trip：解码后重新编码
        Document decoded = BinaryXml.decode(bin);
        byte[] reencoded = BinaryXml.encode(decoded);
        System.out.println("Round-trip reencoded size: " + reencoded.length + " bytes");

        // 解码验证内容
        System.out.println("Decoded roots: " + decoded.roots.size());
        for (Element root : decoded.roots) {
            System.out.println("  Root element: " + root.name + " attrs=" + root.attributes.size());
            for (Attribute a : root.attributes) {
                System.out.printf("    attr: ns=%s name=%s raw=%s type=0x%02x val=%d%n",
                        a.ns, a.name, a.rawValue, a.typedType, a.typedValue);
            }
            for (Object child : root.children) {
                if (child instanceof Element) {
                    Element c = (Element) child;
                    System.out.println("  Child element: " + c.name + " attrs=" + c.attributes.size());
                    for (Attribute a : c.attributes) {
                        System.out.printf("    attr: ns=%s name=%s raw=%s type=0x%02x val=%d%n",
                                a.ns, a.name, a.rawValue, a.typedType, a.typedValue);
                    }
                }
            }
        }

        System.out.println("\n=== ALL CHECKS PASSED ===");
    }

    static void check(String name, int actual, int expected) {
        if (actual != expected) {
            throw new RuntimeException("FAIL: " + name + " = " + actual + " (expected " + expected + ")");
        }
        System.out.println("  OK: " + name + " = " + actual);
    }

    static String chunkName(int type) {
        switch (type) {
            case 0x0001: return "STRING_POOL";
            case 0x0003: return "XML";
            case 0x0100: return "START_NS";
            case 0x0101: return "END_NS";
            case 0x0102: return "START_ELEMENT";
            case 0x0103: return "END_ELEMENT";
            case 0x0104: return "CDATA";
            case 0x0180: return "RESOURCE_MAP";
            default: return "UNKNOWN(0x" + Integer.toHexString(type) + ")";
        }
    }
}
