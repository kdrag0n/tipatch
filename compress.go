package tipatch

import (
	"bytes"
	"errors"
	"io"

	gzip "github.com/klauspost/pgzip"
)

// Compression types/modes
const (
	CompGzip = iota
	CompLz4
	CompLzo
	CompXz
	CompBzip2
	CompLzma
	CompUnknown
)

// CompressRamdisk compresses the input ramdisk in a certain mode.
func CompressRamdisk(ramdisk []byte, cMode int) ([]byte, error) {
	var buf bytes.Buffer
	var writer io.WriteCloser
	var err error

	switch cMode {
	case CompGzip:
		writer, err = gzip.NewWriterLevel(&buf, gzip.BestCompression)
		if err != nil {
			return nil, eMsg(err, "preparing to compress ramdisk")
		}
	case CompLz4:
		return nil, eMsg(errors.New("LZ4 ramdisk compression is not supported"), "preparing to compress ramdisk")
	case CompLzo:
		return nil, eMsg(errors.New("LZO ramdisk compression is not supported"), "preparing to compress ramdisk")
	case CompXz:
		return nil, eMsg(errors.New("XZ ramdisk compression is not supported"), "preparing to compress ramdisk")
	case CompBzip2:
		return nil, eMsg(errors.New("Bzip2 ramdisk compression is not supported"), "preparing to compress ramdisk")
	case CompLzma:
		return nil, eMsg(errors.New("LZMA ramdisk compression is not supported"), "preparing to compress ramdisk")
	default:
		return nil, eMsg(errors.New("Ramdisk compression format is not supported"), "preparing to compress ramdisk")
	}

	_, err = writer.Write(ramdisk)
	if err != nil {
		return nil, eMsg(err, "compressing ramdisk")
	}

	switch cMode {
	case CompGzip:
		err = writer.(*gzip.Writer).Flush()
	}

	if err != nil {
		return nil, eMsg(err, "finishing up ramdisk compression")
	}

	err = writer.Close()
	if err != nil {
		return nil, eMsg(err, "cleaning up ramdisk compression")
	}

	return buf.Bytes(), nil
}

// CompressRamdisk compresses the Image's ramdisk.
func (img *Image) CompressRamdisk(cMode int) (err error) {
	rd, err := CompressRamdisk(img.Ramdisk, cMode)
	if err != nil {
		return
	}

	img.Ramdisk = rd
	return
}
