package crystal.ui.gal;

import arc.scene.style.Drawable;
import crystal.CVars;
import arc.Core;
import arc.func.Cons;

public class DialogueLine {
  public String nodeId;
  public String moduleId;
  public Character character;
  public String characterName;
  public Expression expression;
  public Drawable characterSprite;
  public String content;
  public String key;
  public DialogueOption[] options;
  public Runnable spriteEnterAction;
  public Runnable spriteExitAction;
  public Runnable spriteAction;
  public Runnable onComplete;

  public DialogueLine(Character character, Expression expression, String content) {
    this.character = character;
    this.expression = expression;
    this.characterName = character.name;
    this.characterSprite = character.getSprite(expression);
    this.key = content;
    this.content = parseContent(content);
  }

  // 带选项
  public DialogueLine(Character character, Expression expression, String content, DialogueOption... options) {
    this.character = character;
    this.expression = expression;
    this.characterName = character.name;
    this.characterSprite = character.getSprite(expression);
    this.key = content;
    this.content = parseContent(content);
    this.options = options;
  }

  // 旧兼容保留
  public DialogueLine(String characterName, Drawable characterSprite, String content) {
    this.characterName = characterName;
    this.characterSprite = characterSprite;
    this.key = content;
    this.content = parseContent(content);
  }

  public DialogueLine(String characterName, Drawable characterSprite, String content, DialogueOption... options) {
    this.characterName = characterName;
    this.characterSprite = characterSprite;
    this.content = parseContent(content);
    this.key = content;
    this.options = options;
  }

  public void refreshContent() {
    this.content = parseContent(key);
    if ("player".equals(this.characterName) || this.characterName == null) {
      this.characterName = CVars.playerName;
    }
  }

  public static String parseContent(String str) {
    if (str == null)
      return "";
    if (str.startsWith("@") && Core.bundle.has(str.substring(1))) {
      return Core.bundle.get(str.substring(1));
    }
    return str;
  }

  public DialogueLine withEnterAction(Runnable action) {
    this.spriteEnterAction = action;
    return this;
  }

  public DialogueLine withExitAction(Runnable action) {
    this.spriteExitAction = action;
    return this;
  }

  public DialogueLine withSpriteAction(Runnable action) {
    this.spriteAction = action;
    return this;
  }

  public DialogueLine onComplete(Runnable callback) {
    this.onComplete = callback;
    return this;
  }

  public DialogueLine enter(Runnable action) {
    return withEnterAction(action);
  }

  public DialogueLine exit(Runnable action) {
    return withExitAction(action);
  }

  public DialogueLine action(Runnable action) {
    return withSpriteAction(action);
  }

  public DialogueLine complete(Runnable callback) {
    return onComplete(callback);
  }

  public static class DialogueOption {
    public String optionText;
    public Cons<DialogueOption> onSelect;

    public DialogueOption(String optionText, Cons<DialogueOption> onSelect) {
      this.optionText = parseContent(optionText);
      this.onSelect = onSelect;
    }
  }
}
