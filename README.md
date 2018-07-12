# Tipatch
A patcher for [TWRP](https://twrp.me/) images to include /data/media (Internal Storage) inside Data backups.

# Usage
`tipatch [-o <output.img>] <input.img>`

# Side effects
There are side effects associated that cannot be avoided without recompiling TWRP, due to the nature of binary patching.

Known side effects:
 - Wiping /data **WILL** wipe /data/media as well

# Disclaimer
```cpp
#include <std/disclaimer.h>
/*
 * I'm not responsible for bricked devices, dead SD cards, thermonuclear war, or you getting fired because the alarm app failed.
 * Please do some research if you have any concerns about features included in the products you find here before flashing it!
 * YOU are choosing to make these modifications, and if you point the finger at me for messing up your device, I will laugh at you.
 * Your warranty will be void if you tamper with any part of your device / software.
 */
```
