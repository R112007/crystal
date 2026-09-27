# Crystal 修为系统验证器

测的是**单人**游戏里的修为系统：境界/修为档位/灵力、功法门槛、渡劫、面板 UI、存档。
不跑联机（没有服务端/客户端同步这条线）。

三层，从便宜到贵：

| 层 | 命令 | 用时 | 验什么 |
|---|---|---|---|
| 编译 | `./gradlew --offline dex` | ~5s | 语法/编译（产出 `build/libs/Crystal.jar`，含 `classes.dex`） |
| headless 逻辑 | `verify/run-headless.sh <数据目录>` | ~5s | 状态机：境界重算、功法门槛、渡劫门槛/成功/失败、灵力恢复、击杀加修为、存档往返、旧档兼容 |
| 真客户端 | `verify/run-client.sh <数据目录>` | ~1min | 面板绘制、HUD 按钮、渡劫确认弹窗（真点击）、toast、法宝/神武槽位 |

## headless

```bash
./gradlew --offline dex
verify/run-headless.sh /tmp/mp_xw_main/data                    # 主电池（数据目录自动创建）
verify/run-headless.sh /tmp/mp_xw_p/data write                 # 跨进程存档：写
verify/run-headless.sh /tmp/mp_xw_p/data read                  # 跨进程存档：读（新进程）
verify/run-headless.sh /tmp/mp_xw_p/data legacy                # 旧档兼容（ordinal / 旧 key / 坏数据）
verify/run-headless.sh /tmp/mp_xw_client/data crystal.dbg.XiuWeiHeadlessTest seed   # 只写 locale=zh_CN 的干净 settings
```

- 游戏 jar：官方 160.4 桌面包 `/tmp/mind1604.jar`（模组照这个版本编的）；
  `arc.backend.headless` 桌面包里没有，从官方 160.1 服务端包 `/tmp/server160.jar` 补。
- 场景类**注入 mod jar**（`crystal/dbg/*.class` 塞进 `mods/Crystal.jar`），由 crystal 自己的
  classloader 加载 —— 保证测试和模组共享同一份 `CultivationState` 静态状态。
- `verify/boot/crystalverify/Boot.java` 是启动器（系统 classpath）。crystal 是纯客户端模组，
  headless 里它自己的 `init()` 跑不完（要图集/字体/图标），所以启动器装了最小替身
  （`new TextureAtlas()` 空图集、Unsafe 分配的 UI 空壳、Icon 替身、netServer/logic）。
  面板/弹窗这些"要画面"的东西 headless 不验，交给真客户端那层。
- 判定标准是数值/集合，不看画面；主电池里 `FAIL` 就是真问题（会打印期望/实际）。

## 真客户端

```bash
# 数据目录里要有张地图（任意 .msav），地图名不为空即可
mkdir -p /tmp/mp_xw_client/data/maps && cp <某张>.msav /tmp/mp_xw_client/data/maps/
verify/run-client.sh /tmp/mp_xw_client/data
```

- 流程：Xvfb（离屏 X）+ `SDL_VIDEODRIVER=offscreen`（SDL 走 EGL，Mesa llvmpipe 软渲染）；
  驱动 mod `verify/client/cdrv`（装到数据目录）反射调用注入 mod jar 的 `crystal.dbg.XiuWeiClientTest`；
  场景自己点 HUD「修为」按钮、点渡劫弹窗按钮、按步骤截图。
- 脚本会先跑一次 headless `seed` 阶段：把数据目录的 settings 重写成 `locale=zh_CN`
  （模组只带了 `bundles/bundle_zh_CN.properties`，英文 locale 下所有文案是 `???key???`，
  截图会很难看）。之后才起客户端，这样模组 bundle 才会被合并进 `Core.bundle`。
- 截图落 `~/sd/shots/`（按跨次运行的连续序号 `001_ 002_ …` 命名，不会互相覆盖）。
- **同一帧里 `show()` + 截图会截到上一帧的画面**：开界面和截图必须分两步（本脚本每 12 帧走一步）。
- 报告：`<数据目录>/xiuwei-client-report.txt`，客户端日志：`<数据目录>/client.log`。

## 修过的两个缺陷（2026-09-23）

第一轮跑主电池出 4 条 FAIL（同一个 bug 的多个切面），已修：

1. `JingJie.jiuLu`（旧路数组）顺序错：`shenjun(19000)` 排在 `shenwang(11000)` 前面 →
   旧路「下一境界」从**真神**直接跳到**神君**，跳过神王/神皇，进度条分母也跟着错。
   （`JingJie.shenOldRoad` 那份 Seq 是对的，只有 `jiuLu` 数组没跟着改。已改成
   `weishen → zhenshen → shenwang → shenhuang → shenjun → …`，与 `shenOldRoad` 一致。）
2. `PlayerXiuWeiSystem.clear()`（debug 的「重置修为」）会把占位功法 `none` 一起 `lock()`。
   `none` 是凡人境界绑定的功法，锁上之后功法门槛永远停在"凡人缺功法"，而凡人被 `getMin()`
   豁免 → 之后不管灵力多高、功法多全，境界都升不上去（重启游戏才恢复，因为 `GongFas.load()`
   会重新 `none.unlock()`）。已改成跳过 `none` 并在最后兜底 `unlock()`。

修完复跑：主电池 **86 PASS / 0 FAIL**，跨进程存档 写 2 + 读 14 + 旧档 13 全过，
真客户端 30 PASS / 0 FAIL（截图 `~/sd/shots/042_…`~`048_…`，日志见 `verify/build/*.log`）。

顺带发现的（没算 FAIL，但值得知道）：

- `PlayerXiuWeiSystem.loadCurrentAvailableJingJie()` 从没被调用 → 存档里的
  `crystal.availableJingJie_ordinal` 只写不读，可用境界每次读档都由当前境界重算。
- 渡劫击杀数只在「起劫 / 渡劫结束 / 回主菜单」落盘：中途强杀进程会丢当前进度。
- 面板在默认 900×700 窗口下右侧会被裁：卡片少时 `growX()` 把单元格拉开，
  第二列功法卡片 / 渡劫目标文案尾部 / 第 4 个神武槽位都跑到可视区外（没有横向滚动）。
- 渡劫确认弹窗的两个按钮用 `Styles.flatTogglet`，未选中态不画底/边框，截图里像纯文字。
