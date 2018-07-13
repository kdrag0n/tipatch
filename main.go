package main

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"

	gzip "github.com/klauspost/pgzip"
	flag "github.com/spf13/pflag"
)

// General command-line inteface constants
const (
	LastTestedVersion = "3.2.2"
)

func replace(in *[]byte, from, to string) {
	replaced := bytes.Replace(*in, []byte(from), []byte(to), -1)
	*in = replaced
}

func checkMsg(err error, msg string) {
	if err != nil {
		fmt.Printf(" ! Error %s!\n", msg)
		fmt.Printf(" ! %s\n", err.Error())
		os.Exit(2)
	}
}

func main() {
	var inputPath string
	var outputPath string

	// Italic test
	flag.StringVarP(&inputPath, "input", "i", "", "Path to the TWRP image to patch.")
	flag.StringVarP(&outputPath, "output", "o", "", "Path to output patched image to.")

	fmt.Printf("Tipatch by @kdrag0n\nLast tested with TWRP %s\n\n", LastTestedVersion)
	flag.Parse()

	if inputPath == "" {
		if flag.NArg() > 0 {
			inputPath = flag.Arg(0)
		} else {
			fmt.Println(" ! You must provide a TWRP image to patch!")
			fmt.Println(" ! Example: tipatch twrp-3.2.1-0-grouper.img")
			os.Exit(2)
		}
	}

	if outputPath == "" {
		if flag.NArg() > 1 {
			outputPath = flag.Arg(1)
		} else {
			ext := filepath.Ext(inputPath)
			base := filepath.Base(inputPath)
			dir, _ := filepath.Split(inputPath)

			newName := strings.TrimSuffix(base, ext) + "-tipatched" + ext

			outputPath = filepath.Join(dir, newName)
		}
	}

	fInfo, err := os.Stat(inputPath)
	if err != nil {
		if os.IsNotExist(err) {
			fmt.Printf(" ! Input file '%s' does not exist!\n", inputPath)
			fmt.Println(" ! Please provide a TWRP image and try again.")
		} else {
			checkMsg(err, "verifying file")
		}

		os.Exit(2)
	}

	if fInfo.IsDir() {
		fmt.Println(" ! Input is a directory!")
		fmt.Println(" ! Please provide a TWRP image file.")
		os.Exit(2)
	} else if fInfo.Size() < MinImageSize {
		fmt.Println(" ! Input is too small!")
		fmt.Printf(" ! Are you sure '%s' is a valid TWRP image?\n", fInfo.Name())
		os.Exit(2)
	}

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
	// Preserve /data/media
	replace(&ramdisk, "\x00/media\x00", "\x00/.twrp\x00")
	// Change text in Backup screen for English
	replace(&ramdisk, "Data (excl. storage)", "Data (incl. storage)")
	// Change orange warning text when backing up for English
	replace(&ramdisk, "Backups of {1} do not include any files in internal storage such as pictures or downloads.", "Backups of {1} include files in internal storage such as pictures and downloads.          ")

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
