package main

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"tipatch"

	"github.com/mattn/go-isatty"

	flag "github.com/spf13/pflag"
)

// General command-line inteface constants
const (
	LastTestedVersion = "3.2.2"
)

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
	var reverse bool

	flag.StringVarP(&inputPath, "input", "i", "", "Path to the TWRP image to patch.")
	flag.StringVarP(&outputPath, "output", "o", "", "Path to output patched image to.")
	flag.BoolVarP(&reverse, "revert", "r", false, "Revert a previously patched image.")

	fmt.Printf(`Tipatch by @kdrag0n
TWRP patcher for internal storage backup
Last tested with TWRP %s

`, LastTestedVersion)

	flag.ErrHelp = errors.New("")
	flag.Parse()

	interactive := isatty.IsTerminal(os.Stdout.Fd()) || isatty.IsCygwinTerminal(os.Stdout.Fd())
	interactivePath := false

	if inputPath == "" {
		if flag.NArg() > 0 {
			inputPath = flag.Arg(0)
		} else {
			fmt.Println("Usage: tipatch {-o output} [input]")
			flag.PrintDefaults()
			if interactive {
				defer func() {
					fmt.Print("\n\nPress any key to continue...")
					reader := bufio.NewReader(os.Stdin)
					reader.ReadRune()
				}()

				inputPath = cliGetInputPath()
				interactivePath = true
			}
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

	if !interactivePath {
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
		} else if fInfo.Size() < tipatch.MinImageSize {
			fmt.Println(" ! Input is too small!")
			fmt.Printf(" ! Are you sure '%s' is a valid TWRP image?\n", fInfo.Name())
			os.Exit(2)
		}
	}

	patchImage(inputPath, outputPath, reverse)
}
