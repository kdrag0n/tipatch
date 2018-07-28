#!env bash

echo '- Android'
gomobile bind -target android -o android.aar -ldflags="-s -w" .
