package crystal.plot;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文 CJK 循环偏移混淆器（构建脚本专用，纯标准库）。
 * 与游戏运行时共用同一套算法，确保打包混淆 ↔ 运行解密一致。
 */
public class PlotObfuscator {
    private static final int CJK_START = 0x4E00;
    private static final int CJK_END   = 0x9FFF;
    private static final int CJK_SIZE  = CJK_END - CJK_START + 1;

    /** 默认密钥，必须与 PlotBundle 解密时保持一致 */
    public static final int DEFAULT_KEY = 0x2A7B;

    private PlotObfuscator() {}

    public static String obfuscate(String plain) {
        return obfuscate(plain, DEFAULT_KEY);
    }

    public static String obfuscate(String plain, int key) {
        if (plain == null) return null;
        StringBuilder sb = new StringBuilder(plain.length());
        for (int cp : plain.codePoints().toArray()) {
            if (isCJK(cp)) {
                int offset = (cp - CJK_START + key) % CJK_SIZE;
                if (offset < 0) offset += CJK_SIZE;
                sb.appendCodePoint(CJK_START + offset);
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    public static String deobfuscate(String obfuscated) {
        return deobfuscate(obfuscated, DEFAULT_KEY);
    }

    public static String deobfuscate(String obfuscated, int key) {
        return obfuscate(obfuscated, CJK_SIZE - (Math.abs(key) % CJK_SIZE));
    }

    public static List<String> obfuscateLines(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String s : lines) out.add(obfuscate(s));
        return out;
    }

    private static boolean isCJK(int codePoint) {
        return codePoint >= CJK_START && codePoint <= CJK_END;
    }
}
