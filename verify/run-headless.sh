#!/usr/bin/env bash
# Crystal 模组的 headless 逻辑测试（几秒出结果，不需要图形界面）。
#
#   verify/run-headless.sh <数据目录> [场景类] [场景参数...]
#     <数据目录> 会被就地改造成"游戏数据目录"：mods/Crystal.jar = 新编的包 + 注入的测试类
#     场景类默认 crystal.dbg.XiuWeiHeadlessTest
#
# 例子：
#   ./gradlew --offline dex && verify/run-headless.sh /tmp/mp_xiu/data
#   verify/run-headless.sh /tmp/mp_xiu/data crystal.dbg.XiuWeiHeadlessTest --probe
#
# 说明：
#  - 游戏 jar 用官方的 160.4 桌面包（/tmp/mind1604.jar，模组就是照这个版本编的）；
#    headless 后端（arc.backend.headless）桌面包里没有，从官方 160.1 服务端包补。
#  - 测试场景类**注入 mod jar**，由 crystal 自己的 classloader 加载：保证和模组共享同一份
#    CultivationState 静态状态（另起一个 jar 当模组会各拿一份类，测出来的状态是错的）。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$ROOT/verify"
GAME_JAR="${GAME_JAR:-/tmp/mind1604.jar}"
SERVER_JAR="${SERVER_JAR:-/tmp/server160.jar}"   # 提供 arc 的 headless 后端
MOD_JAR="${MOD_JAR:-$ROOT/build/libs/Crystal.jar}"

if [ $# -lt 1 ]; then
  echo "用法: $0 <数据目录> [场景类] [场景参数...]" >&2
  exit 2
fi

data="$1"; shift
test="${1:-crystal.dbg.XiuWeiHeadlessTest}"
# 第二个参数不是类名（没有点）时，当成场景参数，用默认场景类
if [[ "$test" == *.* ]]; then
  if [ $# -gt 0 ]; then shift; fi
else
  test="crystal.dbg.XiuWeiHeadlessTest"
fi

[ -f "$GAME_JAR" ] || { echo "找不到游戏 jar: $GAME_JAR" >&2; exit 2; }
[ -f "$SERVER_JAR" ] || { echo "找不到 $SERVER_JAR（headless 后端）" >&2; exit 2; }
[ -f "$MOD_JAR" ] || { echo "先 ./gradlew --offline dex（没找到 $MOD_JAR）" >&2; exit 2; }

mkdir -p "$HERE/build/boot" "$HERE/build/scenario" "$data/mods"

echo "[verify] 编译启动器 + 场景类..."
javac -nowarn -encoding UTF-8 -cp "$GAME_JAR:$SERVER_JAR" -d "$HERE/build/boot" "$HERE"/boot/crystalverify/*.java || exit 1
javac -nowarn -encoding UTF-8 -cp "$MOD_JAR:$GAME_JAR" -d "$HERE/build/scenario" "$HERE"/tests/crystal/dbg/*.java || exit 1

echo "[verify] 装数据目录：$data/mods/Crystal.jar（新编的包 + 注入的测试类）"
cp "$MOD_JAR" "$data/mods/Crystal.jar"
(cd "$HERE/build/scenario" && zip -q -g "$data/mods/Crystal.jar" crystal/dbg/*.class) || exit 1

run(){
  java -cp "$GAME_JAR:$SERVER_JAR:$HERE/build/boot" crystalverify.Boot "$data" "$test" "$@"
}

echo "[verify] 跑 $test"
out="$(run "$@" 2>&1)"; code=$?
if [ $code -eq 3 ]; then
  # 上次客户端/测试跑挂过会把 crystal 标成 mod-crystal-failed，之后模组根本不会被加载（测试会"假过"）
  echo "[verify] crystal 没加载（多半是上次跑挂留下的 failed 标记），清掉 settings 重试"
  for f in settings.bin settings_backup.bin; do
    [ -f "$data/$f" ] && mv "$data/$f" "$data/$f.bak-$$"
  done
  out="$(run "$@" 2>&1)"; code=$?
fi

printf '%s\n' "$out"
exit $code
