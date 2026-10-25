#!/usr/bin/env bash
# エミュレーターで起動まわりを自動で確かめる（リリース前に実行）。
#  1. 新しい WebView の端末：初めて起動するとログイン画面が出る
#  2. 同じ端末で通信できないとき：「Spotify に接続できません」が出て、つながると自動でログイン画面に戻る
#  3. WebView が使えない端末（Android 8 の素のイメージ）：落ちずに WebView 更新の案内が出る
# 使い方: scripts/smoke-test.sh [APK]   （省略時は app/build/outputs/apk/release/app-release.apk）
# エミュレーターは SMOKE_AVD（既定 keyboard-test）と SMOKE_AVD_OLD（既定 keyboard-test-api26）を起動して使う。
# CI のように起動済みのエミュレーターを使うときは SMOKE_SERIAL（例 emulator-5554）と
# SMOKE_CASES（modern＝1・2 / old＝3 / 省略で両方）を指定する。
set -uo pipefail
cd "$(dirname "$0")/.."

apk=${1:-app/build/outputs/apk/release/app-release.apk}
avd=${SMOKE_AVD:-keyboard-test}
avd_old=${SMOKE_AVD_OLD:-keyboard-test-api26}
emulator=${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator
pkg=com.ninjin.spotifyweb
port=5584
serial=${SMOKE_SERIAL:-emulator-$port}
cases=${SMOKE_CASES:-modern old}
failures=0

[[ -f "$apk" ]] || { echo "APK がありません: $apk" >&2; exit 1; }

boot() {
  [[ -z "${SMOKE_SERIAL:-}" ]] && "$emulator" -avd "$1" -port $port -no-window -no-audio -no-snapshot -no-boot-anim >/dev/null 2>&1 &
  adb -s $serial wait-for-device
  for _ in $(seq 120); do
    [[ "$(adb -s $serial shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
    sleep 2
  done
  sleep 15  # 起動直後はシステムの UI が重いので落ち着くまで待つ
  adb -s $serial install -r "$apk" >/dev/null
  adb -s $serial shell pm grant $pkg android.permission.POST_NOTIFICATIONS 2>/dev/null
  adb -s $serial logcat -c
}

shutdown() {
  # 起動済みのエミュレーターを借りているときは止めず、次のために後片付けだけする
  if [[ -n "${SMOKE_SERIAL:-}" ]]; then adb -s $serial uninstall $pkg >/dev/null 2>&1; return; fi
  adb -s $serial emu kill >/dev/null 2>&1; sleep 5
}

launch() {
  adb -s $serial shell am force-stop $pkg
  adb -s $serial shell am start -n $pkg/.MainActivity >/dev/null
}

# 画面の文字（WebView の中身も含む）に $1 が出るまで最大 $2 秒待つ
expect_text() {
  local deadline=$((SECONDS + $2))
  while (( SECONDS < deadline )); do
    adb -s $serial shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    local ui; ui=$(adb -s $serial shell cat /sdcard/ui.xml 2>/dev/null)
    grep -q -- "$1" <<<"$ui" && return 0
    # 重いエミュレーターではシステムの「応答していません」が出て画面をふさぐので、「Wait」を押して閉じる
    local wait_btn; wait_btn=$(grep -oE 'text="Wait"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' <<<"$ui" | grep -oE '[0-9]+' | tail -4 | tr '\n' ' ')
    if [[ -n "$wait_btn" ]]; then
      set -- "$1" "$2" $wait_btn
      adb -s $serial shell input tap $(( ($3 + $5) / 2 )) $(( ($4 + $6) / 2 ))
    fi
    sleep 3
  done
  return 1
}

check() {
  local name=$1 text=$2 wait=$3
  if expect_text "$text" "$wait"; then echo "PASS  $name"
  else echo "FAIL  $name（「$text」が出ない）"; failures=$((failures + 1)); fi
  if adb -s $serial logcat -d -b crash 2>/dev/null | grep -q "$pkg"; then
    echo "FAIL  $name（アプリが落ちた）"; failures=$((failures + 1))
    adb -s $serial logcat -d -b crash | grep -A5 "FATAL" | head -20
  fi
}

if [[ " $cases " == *" modern "* ]]; then
echo "== $avd"
boot "$avd"
launch
check "初回起動でログイン画面" 'text="Continue"' 90
adb -s $serial shell cmd connectivity airplane-mode enable
sleep 3
launch
check "通信できないときの案内" "Spotify に接続できません" 60
adb -s $serial shell cmd connectivity airplane-mode disable
check "つながったら自動でログイン画面" 'text="Continue"' 90
shutdown
fi

if [[ " $cases " == *" old "* ]]; then
echo "== $avd_old"
boot "$avd_old"
launch
check "WebView が使えない端末で案内" "Android System WebView" 60
shutdown
fi

if (( failures )); then echo "失敗: $failures 件"; exit 1; fi
echo "すべて成功"
