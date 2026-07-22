package crystal.ui.gal;

import arc.Core;
import arc.func.Cons;
import arc.scene.style.Drawable;
import arc.struct.Seq;
import arc.util.Log;
import crystal.CVars;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单句对话节点。
 * 支持：
 * <ul>
 * <li>左右双立绘</li>
 * <li>文本中嵌入图片/表情包（如 {@code [img:crystal-yi-shy]}）</li>
 * <li>实例化动作（{@link SpriteAction}）与兼容的旧 Runnable 动作</li>
 * <li>选项与完成回调</li>
 * <li>回溯时生成“无回调”的副本</li>
 * </ul>
 */
public class DialogueLine {
    public String nodeId;
    public String moduleId;

    /** 左侧角色与表情。 */
    public Character leftCharacter;
    public Expression leftExpression = Expression.normal;
    /** 右侧角色与表情。 */
    public Character rightCharacter;
    public Expression rightExpression = Expression.normal;
    /** 当前说话侧。 */
    public Side activeSide = Side.left;

    /** 解析后的显示角色名。 */
    public String characterName;
    /** 解析后的立绘。 */
    public Drawable leftSprite;
    public Drawable rightSprite;

    /** 对话文本（已解析 bundle、已移除图片标签）。 */
    public String content;
    /** 原始 key，用于刷新 bundle。 */
    public String key;

    /** 内嵌图片/表情包。 */
    public Seq<Drawable> inlineImages = new Seq<>();

    public DialogueOption[] options;

    /** 旧版 Runnable 动作（兼容保留）。 */
    public Runnable spriteEnterAction;
    public Runnable spriteExitAction;
    public Runnable spriteActionLegacy;
    public Runnable onComplete;

    /** 播放前回调：在当前对话文本开始打字/播放前执行。 */
    public Runnable beforePlay;

    /** 新版实例化动作。 */
    public SpriteAction spriteAction;

    private static final Pattern IMG_TAG = Pattern.compile("\\[img:([^\\]]+)\\]");

    /** 仅左侧角色说话。 */
    public DialogueLine(Character character, Expression expression, String content) {
        this(character, expression, null, null, Side.left, content);
    }

    /** 仅左侧角色说话，带选项。 */
    public DialogueLine(Character character, Expression expression, String content, DialogueOption... options) {
        this(character, expression, null, null, Side.left, content, options);
    }

    /** 左右双角色，指定当前说话侧。 */
    public DialogueLine(Character leftCharacter, Expression leftExpression,
            Character rightCharacter, Expression rightExpression,
            Side activeSide, String content) {
        this(leftCharacter, leftExpression, rightCharacter, rightExpression, activeSide, content,
                new DialogueOption[0]);
    }

    /** 左右双角色，指定当前说话侧，带选项。 */
    public DialogueLine(Character leftCharacter, Expression leftExpression,
            Character rightCharacter, Expression rightExpression,
            Side activeSide, String content, DialogueOption... options) {
        this.leftCharacter = leftCharacter;
        this.leftExpression = leftExpression;
        this.rightCharacter = rightCharacter;
        this.rightExpression = rightExpression;
        this.activeSide = activeSide == null ? Side.left : activeSide;
        this.key = content;
        this.options = options == null ? new DialogueOption[0] : options;
        rebuildDerived();
        init();
    }

    /** 旧版兼容：单独立绘。 */
    public DialogueLine(String characterName, Drawable characterSprite, String content) {
        this.characterName = characterName;
        this.leftSprite = characterSprite;
        this.key = content;
        this.options = new DialogueOption[0];
        rebuildDerived();
    }

    /** 旧版兼容：单独立绘，带选项。 */
    public DialogueLine(String characterName, Drawable characterSprite, String content, DialogueOption... options) {
        this.characterName = characterName;
        this.leftSprite = characterSprite;
        this.key = content;
        this.options = options == null ? new DialogueOption[0] : options;
        rebuildDerived();
    }

    /** 默认构造，用于反序列化/拷贝。 */
    public DialogueLine() {
    }

    /** 根据 key 刷新文本、角色名、立绘、内嵌图片。 */
    public void refreshContent() {
        rebuildDerived();
    }

    private void rebuildDerived() {
        this.content = parseText(key);
        this.inlineImages = parseInlineImages(key);

        // 左侧立绘
        if (leftCharacter != null) {
            this.leftSprite = leftCharacter.getSprite(leftExpression);
            if (activeSide == Side.left) {
                this.characterName = leftCharacter.name;
            }
        }
        // 右侧立绘
        if (rightCharacter != null) {
            this.rightSprite = rightCharacter.getSprite(rightExpression);
            if (activeSide == Side.right) {
                this.characterName = rightCharacter.name;
            }
        }
        // 两侧都有但左侧优先作为说话者兜底
        if (activeSide == Side.both && leftCharacter != null) {
            this.characterName = leftCharacter.name;
        }

        // 玩家名动态替换
        if ("player".equals(this.characterName) || this.characterName == null) {
            this.characterName = CVars.playerName;
        }
    }

    public void init() {
    }

    /** 解析文本：处理 bundle 与图片标签移除。 */
    public static String parseText(String str) {
        if (str == null)
            return "";
        String parsed = str;
        if (parsed.startsWith("@") && Core.bundle.has(parsed.substring(1))) {
            parsed = Core.bundle.get(parsed.substring(1));
        }
        return IMG_TAG.matcher(parsed).replaceAll("").trim();
    }

    /** 解析内嵌图片标签为 Drawable 列表。 */
    public static Seq<Drawable> parseInlineImages(String str) {
        Seq<Drawable> result = new Seq<>();
        if (str == null)
            return result;
        Matcher m = IMG_TAG.matcher(str);
        while (m.find()) {
            String region = m.group(1);
            try {
                Drawable d = Core.atlas.getDrawable(region);
                if (d != null) {
                    result.add(d);
                } else {
                    Log.err("内嵌图片不存在: " + region);
                }
            } catch (Exception e) {
                Log.err("加载内嵌图片失败: " + region, e);
            }
        }
        return result;
    }

    /** 直接添加内嵌图片。 */
    public DialogueLine withInlineImage(Drawable image) {
        if (image != null) {
            inlineImages.add(image);
        }
        return this;
    }

    /** 通过图集区域名添加内嵌图片。 */
    public DialogueLine withInlineImage(String regionName) {
        try {
            Drawable d = Core.atlas.getDrawable(regionName);
            if (d != null)
                inlineImages.add(d);
        } catch (Exception ignored) {
        }
        return this;
    }

    public DialogueLine withEnterAction(Runnable action) {
        this.spriteEnterAction = action;
        return this;
    }

    public DialogueLine withExitAction(Runnable action) {
        this.spriteExitAction = action;
        return this;
    }

    /** 旧版动作。 */
    public DialogueLine withSpriteAction(Runnable action) {
        this.spriteActionLegacy = action;
        return this;
    }

    /** 新版实例化动作。 */
    public DialogueLine withAction(SpriteAction action) {
        this.spriteAction = action;
        return this;
    }

    public DialogueLine onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    public DialogueLine withBeforePlay(Runnable action) {
        this.beforePlay = action;
        return this;
    }

    public DialogueLine beforePlay(Runnable action) {
        return withBeforePlay(action);
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

    public DialogueLine action(SpriteAction action) {
        return withAction(action);
    }

    public DialogueLine complete(Runnable callback) {
        return onComplete(callback);
    }

    /**
     * 创建用于剧情回溯的副本：文本、角色、立绘、表情、选项文案均保留，
     * 但所有回调与动作不拷贝，避免重复触发游戏功能。
     */
    public DialogueLine copyForHistory() {
        DialogueLine copy = new DialogueLine();
        copy.nodeId = this.nodeId;
        copy.moduleId = this.moduleId;
        copy.leftCharacter = this.leftCharacter;
        copy.leftExpression = this.leftExpression;
        copy.rightCharacter = this.rightCharacter;
        copy.rightExpression = this.rightExpression;
        copy.activeSide = this.activeSide;
        copy.characterName = this.characterName;
        copy.leftSprite = this.leftSprite;
        copy.rightSprite = this.rightSprite;
        copy.content = this.content;
        copy.key = this.key;
        copy.inlineImages = this.inlineImages;
        // 选项文案保留；分支引用保留以便回溯/回放时跳转到分支剧情，回调置空避免重复触发游戏功能
        if (this.options != null) {
            copy.options = new DialogueOption[this.options.length];
            for (int i = 0; i < this.options.length; i++) {
                DialogueOption o = this.options[i];
                if (o.branch != null) {
                    copy.options[i] = new DialogueOption(o.optionText, o.branch);
                } else {
                    copy.options[i] = new DialogueOption(o.optionText, opt -> {
                    });
                }
            }
        }
        // 回调与动作不拷贝
        return copy;
    }

    /**
     * 创建用于模块回放副本的副本：文本、角色、立绘、表情、选项、回调与动作均保留。
     * 回调引用被共享（闭包上下文不变），但 DialogueLine 实例是新的，避免影响原模块进度。
     */
    public DialogueLine copyForReplay() {
        DialogueLine copy = copyForHistory();
        copy.spriteEnterAction = this.spriteEnterAction;
        copy.spriteExitAction = this.spriteExitAction;
        copy.spriteActionLegacy = this.spriteActionLegacy;
        copy.onComplete = this.onComplete;
        copy.beforePlay = this.beforePlay;
        copy.spriteAction = this.spriteAction;
        if (this.options != null) {
            copy.options = new DialogueOption[this.options.length];
            for (int i = 0; i < this.options.length; i++) {
                DialogueOption o = this.options[i];
                if (o.branch != null) {
                    copy.options[i] = new DialogueOption(o.optionText, o.branch);
                } else {
                    copy.options[i] = new DialogueOption(o.optionText, o.onSelect);
                }
            }
        }
        return copy;
    }

    /** 选项定义。 */
    public static class DialogueOption {
        public String optionText;
        public Cons<DialogueOption> onSelect;
        /**
         * 选项直接关联的分支。
         * 如果指定，在模块副本回放时可以直接播放该分支，而不依赖 onSelect 闭包。
         */
        public Branch branch;

        public DialogueOption(String optionText, Cons<DialogueOption> onSelect) {
            this.optionText = parseText(optionText);
            this.onSelect = onSelect;
        }

        /** 指定分支的选项构造器，用于支持副本回放时选择未选分支。 */
        public DialogueOption(String optionText, Branch branch) {
            this(optionText, branch, null);
        }

        /**
         * 同时指定分支和点击回调的构造器。
         * 正常播放时走 onSelect，副本回放时走 branch。
         */
        public DialogueOption(String optionText, Branch branch, Cons<DialogueOption> onSelect) {
            this.optionText = parseText(optionText);
            this.branch = branch;
            this.onSelect = onSelect;
        }
    }
}
