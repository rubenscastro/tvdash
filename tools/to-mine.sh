#!/bin/sh
# Bring our home screen back after to-stock.sh.
TV=${1:-10.0.10.149:5555}
adb connect "$TV" >/dev/null 2>&1
S="adb -s $TV shell"
$S cmd package set-home-activity com.ansub.tvhome/.HomeEntry
$S pm disable-user --user 0 com.google.android.apps.tv.launcherx >/dev/null
$S input keyevent KEYCODE_HOME
echo "Our home screen is back."
