#!/usr/bin/env bash

b() {
    rm -f rel/tipatch_$3
    GOOS=$1 GOARCH=$2 go build -ldflags='-s -w' -o rel/tipatch_$3 ./cmd/tipatch
    $4 rel/tipatch_$3 > /dev/null
}

mkdir -p rel

echo '- darwin  amd64   tipatch_macos64'
b       darwin  amd64           macos64 upx

echo '- windows 386     tipatch_windows32.exe'
GO386=sse2 \
b       windows 386             windows32.exe upx
echo '- windows amd64   tipatch_windows64.exe'
b       windows amd64           windows64.exe upx

echo '- linux   386     tipatch_linux32'
GO386=sse2 \
b       linux   386             linux32 upx
echo '- linux   amd64   tipatch_linux64'
b       linux   amd64           linux64 upx

echo '- android armv7   tipatch_android32'
GOARM=7 \
b       linux   arm             android32 upx
echo '- android arm64   tipatch_android64'
b       linux   arm64           android64 goupx

echo '- android 386     tipatch_androidx86_32'
cp rel/tipatch_linux32 rel/tipatch_androidx86_32
echo '- android amd64   tipatch_androidx86_64'
cp rel/tipatch_linux64 rel/tipatch_androidx86_64
