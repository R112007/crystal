package crystal.type;

import arc.graphics.g2d.TextureRegion;

/**
 * 模组制作组成员。
 */
public class Contributor {
    public String name;
    public String relative;
    public boolean root;
    public String description;
    public TextureRegion icon;
    public String title;

    public Contributor(String name, String relative, boolean root, String description, TextureRegion icon,
            String title) {
        this.name = name;
        this.relative = relative;
        this.root = root;
        this.description = description;
        this.icon = icon;
        this.title = title;
    }

    public Contributor(String name, String relative, boolean root, String description, TextureRegion icon) {
        this(name, relative, root, description, icon, null);
    }
}
