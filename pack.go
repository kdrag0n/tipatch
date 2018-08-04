package tipatch

import (
	"bytes"
	"encoding/binary"
	"errors"
	"os"
	"unsafe"

	"github.com/cespare/xxhash"
)

// paddingSize calculates the amount of padding necessary for the Image's page size.
func (img *Image) paddingSize(dataSize int) int {
	pageSize := int(img.pageSize)
	pageMask := pageSize - 1
	pbSize := dataSize & pageMask

	if pbSize == 0 {
		return 0
	}

	return pageSize - pbSize
}

// writePadding writes padding for the image's page size.
func (img *Image) writePadding(out Writer, dataSize int) (err error) {
	size := img.paddingSize(dataSize)
	if size == 0 {
		return
	}

	pad := make([]byte, size)
	_, err = out.Write(pad)

	return
}

// checksum computes a checksum corresponding to all the data in the image.
func (img *Image) checksum(hdr *RawImage) uint64 {
	xxh := xxhash.New()

	xxh.Write(img.Kernel)
	xxh.Write(img.Ramdisk)
	xxh.Write(img.Second)
	xxh.Write(img.DeviceTree)

	return xxh.Sum64()
}

// WriteHeader writes the Image's header in Android boot format.
func (img *Image) WriteHeader(out Writer) (err error) {
	var magic [BootMagicSize]byte
	copy(magic[:], BootMagic)

	var board [BootNameSize]byte
	copy(board[:], img.Board)

	var cmdline [BootArgsSize]byte
	var extraCmdline [BootExtraArgsSize]byte

	cmdLen := len(img.Cmdline)
	if cmdLen <= BootArgsSize {
		copy(cmdline[:], img.Cmdline)
	} else if cmdLen <= BootArgsSize+BootExtraArgsSize {
		copy(cmdline[:], img.Cmdline[:BootArgsSize])
		copy(extraCmdline[:], img.Cmdline[BootArgsSize+1:])
	}

	hdr := RawImage{
		Magic: magic,

		KernelSize: uint32(len(img.Kernel)),
		KernelAddr: img.base + img.kernelOffset,

		RamdiskSize: uint32(len(img.Ramdisk)),
		RamdiskAddr: img.base + img.ramdiskOffset,

		SecondSize: uint32(len(img.Second)),
		SecondAddr: img.base + img.secondOffset,

		TagsAddr: img.base + img.tagsOffset,
		PageSize: img.pageSize,
		DtSize:   uint32(len(img.DeviceTree)),

		OSVersion: img.osVersion,

		Board:   board,
		Cmdline: cmdline,

		ID: [32]byte{},

		ExtraCmdline: extraCmdline,
	}

	checksum := img.checksum(&hdr)
	binary.LittleEndian.PutUint64(hdr.ID[:], checksum)

	hdrBytes := *(*[unsafe.Sizeof(hdr)]byte)(unsafe.Pointer(&hdr))
	count, err := out.Write(hdrBytes[:])
	if err != nil {
		return
	}

	err = img.writePadding(out, count)
	if err != nil {
		return
	}

	return
}

// writePaddedSection writes data to the output, then pads it to the page size.
func (img *Image) writePaddedSection(out Writer, data []byte) (err error) {
	count, err := out.Write(data)
	if err != nil {
		return
	}

	err = img.writePadding(out, count)
	return
}

// WriteData writes the data chunks (ramdisk, kernel, etc) to the output.
func (img *Image) WriteData(out Writer) (err error) {
	err = img.writePaddedSection(out, img.Kernel)
	if err != nil {
		return
	}

	err = img.writePaddedSection(out, img.Ramdisk)
	if err != nil {
		return
	}

	if len(img.Second) > 0 {
		err = img.writePaddedSection(out, img.Second)
		if err != nil {
			return
		}
	}

	if len(img.DeviceTree) > 0 {
		err = img.writePaddedSection(out, img.DeviceTree)
		if err != nil {
			return
		}
	}

	return
}

// WriteToFd writes all the data of the Image to the provided fd.
func (img *Image) WriteToFd(fd int) (err error) {
	out := os.NewFile(uintptr(fd), "img.img")

	err = img.WriteHeader(out)
	if err != nil {
		return
	}

	err = img.WriteData(out)
	if err != nil {
		return
	}

	return
}

// DumpBytes dumps the Image data into a byte slice.
func (img *Image) DumpBytes() ([]byte, error) {
	// Calculate size for efficiency
	ps := func(data []byte) int {
		return len(data) + img.paddingSize(len(data))
	}

	var hdr RawImage
	size := int(unsafe.Sizeof(hdr)) + ps(img.Kernel) + ps(img.Ramdisk) + ps(img.Second) + ps(img.DeviceTree)

	buf := bytes.NewBuffer(make([]byte, 0, size))

	err := img.WriteHeader(buf)
	if err != nil {
		return nil, err
	}

	err = img.WriteData(buf)
	if err != nil {
		return nil, err
	}

	return buf.Bytes(), nil
}

// WrapWriter wraps a Writer to error when count is -1.
// Useful for bindings via gobind.
func WrapWriter(orig Writer) Writer {
	return writerWrapper{
		orig: orig,
	}
}

// Writer is the interface that inplements the basic Write method.
// See the io package for full documentation.
// This exists as a stub for binding purposes.
type Writer interface {
	Write(p []byte) (n int, err error)
}

// writerWrapper implements the Writer interface for error handling purposes.
type writerWrapper struct {
	orig Writer
}

func (wr writerWrapper) Write(p []byte) (n int, err error) {
	n, err = wr.orig.Write(p)
	if n == -1 {
		err = errors.New("WriteError: n -1")
	}

	return
}
