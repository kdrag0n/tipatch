package main

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"os"

	gzip "github.com/klauspost/pgzip"
	"go4.org/bytereplacer"
)

func patchImage(inputPath, outputPath string) {
	fmt.Println(" - Extracting image")
	in, err := os.Open(inputPath)
	checkMsg(err, "opening image for reading")
	defer in.Close()

	image := imageFromFile(in)

	fmt.Println(" - Extracting ramdisk")
	gReader, err := gzip.NewReader(bytes.NewReader(image.Ramdisk))
	checkMsg(err, "preparing to extract ramdisk")
	ramdisk, err := ioutil.ReadAll(gReader)
	checkMsg(err, "extracting ramdisk")

	err = gReader.Close()
	checkMsg(err, "cleaning up ramdisk extraction")

	fmt.Println(" - Patching ramdisk")
	replacer := bytereplacer.New(
		// Preserve /data/media
		"\x00/media\x00", "\x00/.twrp\x00",
		// Change text in Backup screen for English
		"Data (excl. storage)", "Data (incl. storage)",
		// Change orange warning text when backing up for English
		"Backups of {1} do not include any files in internal storage such as pictures or downloads.", "Backups of {1} include files in internal storage such as pictures and downloads.          ",
	)

	ramdisk = replacer.Replace(ramdisk)

	fmt.Println(" - Compressing ramdisk")
	var gzRamdisk bytes.Buffer
	gWriter, err := gzip.NewWriterLevel(&gzRamdisk, gzip.BestCompression)
	checkMsg(err, "preparing to compress ramdisk")
	_, err = gWriter.Write(ramdisk)
	checkMsg(err, "compressing ramdisk")

	err = gWriter.Flush()
	checkMsg(err, "finishing up ramdisk compression")
	err = gWriter.Close()
	checkMsg(err, "cleaning up ramdisk compression")

	image.Ramdisk = gzRamdisk.Bytes()

	fmt.Println(" - Repacking & writing image")
	out, err := os.OpenFile(outputPath, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0644)
	checkMsg(err, "creating output file")
	defer out.Close()

	err = image.WriteHeader(out)
	checkMsg(err, "writing output file header")
	err = image.WriteData(out)
	checkMsg(err, "writing output file data")

	fmt.Printf(" - Finished! Output is '%s'.\n", outputPath)
}
