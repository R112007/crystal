# Crystal 逻辑验证器

当前覆盖**单人**游戏里的卫星物品后台模拟、GAL 剧情模块/分支/队列，以及修为系统：
境界/修为档位/灵力、功法门槛、渡劫、灵力恢复、击杀加修为、存档往返、旧档兼容。
只跑 headless（没有画面，几秒出结果），不跑联机。

## 跑

```powershell
.\gradlew --offline dex                                          # 编模组 → build\libs\Crystal.jar
.\verify\run-headless.ps1                                        # 启动器自检（游戏/模组/修为系统通不通）
.\verify\run-headless.ps1 verify\build\data crystal.dbg.XXXTest   # 跑某个场景类
.\verify\run-headless.ps1 verify\build\data crystal.dbg.XXXTest write   # 场景参数照传

# 三个完整回归场景
.\verify\run-headless.ps1 verify\build\data crystal.dbg.SatelliteItemSimulationTest
.\verify\run-headless.ps1 verify\build\data crystal.dbg.GalSystemTest
.\verify\run-headless.ps1 verify\build\data crystal.dbg.CultivationSystemTest
```

- 游戏 jar 默认用本机 `C:\Users\RHN\Downloads\Mindustry.jar`（官方桌面包，不带 `arc.backend.headless`），
  headless 后端从 `C:\Users\RHN\Downloads\server-release.jar` 补 —— 两个包一起上才起得来。
  换 jar：`-GameJar` / `-ServerJar` / `-ModJar`，或设同名环境变量。
- 数据目录默认 `verify\build\data`，会被就地改造成"游戏数据目录"：`mods\Crystal.jar` = 新编的包 + 注入的场景类。
- 场景类放 `verify\tests\**\*.java`。它们被**注入 mod jar**、由 crystal 自己的 classloader 加载 ——
  这样测试和模组共用同一份 `CultivationState` 静态状态，不会出现"两份类、两套状态"的假结果。
- 入口是 `verify\boot\crystalverify\Boot.java`（跑在系统 classpath 上）。crystal 是纯客户端模组，headless 里它自己的
  `init()` 跑不完（要图集/字体/图标），所以启动器装了最小替身（空图集、Unsafe 分配的 UI 空壳、Icon 替身、netServer/logic）。
  面板/弹窗这些"要画面"的东西 headless 不验。
- 判定标准是数值/集合，不看画面；场景里打出 `FAIL` 就是真问题（会打印期望/实际），退出码 0/1。

## 约定

- 临时探针、调试打印用完就删；`verify\build\` 是产物，不提交。
- 跑挂过会在数据目录的 settings 里留下 `mod-crystal-failed`，之后模组根本不会被加载（测试会"假过"）；
  运行器见到退出码 3 会自动把 settings 挪走重跑一次。

## 历史发现（160.4 时期验出来的两个模组缺陷，已修）

- `JingJie.jiuLu`（旧路数组）顺序错：`shenjun` 排在 `shenwang` 前面 → 旧路"下一境界"从真神直接跳到神君。
- `PlayerXiuWeiSystem.clear()`（debug 的重置修为）会把占位功法 `none` 一起 `lock()`，之后境界永远升不上去。

## 本轮发现（已修）

- 卫星后台模拟在没有区块统计记录时提前返回，导致注入台永久停摆；同时不足 30 秒的经过时间会丢失。
- GAL 回放副本中的分支选项丢失 `onSelect` 回调，与“回放保留回调”的行为约定不一致。
