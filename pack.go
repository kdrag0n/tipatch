package tipatch

import (
	"encoding/binary"
	"errors"
	"os"
	"unsafe"

	"github.com/cespare/xxhash"
)

// ErrLengthMismatch is returned when a Write call did not write the corect number of bytes.
var ErrLengthMismatch = errors.New("written byte count does not match data")

// WritePadding writes padding for the image's page size to the given file.
func (img *Image) WritePadding(out *os.File) (err error) {
	curPos, err := out.Seek(0, os.SEEK_CUR)
	if err != nil {
		return
	}

	pageMask := img.pageSize - 1
	pad := make([]byte, (img.pageSize-(uint32(curPos)&pageMask))&pageMask)
	count, err := out.Write(pad)
	if err == nil && count != len(pad) {
		err = ErrLengthMismatch
	}

	return
}

// Checksum computes a checksum corresponding to all the data in the image.
func (img *Image) Checksum(hdr *RawImage) uint64 {
	xxh := xxhash.New()

	xxh.Write(img.Kernel)
	xxh.Write(img.Ramdisk)
	xxh.Write(img.Second)
	xxh.Write(img.DeviceTree)

	return xxh.Sum64()
}

// WriteHeader writes the Image's header to the provided file in Android boot format.
func (img *Image) WriteHeader(out *os.File) (err error) {
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

	checksum := img.Checksum(&hdr)
	binary.LittleEndian.PutUint64(hdr.ID[:], checksum)

	hdrBytes := *(*[unsafe.Sizeof(hdr)]byte)(unsafe.Pointer(&hdr))
	count, err := out.Write(hdrBytes[:])
	if err != nil {
		return
	} else if len(hdrBytes) != count {
		err = ErrLengthMismatch
		return
	}

	err = img.WritePadding(out)
	if err != nil {
		return
	}

	return
}

// WritePaddedSection writes data to the file, then pads it to the page size.
func (img *Image) WritePaddedSection(out *os.File, data []byte) (err error) {
	count, err := out.Write(data)
	if err != nil {
		return
	} else if len(data) != count {
		err = ErrLengthMismatch
		return
	}

	err = img.WritePadding(out)
	return
}

// WriteData writes the data chunks (ramdisk, kernel, etc) to the output file.
func (img *Image) WriteData(out *os.File) (err error) {
	err = img.WritePaddedSection(out, img.Kernel)
	if err != nil {
		return
	}

	err = img.WritePaddedSection(out, img.Ramdisk)
	if err != nil {
		return
	}

	if len(img.Second) > 0 {
		err = img.WritePaddedSection(out, img.Second)
		if err != nil {
			return
		}
	}

	if len(img.DeviceTree) > 0 {
		err = img.WritePaddedSection(out, img.DeviceTree)
		if err != nil {
			return
		}
	}

	return
}

// WriteToFd writes all the data of the Image to the provided fd.
func (img *Image) WriteToFd(fd uintptr) (err error) {
	out := os.NewFile(fd, "img.img")

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
