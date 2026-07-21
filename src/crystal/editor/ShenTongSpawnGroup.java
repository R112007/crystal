package crystal.editor;

/**
 * 占位类：旧版/旧地图保存的类名为 crystal.editor.ShenTongSpawnGroup。
 * 让它继承新的 MagicSpawnGroup，确保旧地图读取时 Class.forName 不会崩溃。
 */
public class ShenTongSpawnGroup extends MagicSpawnGroup {
  // 所有逻辑已由 MagicSpawnGroup 实现
  public ShenTongSpawnGroup() {
  }
}
