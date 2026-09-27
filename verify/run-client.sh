#!/usr/bin/env bash
# Crystal 修为系统的**真客户端**验证（离屏 Xvfb + Mesa 软渲染），自动截图。
#
#   verify/run-client.sh <数据目录>
#
# 做三件事：
#   1) 把 build/libs/Crystal.jar（先 ./gradlew --offline dex）+ 本目录的 crystal/dbg/*.class 装进数据目录；
#   2) 编译并装上驱动 mod（verify/client/cdrv，负责反射调用 crystal.dbg.XiuWeiClientTest）；
#   3) Xvfb + SDL offscreen 起真客户端，跑场景、连点带截图，结果写
#      <数据目录>/xiuwei-client-report.txt，截图写 ~/sd/shots（001_ 002_… 连续编号）。
#
# 需要：官方 160.4 桌面包 /tmp/mind1604.jar（模组照这个版本编的）、
#       aarch64 的 SDL native（用 combine 验证器里那份 verify/native/libsdl-arcarm64.so）。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$ROOT/verify"
GAME_JAR="${GAME_JAR:-/tmp/mind1604.jar}"
MOD_JAR="${MOD_JAR:-$ROOT/build/libs/Crystal.jar}"
NATIVE="${NATIVE:-/root/combine/verify/native/libsdl-arcarm64.so}"
DISPLAY_NUM="${DISPLAY_NUM:-:99}"
DRV_OUT="${XW_SHOTS:-$HOME/sd/shots}"

data="${1:?用法: $0 <数据目录>}"
[ -f "$GAME_JAR" ] || { echo "找不到游戏 jar: $GAME_JAR" >&2; exit 2; }
[ -f "$MOD_JAR" ] || { echo "先 ./gradlew --offline dex（没找到 $MOD_JAR）" >&2; exit 2; }
[ -f "$NATIVE" ] || { echo "找不到 SDL native: $NATIVE" >&2; exit 2; }

mkdir -p "$HERE/build/client" "$HERE/build/scenario" "$DRV_OUT" "$data/mods" "$data/maps"

echo "[verify] 编译场景类 + 驱动 mod..."
javac -nowarn -encoding UTF-8 -cp "$MOD_JAR:$GAME_JAR" -d "$HERE/build/scenario" "$HERE"/tests/crystal/dbg/*.java || exit 1
javac -nowarn -encoding UTF-8 -cp "$GAME_JAR" -d "$HERE/build/client" "$HERE"/client/cdrv/*.java || exit 1

echo "[verify] 装数据目录 $data（crystal.jar + 测试类 + 驱动 mod）"
cp "$MOD_JAR" "$data/mods/Crystal.jar"
(cd "$HERE/build/scenario" && zip -q -g "$data/mods/Crystal.jar" crystal/dbg/*.class) || exit 1
cp "$HERE/client/mod.hjson" "$HERE/build/client/"
(cd "$HERE/build/client" && rm -f "$HERE/build/xwdrv.jar" && zip -q -r "$HERE/build/xwdrv.jar" mod.hjson cdrv) || exit 1
cp "$HERE/build/xwdrv.jar" "$data/mods/xwdrv.jar"

# 上次跑挂会把 crystal 记成 mod-crystal-failed（写进 settings），下次模组根本不加载 → 先挪走
for f in settings.bin settings_backup.bin; do
  [ -f "$data/$f" ] && mv "$data/$f" "$data/$f.bak-prev"
done

# 重写一份干净 settings：locale=zh_CN（模组只带 zh_CN 语言包，英文下所有文案是 ???key???）
echo "[verify] seed settings（locale=zh_CN）"
"$HERE/run-headless.sh" "$data" crystal.dbg.XiuWeiHeadlessTest seed | grep -E "seed|PASS|FAIL" || true

echo "[verify] 注入 aarch64 SDL native ⊂ game.jar"
cp "$GAME_JAR" "$HERE/build/xw-game.jar"
(cd "$(dirname "$NATIVE")" && zip -q -g "$HERE/build/xw-game.jar" "$(basename "$NATIVE")")

X_SOCK="/tmp/.X11-unix/X${DISPLAY_NUM#:}"
if [ ! -e "$X_SOCK" ]; then
  echo "[verify] 启动 Xvfb $DISPLAY_NUM"
  nohup Xvfb "$DISPLAY_NUM" -screen 0 1280x800x24 -nolisten tcp > "$HERE/build/xvfb-xw.log" 2>&1 &
  sleep 3
fi

echo "[verify] 跑客户端（软渲染，约 1~3 分钟），日志 -> $data/client.log"
DISPLAY="$DISPLAY_NUM" SDL_VIDEODRIVER=offscreen \
  java -Xmx2g -Dmindustry.data.dir="$data" -Dxw.out="$data" -Dxw.shots="$DRV_OUT" \
  -jar "$HERE/build/xw-game.jar" > "$data/client.log" 2>&1
code=$?

echo "[verify] 客户端退出码=$code"
grep -E "^\[xw\]|^\[xwd\]|^\[Crystal\]" "$data/client.log" | tail -60
echo "[verify] 报告: $data/xiuwei-client-report.txt"
[ -f "$data/xiuwei-client-report.txt" ] && cat "$data/xiuwei-client-report.txt"
echo "[verify] 截图:"; ls -l "$DRV_OUT" | tail -10
exit $code
