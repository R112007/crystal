package crystal.ui.gal;

import arc.scene.style.Drawable;
import arc.struct.ObjectMap;
import arc.util.Log;
import crystal.CVars;
import crystal.util.DLog;

public class Character {
  public String characterId;
  public String name;
  private ObjectMap<Expression, Drawable> sprites = new ObjectMap<>();

  public Character(String characterId, String name) {
    this.characterId = characterId;
    this.name = name;
  }

  // Character.java
  public String getName() {
    return this.name;
  }

  public String getPlayerName() {
    return CVars.playerName;
  }

  // 注册表情（枚举版）
  public Character addExpression(Expression exp, Drawable sprite) {
    if (sprite == null) {
      Log.err(name + exp + "表情没有对应图片");
    }
    sprites.put(exp, sprite);
    return this;
  }

  // 获取表情（自动补全、不会错）
  public Drawable getSprite(Expression exp) {
    Drawable sprite = sprites.get(exp);
    if (sprite == null) {
      sprite = sprites.get(Expression.normal);
      Log.err(name + exp + "表情没有注册");
    }
    return sprite;
  }
}
