# tvdash

Custom home screen + apps for the TCL 75C7K (Android TV, adb `10.0.10.149:5555`).

| Folder | What |
|---|---|
| `app/` | The Android app (`com.ansub.tvhome`, pure Java). Sources in `app/src/com/ansub/tvhome/`, images in `app/assets/`. Own git repo. |
| `cards/` | The home cards (weather, solar, Taurus): housedash React components bundled for a WebView. |
| `cerberus-tv/` | The Cerberus screen (React, reuses the Cerberus panel's CSS/components). |
| `house3d/` | The 3D house viewer rendered once on the server. |
| `renderer/` | The headless renderer that runs on atlas (`~/housedash-renderer` there). |
| `tools/` | adb helper scripts (switch to the stock launcher and back, channel scans). |
| `data/` | Channel scan results. |
| `backup/` | State of the TV before any change (packages, settings) — used to undo. |
| `changes.log` | Every change made, newest last. |

## Build
```sh
# app (needs the Android command-line tools)
cd app; unset JAVA_HOME; export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
APPS=apps.mine.txt sh build.sh            # -> app/out/tvhome.apk
adb -s 10.0.10.149:5555 install -r out/tvhome.apk

# cards: build, then copy the output into the app
cd cards && node build.mjs && cp dist/cards.js dist/index.html ../app/assets/cards/

# Cerberus screen (writes straight into app/assets/cerberus/)
cd cerberus-tv && node build.mjs

# 3D house bundle (then copy to renderer/house/ and redeploy on atlas)
cd house3d && npx esbuild src/main.js --bundle --minify --outfile=dist/bundle.js
```

The cards and Cerberus builds import code from the housedash and Cerberus repos
(`/Users/Shared/Projects/personal/housedash`, `.../cerberus`): they must stay where they are.

## Launcher switch
`sh tools/to-stock.sh` turns the TV's own launcher on; `sh tools/to-mine.sh` brings ours back.
