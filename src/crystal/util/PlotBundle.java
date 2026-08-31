package crystal.util;

import arc.files.Fi;
import arc.func.Cons2;
import arc.math.Rand;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;

import java.io.*;
import java.util.Comparator;
import java.util.MissingResourceException;

public class PlotBundle {
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final int DEFAULT_KEY = PlotObfuscator.DEFAULT_KEY;

    private ObjectMap<String, String> properties = new ObjectMap<>();
    private PlotBundle parent;
    private String name = "unknown";
    private int decodeKey = DEFAULT_KEY;
    private Fi sourceFile;

    private PlotBundle() {
    }

    public static PlotBundle load(Fi file) {
        return load(file, DEFAULT_KEY, null);
    }

    public static PlotBundle load(Fi file, int decodeKey) {
        return load(file, decodeKey, null);
    }

    public static PlotBundle load(Fi file, PlotBundle parent) {
        return load(file, DEFAULT_KEY, parent);
    }

    public static PlotBundle load(Fi file, int decodeKey, PlotBundle parent) {
        PlotBundle bundle = new PlotBundle();
        bundle.sourceFile = file;
        bundle.decodeKey = decodeKey;
        bundle.parent = parent;
        bundle.name = file.nameWithoutExtension();
        // 【修复】原来只判断 file.exists()：传入目录时 exists() 也为 true，
        // reader() 会抛异常导致静默返回空 bundle；文件不存在时更是毫无提示。
        if (file.exists() && !file.isDirectory()) {
            try (Reader reader = file.reader(DEFAULT_ENCODING)) {
                bundle.load(reader);
                Log.info("[PlotBundle] 已加载 @ 条剧情文本: @", bundle.size(), file.absolutePath());
            } catch (Exception e) {
                Log.err("[PlotBundle] Failed to load: " + file.absolutePath(), e);
            }
        } else {
            Log.warn("[PlotBundle] 剧情文件不存在或不是文件: @", file.absolutePath());
        }
        return bundle;
    }

    public static PlotBundle loadFromMod(Fi modRoot) {
        return loadFromMod(modRoot, DEFAULT_KEY, null);
    }

    public static PlotBundle loadFromMod(Fi modRoot, PlotBundle parent) {
        return loadFromMod(modRoot, DEFAULT_KEY, parent);
    }

    public static PlotBundle loadFromMod(Fi modRoot, int decodeKey, PlotBundle parent) {
        if (modRoot == null) {
            Log.err("[PlotBundle] modRoot 为 null，无法定位 plot.properties（getMod 未找到本 mod，请检查 mod.json 的 name 字段）");
            return new PlotBundle();
        }
        // 候选路径：正常路径 + assets 兼容路径 + 常见误拼写 propertise
        String[] candidates = {
                "plot/plot.properties",
                "assets/plot/plot.properties",
                "plot/plot.propertise",
                "assets/plot/plot.propertise",
                "plot.properties"
        };
        Fi plotFile = null;
        for (String path : candidates) {
            Fi f = childPath(modRoot, path);
            if (f.exists() && !f.isDirectory()) {
                plotFile = f;
                break;
            }
        }
        if (plotFile == null) {
            // 诊断：打印 mod 根目录和 plot/ 目录的实际内容，方便定位放错位置/拼错文件名的情况
            Log.err("[PlotBundle] 在 mod 根目录下找不到 plot.properties，modRoot=@，已尝试路径: plot/plot.properties, assets/plot/plot.properties 等", modRoot.absolutePath());
            try {
                for (Fi f : modRoot.list())
                    Log.err("[PlotBundle]   根目录条目: @", f.name());
                Fi plotDir = childPath(modRoot, "plot");
                if (plotDir.exists() && plotDir.isDirectory()) {
                    for (Fi f : plotDir.list())
                        Log.err("[PlotBundle]   plot/ 条目: @", f.name());
                }
            } catch (Throwable t) {
                Log.err("[PlotBundle] 列出目录失败", t);
            }
            plotFile = childPath(modRoot, candidates[0]);
        }
        return load(plotFile, decodeKey, parent);
    }

    /**
     * 按路径段逐级取子文件。
     * 【重要】不能直接 root.child("a/b/c")：jar/zip 安装时 mod 根目录是 Arc 的 ZipFi，
     * 其 child() 只按单级文件名匹配直接子项，传多级路径会返回一个 exists() 恒为 false 的假 Fi，
     * 这就是 plot.properties 明明在 jar 里却永远读不到的原因。
     */
    private static Fi childPath(Fi root, String path) {
        Fi cur = root;
        for (String seg : path.split("/")) {
            if (seg.isEmpty())
                continue;
            cur = cur.child(seg);
        }
        return cur;
    }

    public static Seq<PlotBundle> loadAllMods(Fi modsDirectory) {
        return loadAllMods(modsDirectory, DEFAULT_KEY);
    }

    public static Seq<PlotBundle> loadAllMods(Fi modsDirectory, int decodeKey) {
        Seq<PlotBundle> list = new Seq<>();
        if (!modsDirectory.exists() || !modsDirectory.isDirectory())
            return list;

        for (Fi mod : modsDirectory.list()) {
            if (!mod.isDirectory())
                continue;
            Fi plot = childPath(mod, "plot/plot.properties");
            if (!plot.exists())
                plot = childPath(mod, "assets/plot/plot.properties");
            if (plot.exists()) {
                list.add(load(plot, decodeKey, null));
            }
        }
        return list;
    }

    private void load(Reader reader) throws IOException {
        properties = new ObjectMap<>();
        BufferedReader br = new BufferedReader(reader);
        StringBuilder multiLine = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            if (line.endsWith("\\") && !line.trim().startsWith("#")) {
                multiLine.append(line, 0, line.length() - 1).append("\n");
                continue;
            }

            if (multiLine.length() > 0) {
                multiLine.append(line);
                line = multiLine.toString();
                multiLine.setLength(0);
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
                continue;

            int eq = trimmed.indexOf('=');
            if (eq <= 0)
                continue;

            String key = trimmed.substring(0, eq).trim();
            String rawValue = trimmed.substring(eq + 1).trim();

            rawValue = rawValue.replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\");

            String decodedValue = PlotObfuscator.deobfuscate(rawValue, decodeKey);
            properties.put(key, decodedValue);
        }
    }

    public void reload() {
        if (sourceFile != null && sourceFile.exists() && !sourceFile.isDirectory()) {
            try (Reader reader = sourceFile.reader(DEFAULT_ENCODING)) {
                load(reader);
            } catch (Exception e) {
                Log.err("[PlotBundle] Reload failed: " + sourceFile.absolutePath(), e);
            }
        }
    }

    public String get(String key) {
        String result = properties.get(key);
        if (result == null) {
            if (parent != null)
                result = parent.get(key);
            if (result == null)
                return "???" + key + "???";
        }
        return result;
    }

    public String get(String key, String def) {
        String result = properties.get(key);
        if (result == null && parent != null)
            result = parent.get(key);
        return result != null ? result : def;
    }

    public String getOrNull(String key) {
        String result = properties.get(key);
        if (result == null && parent != null)
            result = parent.getOrNull(key);
        return result;
    }

    public String getNotNull(String key) {
        String s = getOrNull(key);
        if (s == null)
            throw new MissingResourceException("No plot key: " + key, PlotBundle.class.getName(), key);
        return s;
    }

    public boolean has(String key) {
        if (properties.containsKey(key))
            return true;
        return parent != null && parent.has(key);
    }

    public String format(String key, Object... args) {
        String pattern = get(key);
        for (int i = 0; i < args.length; i++) {
            pattern = pattern.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return pattern;
    }

    public String format(String key, ObjectMap<String, ?> args) {
        String pattern = get(key);
        for (ObjectMap.Entry<String, ?> entry : args) {
            pattern = pattern.replace("{" + entry.key + "}", String.valueOf(entry.value));
        }
        return pattern;
    }

    /** 优先从 plot.properties 读取；key 缺失时回退到游戏语言包 Core.bundle，避免界面显示 ???key???。 */
    public String getOrBundle(String key) {
        if (has(key))
            return get(key);
        return arc.Core.bundle.get(key);
    }

    /** {@link #getOrBundle} 的带参数版本。 */
    public String formatOrBundle(String key, Object... args) {
        if (has(key))
            return format(key, args);
        return arc.Core.bundle.format(key, args);
    }

    public String color(String key, String hexColor) {
        return "[[#" + hexColor + "]" + get(key) + "[]]";
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public float getFloat(String key, float def) {
        try {
            return Float.parseFloat(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        String s = get(key, String.valueOf(def));
        return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes");
    }

    public Seq<String> keysByPrefix(String prefix) {
        Seq<String> out = new Seq<>();
        for (String k : properties.keys()) {
            if (k.startsWith(prefix))
                out.add(k);
        }
        if (parent != null) {
            for (String k : parent.keysByPrefix(prefix)) {
                if (!out.contains(k))
                    out.add(k);
            }
        }
        return out;
    }

    public Seq<String> valuesByPrefix(String prefix) {
        Seq<String> keys = keysByPrefix(prefix);
        keys.sort(String::compareTo);
        Seq<String> out = new Seq<>(keys.size);
        for (String k : keys)
            out.add(get(k));
        return out;
    }

    public Seq<String> getSequence(String prefix) {
        Seq<String> keys = keysByPrefix(prefix + ".");
        keys.sort(new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                try {
                    int na = Integer.parseInt(a.substring(prefix.length() + 1));
                    int nb = Integer.parseInt(b.substring(prefix.length() + 1));
                    return Integer.compare(na, nb);
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            }
        });
        return keys.map(this::get);
    }

    public String getRandom(String prefix) {
        Seq<String> keys = keysByPrefix(prefix + ".");
        if (keys.isEmpty())
            return "???" + prefix + "???";
        return get(keys.random());
    }

    public String getRandom(String prefix, Rand rand) {
        Seq<String> keys = keysByPrefix(prefix + ".");
        if (keys.isEmpty())
            return "???" + prefix + "???";
        return get(keys.get(rand.nextInt(keys.size)));
    }

    public void each(Cons2<String, String> cons) {
        properties.each(cons);
    }

    public Iterable<String> keys() {
        return properties.keys();
    }

    public ObjectMap<String, String> getProperties() {
        return properties;
    }

    public int size() {
        return properties.size;
    }

    public String getName() {
        return name;
    }

    public PlotBundle getParent() {
        return parent;
    }

    public void setParent(PlotBundle parent) {
        this.parent = parent;
    }

    public Fi getSourceFile() {
        return sourceFile;
    }

    @Override
    public String toString() {
        return "PlotBundle[name=" + name + ", size=" + properties.size + ", parent=" + (parent != null) + "]";
    }
}
