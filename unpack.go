package main

/* Original source: https://github.com/chenxiaolong/DualBootPatcher */

import (
	"bytes"
	"errors"
	"os"
	"unsafe"
)

func readPadding(fin *os.File, itemSize uint32, pageSize uint32) (count int, err error) {
	pageMask := pageSize - 1

	if (itemSize & pageMask) == 0 {
		return
	}

	count = int(pageSize - (itemSize & pageMask))
	count, err = fin.Read(make([]byte, count))

	return
}

func imageFromFile(fin *os.File) *Image {
	var i int64
	magicbuf := make([]byte, BootMagicSize)
	for i = 0; i <= 512; i++ {
		_, err := fin.Seek(i, os.SEEK_SET)
		checkMsg(err, "seeking in input file")

		_, err = fin.Read(magicbuf)
		checkMsg(err, "reading magic number from input file")

		if bytes.Equal(magicbuf, []byte(BootMagic)) {
			break
		}
	}

	if i > 512 {
		checkMsg(errors.New("offset > 512"), "finding Android header")
	}

	_, err := fin.Seek(0, os.SEEK_SET)
	checkMsg(err, "seeking to read header")

	var header RawImage
	headerSize := uint32(unsafe.Sizeof(header))
	headerBuf := make([]byte, headerSize)
	_, err = fin.Read(headerBuf)
	checkMsg(err, "reading header from input")

	bufDataPtr := *(*uintptr)(unsafe.Pointer(&headerBuf))
	header = *(*RawImage)(unsafe.Pointer(bufDataPtr))

	baseAddr := header.KernelAddr - 0x00008000

	readPadding(fin, headerSize, header.PageSize)
	kernel := make([]byte, header.KernelSize)
	_, err = fin.Read(kernel)
	checkMsg(err, "reading kernel from input")

	readPadding(fin, header.KernelSize, header.PageSize)
	ramdisk := make([]byte, header.RamdiskSize)
	_, err = fin.Read(ramdisk)
	checkMsg(err, "reading ramdisk from input")

	readPadding(fin, header.RamdiskSize, header.PageSize)
	second := make([]byte, header.SecondSize)
	if header.SecondSize > 0 {
		_, err = fin.Read(second)
		checkMsg(err, "reading second stage bootloader from input")
	}

	readPadding(fin, header.SecondSize, header.PageSize)
	deviceTree := make([]byte, header.DtSize)
	if header.DtSize > 0 {
		_, err = fin.Read(deviceTree)
		checkMsg(err, "reading device tree from input")
	}

	return &Image{
		Board:     string(header.Board[:bytes.IndexByte(header.Board[:], 0)]),
		Cmdline:   string(header.Cmdline[:bytes.IndexByte(header.Cmdline[:], 0)]),
		OSVersion: header.OSVersion,

		Base:          baseAddr,
		KernelOffset:  header.KernelAddr - baseAddr,
		RamdiskOffset: header.RamdiskAddr - header.KernelAddr + 0x00008000,
		SecondOffset:  header.SecondAddr - header.KernelAddr + 0x00008000,
		TagsOffset:    header.TagsAddr - header.KernelAddr + 0x00008000,
		PageSize:      header.PageSize,

		Kernel:     kernel,
		Ramdisk:    ramdisk,
		Second:     second,
		DeviceTree: deviceTree,
	}
}
