package tipatch

/* Original source: https://github.com/chenxiaolong/DualBootPatcher */

import (
	"bytes"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"unsafe"

	"github.com/hashicorp/errwrap"
)

func eMsg(err error, msg string) error {
	return errwrap.Wrap(errors.New(msg), err)
}

// ReadPadding reads padding from the input file up to pageSize.
func ReadPadding(fin io.Reader, itemSize uint32, pageSize uint32) (count int, err error) {
	pageMask := pageSize - 1

	if (itemSize & pageMask) == 0 {
		return
	}

	count = int(pageSize - (itemSize & pageMask))
	count, err = fin.Read(make([]byte, count))

	return
}

// UnpackImage unpacks an image and reads all the embedded data blocks.
func UnpackImage(fin io.ReadSeeker) (*Image, error) {
	var i int64
	magicbuf := make([]byte, BootMagicSize)
	for i = 0; i <= 512; i++ {
		_, err := fin.Seek(i, os.SEEK_SET)
		if err != nil {
			return nil, eMsg(err, "seeking in input")
		}

		_, err = fin.Read(magicbuf)
		if err != nil {
			return nil, eMsg(err, "reading magic number from input")
		}

		if bytes.Equal(magicbuf, []byte(BootMagic)) {
			break
		}
	}

	if i > 512 {
		return nil, errwrap.Wrapf("finding Android header", errors.New("Perhaps this is not a TWRP image?"))
	}

	_, err := fin.Seek(0, os.SEEK_SET)
	if err != nil {
		return nil, eMsg(err, "seeking to read header")
	}

	var header RawImage
	headerSize := uint32(unsafe.Sizeof(header))
	headerBuf := make([]byte, headerSize)
	_, err = fin.Read(headerBuf)
	if err != nil {
		return nil, eMsg(err, "reading header from input")
	}

	bufDataPtr := *(*uintptr)(unsafe.Pointer(&headerBuf))
	header = *(*RawImage)(unsafe.Pointer(bufDataPtr))

	baseAddr := header.KernelAddr - 0x00008000

	ReadPadding(fin, headerSize, header.PageSize)
	kernel := make([]byte, header.KernelSize)
	_, err = fin.Read(kernel)
	if err != nil {
		return nil, eMsg(err, "reading kernel from input")
	}

	ReadPadding(fin, header.KernelSize, header.PageSize)
	ramdisk := make([]byte, header.RamdiskSize)
	_, err = fin.Read(ramdisk)
	if err != nil {
		return nil, eMsg(err, "reading ramdisk from input")
	}

	ReadPadding(fin, header.RamdiskSize, header.PageSize)
	second := make([]byte, header.SecondSize)
	if header.SecondSize > 0 {
		_, err = fin.Read(second)
		if err != nil {
			return nil, eMsg(err, "reading second stage bootloader from input")
		}
	}

	ReadPadding(fin, header.SecondSize, header.PageSize)
	deviceTree := make([]byte, header.DtSize)
	if header.DtSize > 0 {
		_, err = fin.Read(deviceTree)
		if err != nil {
			return nil, eMsg(err, "reading device tree from input")
		}
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
	}, nil
}

// ExtractRamdisk decompresses the provided ramdisk.
func ExtractRamdisk(compr []byte) (ramdisk []byte, cMode int, err error) {
	switch fmt.Sprintf("%x%x", compr[0], compr[1]) {
	case "425a":
		cMode = CompBzip2
	case "1f8b":
		cMode = CompGzip
	case "1f9e":
		cMode = CompGzip
	case "0422":
		cMode = CompLz4
	case "894c":
		cMode = CompLzo
	case "5d00":
		cMode = CompLzma
	case "fd37":
		cMode = CompXz
	default:
		cMode = CompUnknown
	}

	gReader, err := gzip.NewReader(bytes.NewReader(compr))
	if err != nil {
		return nil, cMode, eMsg(err, "preparing to extract ramdisk")
	}

	ramdisk, err = ioutil.ReadAll(gReader)
	if err != nil {
		return nil, cMode, eMsg(err, "extracting ramdisk")
	}

	err = gReader.Close()
	if err != nil {
		return nil, cMode, eMsg(err, "cleaning up ramdisk extraction")
	}

	return
}
