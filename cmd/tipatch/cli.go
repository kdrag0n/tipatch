package main

import (
	"bufio"
	"fmt"
	"os"
	"strings"
	"tipatch"

	"github.com/tgulacsi/wrap"
)

const (
	cliWelcome = `
Please drag and drop the TWRP image you want to patch
into this window.

After you drop the file, press the [Enter] key to continue.

> `
	cliStatError = `
An error occurred verifying that file:
"%s"

Try dragging and dropping a TWRP image you are able
to open.

> `
)

func cliPrompt(msg string) {
	var cols uint = 60
	wrapped := wrap.String(msg, cols)

	fmt.Printf(`
%s

> `, wrapped)
}

func cliPromptDrag(msg string) {
	cliPrompt(msg + " Try dragging and dropping a TWRP image here.")
}

// Interactive CLI for getting input path
func cliGetInputPath() (path string) {
	fmt.Print(cliWelcome)
	scanner := bufio.NewScanner(os.Stdin)

	for {
		if !scanner.Scan() {
			fmt.Println()
			os.Exit(2)
		}

		path = scanner.Text()
		path = strings.TrimSpace(path)

		if (strings.HasPrefix(path, "\"") && strings.HasSuffix(path, "\"")) || (strings.HasPrefix(path, "'") && strings.HasSuffix(path, "'")) {
			path = path[1 : len(path)-1]
		}

		if len(path) == 0 {
			cliPromptDrag("That wasn't the path to a file.")
			continue
		}

		fInfo, err := os.Stat(path)
		if err != nil {
			if os.IsNotExist(err) {
				cliPromptDrag("That file doesn't exist.")
			} else {
				fmt.Printf(cliStatError, err.Error())
			}

			continue
		}

		if fInfo.IsDir() {
			cliPromptDrag("That's a folder, not a file.")
			continue
		} else if fInfo.Size() < tipatch.MinImageSize {
			cliPromptDrag("That file is too small to be a TWRP image.")
			continue
		}

		break
	}

	fmt.Println()
	return
}
