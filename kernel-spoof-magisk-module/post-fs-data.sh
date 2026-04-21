#!/system/bin/sh

PERSISTENT_DIR="/data/adb/violet_kernel_spoof"
CONFIG_FILE="$PERSISTENT_DIR/config.sh"
LOG_FILE="$PERSISTENT_DIR/kernel_spoof.log"
FEATURE_FILE="$PERSISTENT_DIR/enabled_features.txt"

find_susfs_bin() {
  if [ -x /data/adb/ksu/bin/ksu_susfs ]; then
    echo "/data/adb/ksu/bin/ksu_susfs"
    return 0
  fi
  if command -v ksu_susfs >/dev/null 2>&1; then
    command -v ksu_susfs
    return 0
  fi
  return 1
}

apply_spoof_uname() {
  [ -f "$CONFIG_FILE" ] || return 1
  . "$CONFIG_FILE"

  [ -z "$spoof_uname" ] && spoof_uname=1
  [ "$spoof_uname" = "0" ] && return 0

  [ -z "$kernel_version" ] && kernel_version='default'
  [ -z "$kernel_build" ] && kernel_build='default'

  SUSFS_BIN="$(find_susfs_bin)" || return 1
  "$SUSFS_BIN" set_uname "$kernel_version" "$kernel_build"
}

cache_features() {
  SUSFS_BIN="$(find_susfs_bin)" || return 1
  "$SUSFS_BIN" show enabled_features > "$FEATURE_FILE" 2>/dev/null || true
}

mkdir -p "$PERSISTENT_DIR"
cache_features

# 复刻 susfs4ksu 行为：mode=2 在 post-fs-data 阶段应用
. "$CONFIG_FILE" 2>/dev/null
[ -z "$spoof_uname" ] && spoof_uname=1

if [ "$spoof_uname" = "2" ] && apply_spoof_uname; then
  echo "$(date '+%F %T') post-fs-data: mode=2 set_uname applied" >> "$LOG_FILE"
else
  echo "$(date '+%F %T') post-fs-data: skipped/failed (mode=$spoof_uname)" >> "$LOG_FILE"
fi
