package crystal.ui.gal;

import arc.graphics.g2d.TextureRegion;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.ObjectMap;
import mindustry.Vars;

public class DialogCharacter {
    /** 角色唯一ID */
    public final String id;
    /** 角色显示名称 */
    public final String name;
    /** 表情映射：key=表情名（如normal/angry/smile），value=立绘Drawable */
    public final ObjectMap<String, Drawable> expressions = new ObjectMap<>();
    /** 角色默认表情 */
    public String defaultExpression = "normal";

    public DialogCharacter(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** 注册角色表情立绘 */
    public DialogCharacter addExpression(String expressionName, TextureRegion region) {
        expressions.put(expressionName, new TextureRegionDrawable(region));
        return this;
    }

    /** 注册角色表情立绘（直接用Atlas中的Drawable） */
    public DialogCharacter addExpression(String expressionName, Drawable drawable) {
        expressions.put(expressionName, drawable);
        return this;
    }

    /** 获取表情对应的立绘，不存在则返回默认表情 */
    public Drawable getExpression(String expressionName) {
        return expressions.get(expressionName, expressions.get(defaultExpression, Vars.ui.getIcon("error")));
    }
}
