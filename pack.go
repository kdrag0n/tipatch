package tipatch

import (
	"encoding/binary"
	"errors"
	"io"
	"os"
	"unsafe"

	"github.com/cespare/xxhash"
)

// ErrLengthMismatch is returned when a Write call did not write the corect number of bytes.
var ErrLengthMismatch = errors.New("written byte count does not match data")

// writePadding writes padding for the image's page size.
func (img *Image) writePadding(out io.WriteSeeker) (err error) {
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

// checksum computes a checksum corresponding to all the data in the image.
func (img *Image) checksum(hdr *RawImage) uint64 {
	xxh := xxhash.New()

	xxh.Write(img.Kernel)
	xxh.Write(img.Ramdisk)
	xxh.Write(img.Second)
	xxh.Write(img.DeviceTree)

	return xxh.Sum64()
}

// writeHeader writes the Image's header in Android boot format.
func (img *Image) writeHeader(out io.WriteSeeker) (err error) {
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
	} else if len(hdrBytes) != count {
		err = ErrLengthMismatch
		return
	}

	err = img.writePadding(out)
	if err != nil {
		return
	}

	return
}

// writePaddedSection writes data to the output, then pads it to the page size.
func (img *Image) writePaddedSection(out io.WriteSeeker, data []byte) (err error) {
	count, err := out.Write(data)
	if err != nil {
		return
	} else if len(data) != count {
		err = ErrLengthMismatch
		return
	}

	err = img.writePadding(out)
	return
}

// writeData writes the data chunks (ramdisk, kernel, etc) to the output.
func (img *Image) writeData(out io.WriteSeeker) (err error) {
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

	err = img.writeHeader(out)
	if err != nil {
		return
	}

	err = img.writeData(out)
	if err != nil {
		return
	}

	return
}
