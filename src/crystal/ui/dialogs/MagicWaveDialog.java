package crystal.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.TextField.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import crystal.type.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

/**
 * 修为波次统计窗口
 * 显示每一波中所有实现 MagicUnitInterface 的单位的修为总量，
 * 以及从第1波到当前波的累计修为总量。
 * 
 * 使用方式:
 * new MagicWaveDialog().show();
 */
public class MagicWaveDialog extends BaseDialog {
  /** 当前选中的波次（0-based，显示时+1） */
  private int waveIndex = 0;
  /** 最大波次 */
  private static final int maxWave = 200;

  private Label currentWaveLabel;
  private Label currentWaveXiuWeiLabel;
  private Label totalXiuWeiLabel;
  private Table detailsTable;

  public MagicWaveDialog() {
    super("@magicwave.title");

    shown(() -> {
      waveIndex = Math.max(0, state.wave - 1);
      setup();
    });

    addCloseButton();
  }

  void setup() {
    cont.clear();
    cont.defaults().pad(4f);

    // ==================== 顶部波次选择区域 ====================
    cont.table(Tex.button, t -> {
      t.defaults().pad(6f);

      // 减少波次按钮
      t.button(Icon.left, Styles.emptyi, () -> {
        if (waveIndex > 0) {
          waveIndex--;
          updateDisplay();
        }
      }).size(50f).disabled(b -> waveIndex <= 0);

      // 当前波次显示
      currentWaveLabel = t.add("").style(Styles.outlineLabel).padLeft(20f).padRight(20f).get();
      currentWaveLabel.setFontScale(1.5f);

      // 增加波次按钮
      t.button(Icon.right, Styles.emptyi, () -> {
        if (waveIndex < maxWave - 1) {
          waveIndex++;
          updateDisplay();
        }
      }).size(50f).disabled(b -> waveIndex >= maxWave - 1);

      t.row();

      // 直接输入波次
      t.add("@magicwave.jump").padTop(8f).padRight(8f);
      t.field("", TextFieldFilter.digitsOnly, text -> {
        if (Strings.canParsePositiveInt(text)) {
          int target = Strings.parseInt(text) - 1;
          waveIndex = Mathf.clamp(target, 0, maxWave - 1);
          updateDisplay();
        }
      }).width(80f).padTop(8f).get().setMessageText("1");
    }).width(420f).padBottom(16f).row();

    // 分隔线
    cont.image().color(Pal.accent).height(3f).growX().padBottom(16f).row();

    // ==================== 修为统计区域 ====================
    cont.table(Tex.pane, t -> {
      t.defaults().pad(8f).left();

      // ---- 当前波次修为 ----
      t.add("@magicwave.current").color(Pal.accent).fontScale(1.1f).row();
      currentWaveXiuWeiLabel = t.add("0").style(Styles.outlineLabel).padBottom(16f).get();
      currentWaveXiuWeiLabel.setFontScale(1.4f);
      t.row();

      // ---- 累计修为 ----
      t.add("@magicwave.total").color(Pal.accent).fontScale(1.1f).row();
      totalXiuWeiLabel = t.add("0").style(Styles.outlineLabel).padBottom(16f).get();
      totalXiuWeiLabel.setFontScale(1.4f);
      t.row();

      // ---- 详细列表 ----
      t.add("@magicwave.details").color(Pal.lightishGray).padTop(8f).row();

      detailsTable = new Table();
      detailsTable.defaults().pad(4f).left();
      t.add(detailsTable).growX().padTop(8f);
    }).width(420f).growY();

    updateDisplay();
  }

  void updateDisplay() {
    currentWaveLabel.setText(Core.bundle.format("magicwave.wave", waveIndex + 1));

    // 获取当前扇区的 rules
    Rules rules = state.rules;
    if (rules == null || rules.spawns == null) {
      currentWaveXiuWeiLabel.setText("0");
      totalXiuWeiLabel.setText("0");
      clearDetails();
      return;
    }

    Seq<SpawnGroup> spawns = rules.spawns;
    if (spawns == null || spawns.isEmpty()) {
      currentWaveXiuWeiLabel.setText("0");
      totalXiuWeiLabel.setText("0");
      clearDetails();
      return;
    }

    // 计算当前波次的修为总量
    float currentWaveXiuWei = calculateWaveXiuWei(spawns, waveIndex);

    // 计算从第1波到当前波次的累计修为
    float totalXiuWei = 0f;
    for (int i = 0; i <= waveIndex; i++) {
      totalXiuWei += calculateWaveXiuWei(spawns, i);
    }

    currentWaveXiuWeiLabel.setText(Strings.autoFixed(currentWaveXiuWei, 2));
    totalXiuWeiLabel.setText(Strings.autoFixed(totalXiuWei, 2));

    // 更新详细列表
    updateDetails(spawns, waveIndex);
  }

  /**
   * 计算指定波次中所有实现 MagicUnitInterface 的单位的修为总量
   * 
   * @param spawns 生成组列表
   * @param wave   波次（0-based）
   * @return 修为总量
   */
  float calculateWaveXiuWei(Seq<SpawnGroup> spawns, int wave) {
    float total = 0f;

    for (SpawnGroup group : spawns) {
      int spawned = group.getSpawned(wave);
      if (spawned <= 0)
        continue;

      UnitType type = group.type;
      // 检查 UnitType 是否实现了 MagicUnitInterface
      if (type instanceof MagicUnitInterface magic) {
        total += magic.xiuWeiAmount() * spawned;
      }
    }

    return total;
  }

  /**
   * 获取指定波次中所有实现 MagicUnitInterface 的单位信息
   */
  Seq<MagicUnitInfo> getMagicUnitsForWave(Seq<SpawnGroup> spawns, int wave) {
    Seq<MagicUnitInfo> result = new Seq<>();

    for (SpawnGroup group : spawns) {
      int spawned = group.getSpawned(wave);
      if (spawned <= 0)
        continue;

      UnitType type = group.type;
      if (type instanceof MagicUnitInterface magic) {
        result.add(new MagicUnitInfo(type, magic.xiuWeiAmount(), spawned));
      }
    }

    // 按修为值降序排序
    result.sort(info -> -info.xiuWeiAmount);

    return result;
  }

  void clearDetails() {
    if (detailsTable != null) {
      detailsTable.clear();
      detailsTable.add("@magicwave.nomagic").color(Color.gray);
    }
  }

  void updateDetails(Seq<SpawnGroup> spawns, int wave) {
    if (detailsTable == null)
      return;

    detailsTable.clear();
    detailsTable.defaults().pad(4f).left();

    Seq<MagicUnitInfo> units = getMagicUnitsForWave(spawns, wave);

    if (units.isEmpty()) {
      detailsTable.add("@magicwave.nomagic").color(Color.gray).row();
      return;
    }

    // 表头
    detailsTable.table(header -> {
      header.defaults().pad(4f);
      header.add("@magicwave.unit").width(150f).color(Pal.lightishGray);
      header.add("@magicwave.xiuwei").width(80f).color(Pal.lightishGray);
      header.add("@magicwave.count").width(60f).color(Pal.lightishGray);
      header.add("@magicwave.subtotal").width(90f).color(Pal.lightishGray);
    }).growX().row();

    detailsTable.image().color(Color.darkGray).height(1f).growX().padTop(2f).padBottom(4f).row();

    for (MagicUnitInfo info : units) {
      detailsTable.table(row -> {
        row.defaults().pad(4f);

        // 单位图标和名称
        row.image(info.type.uiIcon).size(32f).scaling(Scaling.fit).padRight(6f);
        row.add(info.type.localizedName).width(110f).left().ellipsis(true);

        // 单个修为值
        row.add(Strings.autoFixed(info.xiuWeiAmount, 2)).width(80f);

        // 数量
        row.add("x" + info.count).width(60f);

        // 小计
        float subtotal = info.xiuWeiAmount * info.count;
        row.add(Strings.autoFixed(subtotal, 2)).width(90f).color(Pal.accent);
      }).growX().row();
    }

    detailsTable.image().color(Color.darkGray).height(1f).growX().padTop(4f).padBottom(4f).row();

    // 当前波次总计
    float waveTotal = calculateWaveXiuWei(spawns, wave);
    detailsTable.table(total -> {
      total.defaults().pad(4f);
      total.add("@magicwave.wavetotal").padRight(20f);
      total.add(Strings.autoFixed(waveTotal, 2)).color(Pal.accent).fontScale(1.15f);
    }).padTop(8f).row();
  }

  /** 修为单位信息内部类 */
  static class MagicUnitInfo {
    UnitType type;
    float xiuWeiAmount;
    int count;

    MagicUnitInfo(UnitType type, float xiuWeiAmount, int count) {
      this.type = type;
      this.xiuWeiAmount = xiuWeiAmount;
      this.count = count;
    }
  }
}
