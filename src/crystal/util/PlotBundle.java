package crystal.util;

import arc.files.Fi;
import arc.func.Cons2;
import arc.math.Rand;
import arc.struct.ObjectMap;
import arc.struct.Seq;

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
        if (file.exists()) {
            try (Reader reader = file.reader(DEFAULT_ENCODING)) {
                bundle.load(reader);
            } catch (IOException e) {
                System.err.println("[PlotBundle] Failed to load: " + file.absolutePath());
            }
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
        Fi plotFile = modRoot.child("plot/plot.properties");
        if (!plotFile.exists()) {
            plotFile = modRoot.child("assets/plot/plot.properties");
        }
        return load(plotFile, decodeKey, parent);
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
            Fi plot = mod.child("plot/plot.properties");
            if (!plot.exists())
                plot = mod.child("assets/plot/plot.properties");
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
        if (sourceFile != null && sourceFile.exists()) {
            try (Reader reader = sourceFile.reader(DEFAULT_ENCODING)) {
                load(reader);
            } catch (IOException e) {
                System.err.println("[PlotBundle] Reload failed: " + sourceFile.absolutePath());
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
