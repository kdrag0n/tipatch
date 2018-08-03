package main

import (
	"fmt"
	"os"
	"strings"
	"tipatch"

	"github.com/hashicorp/errwrap"
)

func checkWrap(err error) {
	if err != nil {
		wrapped := err.(errwrap.Wrapper).WrappedErrors()
		err1 := wrapped[0].Error()
		if strings.ContainsRune(err1, ';') {
			err1 = err1[:strings.IndexByte(err1, ';')+1]
		}

		fmt.Printf(" ! Error %s!\n", err1)
		fmt.Printf(" ! %s\n", wrapped[1].Error())
		os.Exit(2)
	}
}

func patchImage(inputPath, outputPath string) {
	fmt.Println(" - Extracting image")
	in, err := os.Open(inputPath)
	checkMsg(err, "opening image for reading")
	defer in.Close()

	image, err := tipatch.UnpackImage(in)
	checkWrap(err)

	fmt.Println(" - Extracting ramdisk")
	cMode := tipatch.DetectCompressor(image.Ramdisk)
	ramdisk, err := tipatch.ExtractRamdisk(image.Ramdisk, cMode)
	checkWrap(err)

	fmt.Println(" - Patching ramdisk")
	ramdisk = tipatch.PatchRamdisk(ramdisk)

	fmt.Println(" - Compressing ramdisk")
	ramdisk, err = tipatch.CompressRamdisk(ramdisk, cMode)
	checkWrap(err)
	image.Ramdisk = ramdisk

	fmt.Println(" - Repacking & writing image")
	out, err := os.OpenFile(outputPath, os.O_WRONLY|os.O_TRUNC|os.O_CREATE, 0644)
	checkMsg(err, "creating output file")
	defer out.Close()

	err = image.WriteToFd(int(out.Fd()))
	checkMsg(err, "writing output file")

	fmt.Printf(" - Finished! Output is '%s'.\n", outputPath)
}
