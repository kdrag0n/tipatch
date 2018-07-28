package main

import (
	"fmt"
	"os"
	"tipatch"

	"github.com/hashicorp/errwrap"
)

func checkWrap(err error) {
	if err != nil {
		wrapped := err.(errwrap.Wrapper).WrappedErrors()
		fmt.Printf(" ! Error %s!\n", wrapped[0].Error())
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
	ramdisk, cMode, err := tipatch.ExtractRamdisk(image.Ramdisk)
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

	err = image.WriteHeader(out)
	checkMsg(err, "writing output file header")
	err = image.WriteData(out)
	checkMsg(err, "writing output file data")

	fmt.Printf(" - Finished! Output is '%s'.\n", outputPath)
}
