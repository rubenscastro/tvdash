#!/bin/sh
# Desfaz todas as mudanças feitas na TV (10.0.10.149). Rode: sh desfazer-tudo.sh
TV=10.0.10.149:5555
adb connect $TV >/dev/null
adb -s $TV shell pm enable com.tcl.exhibit
adb -s $TV shell pm enable com.tcl.hotelmenu
adb -s $TV shell pm enable com.tcl.esticker
adb -s $TV shell pm enable com.tcl.logkit
adb -s $TV shell pm enable com.tcl.messagebox
adb -s $TV shell pm enable com.tcl.waterfall.overseas
adb -s $TV shell pm enable com.tcl.repairguide
adb -s $TV shell pm enable com.tcl.ocean.instructions
adb -s $TV shell pm enable com.google.android.play.games
adb -s $TV shell pm enable com.google.android.youtube.tvmusic
adb -s $TV shell pm enable com.google.android.feedback
adb -s $TV shell pm enable com.android.tv.feedbackconsent
adb -s $TV shell settings put global window_animation_scale 1.0
adb -s $TV shell settings put global transition_animation_scale 1.0
adb -s $TV shell settings delete global animator_duration_scale
adb -s $TV shell settings put secure screensaver_enabled 1
adb -s $TV shell settings put secure screensaver_activate_on_sleep 1
adb -s $TV shell settings put secure screensaver_activate_on_dock 1
adb -s $TV shell pm enable com.google.android.apps.tv.launcherx
adb -s $TV shell pm enable com.google.android.tungsten.setupwraith
sleep 3
adb -s $TV uninstall com.ansub.tvhome
adb -s $TV shell input keyevent KEYCODE_HOME
