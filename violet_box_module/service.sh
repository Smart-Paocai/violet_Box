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

set_prop_value() {
  key="$1"
  value="$2"
  [ -z "$key" ] && return 1
  [ -z "$value" ] && return 0
  if command -v resetprop >/dev/null 2>&1; then
    resetprop "$key" "$value"
    return $?
  fi
  if [ -x /data/adb/magisk/resetprop ]; then
    /data/adb/magisk/resetprop "$key" "$value"
    return $?
  fi
  return 1
}

is_unset_or_default() {
  val="$1"
  [ -z "$val" ] && return 0
  [ "$val" = "default" ] && return 0
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

apply_global_props() {
  [ -f "$CONFIG_FILE" ] || return 1
  . "$CONFIG_FILE"

  [ -z "$spoof_props" ] && spoof_props=0
  [ "$spoof_props" = "0" ] && return 0

  if is_unset_or_default "$ro_product_brand" \
    || is_unset_or_default "$ro_product_manufacturer" \
    || is_unset_or_default "$ro_product_model" \
    || is_unset_or_default "$ro_product_device"; then
    return 0
  fi

  set_prop_value "ro.product.brand" "$ro_product_brand" || return 1
  set_prop_value "ro.product.manufacturer" "$ro_product_manufacturer" || return 1
  set_prop_value "ro.product.model" "$ro_product_model" || return 1
  set_prop_value "ro.product.device" "$ro_product_device" || return 1
  set_prop_value "ro.product.name" "$ro_product_device" || return 1
  set_prop_value "ro.product.marketname" "$ro_product_model" || return 1
  set_prop_value "ro.product.system.brand" "$ro_product_brand" || return 1
  set_prop_value "ro.product.system.name" "$ro_product_device" || return 1
  set_prop_value "ro.product.system.device" "$ro_product_device" || return 1
  set_prop_value "ro.build.product" "$ro_product_device" || return 1
  if ! is_unset_or_default "$ro_build_fingerprint"; then
    set_prop_value "ro.build.fingerprint" "$ro_build_fingerprint" || return 1
  fi
}

mkdir -p "$PERSISTENT_DIR"

# 复刻 susfs4ksu 行为：mode=1 在 service 阶段应用
. "$CONFIG_FILE" 2>/dev/null
[ -z "$spoof_uname" ] && spoof_uname=1
[ -z "$spoof_props" ] && spoof_props=0

# 记录特性，便于排障
SUSFS_BIN="$(find_susfs_bin)"
if [ -n "$SUSFS_BIN" ]; then
  "$SUSFS_BIN" show enabled_features > "$FEATURE_FILE" 2>/dev/null || true
fi

if [ "$spoof_uname" = "1" ] && apply_spoof_uname; then
  echo "$(date '+%F %T') service: mode=1 set_uname applied" >> "$LOG_FILE"
else
  # 尝试探测“内核不支持”并记录明确日志
  if [ -n "$SUSFS_BIN" ] && [ "$spoof_uname" = "1" ]; then
    err="$("$SUSFS_BIN" set_uname "default" "default" 2>&1 >/dev/null)"
    case "$err" in
      *"operation not supported"*|*"not supported"*)
        echo "$(date '+%F %T') service: kernel does not support set_uname" >> "$LOG_FILE"
        ;;
      *)
        echo "$(date '+%F %T') service: skipped/failed (mode=$spoof_uname)" >> "$LOG_FILE"
        ;;
    esac
  else
    echo "$(date '+%F %T') service: skipped/failed (mode=$spoof_uname)" >> "$LOG_FILE"
  fi
fi

if [ "$spoof_props" = "1" ] && apply_global_props; then
  echo "$(date '+%F %T') service: mode=1 global props applied" >> "$LOG_FILE"
else
  echo "$(date '+%F %T') service: global props skipped/failed (mode=$spoof_props)" >> "$LOG_FILE"
fi
