#!/bin/sh
# Temporarily switch the TV back to its own (stock) home screen. Our app stays installed and untouched.
# Undo with: sh to-mine.sh
TV=${1:-10.0.10.149:5555}
adb connect "$TV" >/dev/null 2>&1
S="adb -s $TV shell"
STOCK=com.google.android.apps.tv.launcherx
$S pm enable "$STOCK" >/dev/null
# find the stock home activity (the HOME activity that is neither ours nor the system fallback)
ACT=$($S cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | grep "^$STOCK/" | head -1)
[ -n "$ACT" ] || ACT="$STOCK/.home.HomeActivity"   # known name; the lookup can miss right after enabling
$S cmd package set-home-activity "$ACT"
$S input keyevent KEYCODE_HOME
echo "Stock home screen is on ($ACT). To bring ours back: sh to-mine.sh"
