#!env bash


echo '- Android'
gomobile bind -target android -o android.aar -javapkg com.kdrag0n.jni -ldflags="-s -w" .

app="$HOME/code/android/tipatch/app"
[ -d "$app" ] && mkdir -p "$app/libs" && cp android.aar "$app/libs/native.aar"
