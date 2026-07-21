package crystal.ui.gal;

import arc.scene.style.Drawable;
import arc.struct.ObjectMap;
import arc.util.Log;
import crystal.CVars;

/**
 * 可注册角色。每个角色可拥有多组表情（Expression -> Drawable）。
 */
public class Character {
    /** 角色唯一 ID。 */
    public String characterId;
    /** 显示名。 */
    public String name;

    private final ObjectMap<Expression, Drawable> sprites = new ObjectMap<>();

    public Character(String characterId, String name) {
        this.characterId = characterId;
        this.name = name;
        init();
    }

    public void init() {
    }

    public String getName() {
        return this.name;
    }

    /** 玩家名，动态从存档读取。 */
    public String getPlayerName() {
        return CVars.playerName;
    }

    /** 注册表情。 */
    public Character addExpression(Expression exp, Drawable sprite) {
        if (sprite == null) {
            Log.err(name + " " + exp + " 表情没有对应图片");
        }
        sprites.put(exp, sprite);
        return this;
    }

    /** 获取表情；未注册时回退到 normal。 */
    public Drawable getSprite(Expression exp) {
        Drawable sprite = sprites.get(exp);
        if (sprite == null) {
            sprite = sprites.get(Expression.normal);
            Log.err(name + " " + exp + " 表情没有注册");
        }
        return sprite;
    }

    /** 判断是否存在指定表情。 */
    public boolean hasExpression(Expression exp) {
        return sprites.containsKey(exp);
    }
}
