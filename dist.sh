#!env bash


echo '- Android'
gomobile bind -target android -o android.aar -javapkg com.kdrag0n -ldflags="-s -w" .

[ -d "$HOME/code/android/tipatch/app/libs" ] && cp android.aar ~/code/android/tipatch/app/libs/native.aar
