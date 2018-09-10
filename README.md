# Tipatch

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![GitHub release](https://img.shields.io/github/release/kdrag0n/tipatch.svg?style=flat)](https://github.com/kdrag0n/tipatch/releases)
[![Release downloads](https://img.shields.io/github/downloads/kdrag0n/tipatch/total.svg?style=flat)](https://github.com/kdrag0n/tipatch/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat)](https://opensource.org/licenses/MIT)

Tipatch patches TWRP to backup contents of internal storage (the emulated SD card at /sdcard) as part of **Data**, thus preventing data loss. Internal storage typically includes items such as photos, videos, downloads, game data, and other assorted files. [Website](https://khronodragon.com/projects/tipatch/)

<a href="https://play.google.com/store/apps/details?id=com.kdrag0n.tipatch" target="_blank"><img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" height="60" alt="Get it on Google Play"></a>
or <a href="https://labs.xda-developers.com/store/app/com.kdrag0n.tipatch" target="_blank">XDA Labs</a>

## Why?
TWRP Data backups do not include Internal Storage, which is the emulated SD card that stores your photos, videos, downloads, and more. This means that in the event you transfer phones or completely wipe data, all of that is gone! Eventual data loss is inevitable here.

I decided to make this tool after encountering data loss multiple times because of the issue. To make it accessible to all users, I made it an app instead of only a command-line computer program.

## Effects
 - `Data (incl. storage)` instead of `Data (excl. storage)` on Backup screen
 - `Backups of Data include...` instead of `...do not include...` when stating backup
 - Bigger calculated Data size on Backup screen

**__Side effect__: wiping Data *WILL* also wipe internal storage!**

## Will it work for *device name*?
The app supports patching the currently installed recovery in-place on rooted devices. It has been confirmed to work on Exynos, Snapdragon, Kirin, and MediaTek devices.

If you are **not rooted** or your device is not supported by in-place patching, don't worry. It also supports patching an image and saving the result as an image.

## Help, something broke!
If you used in-place patching and your recovery doesn't boot anymore, tap `Restore backup` in the action menu.

If you don't want internal storage to be backed up anymore, tap `Undo patch` in the action menu instead of `Patch`.

You can find me on [Telegram](https://t.me/kdrag0n) or [e-mail](mailto:kdrag0n@pm.me) me.