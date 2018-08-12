#pragma once

#include <string>
#include "image.h"

/*
 * // Image represents the contents of a boot image.
type Image struct {
	Board     string
	Cmdline   string
	osVersion uint32

	base          uint32
	kernelOffset  uint32
	ramdiskOffset uint32
	secondOffset  uint32
	tagsOffset    uint32
	pageSize      uint32
	Kernel        []byte
	Ramdisk       []byte
	Second        []byte
	DeviceTree    []byte

	OriginalRamdiskSize int
	OverrideRamdiskSize int
}
*/

class Image {
public:
    Image(char* data);
};