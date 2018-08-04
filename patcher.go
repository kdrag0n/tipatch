package tipatch

import (
	"fmt"
	"go4.org/bytereplacer"
)

// Replacement directions
const (
	ReplNormal = iota
	ReplReverse
)

type replList struct {
	replacements []string
}

func newRepl(size int) *replList {
	return &replList{
		replacements: make([]string, 0, size * 2),
	}
}

func (r *replList) add(from string, to string, direction int) {
	if len(from) != len(to) {
		panic(fmt.Sprintf("replacement length %d != %d, from %s to %s", len(from), len(to), from, to))
	}

	var pair [2]string
	switch (direction) {
	case ReplNormal:
		pair[0] = from
		pair[1] = to
	case ReplReverse:
		pair[1] = to
		pair[0] = from
	default:
		panic(fmt.Sprintf("unknown direction for replacement: %d", direction))
	}

	r.replacements = append(r.replacements, pair[0], pair[1])
}

func (r *replList) create() *bytereplacer.Replacer {
	return bytereplacer.New(r.replacements...)
}

// PatchRamdisk applies Tipatch patches on the provided ramdisk.
func PatchRamdisk(ramdisk []byte, dir int) []byte {
	r := newRepl(3)

	// Preserve /data/media
	r.add("\x00/media\x00", "\x00/.twrp\x00", dir)

	// Change text in Backup screen for English
	r.add("Data (excl. storage)", "Data (incl. storage)", dir)

	// Change orange warning text when backing up for English
	r.add("Backups of {1} do not include any files in internal storage such as pictures or downloads.",
		  "Backups of {1} include files in internal storage such as pictures and downloads.          ",
		  dir)

	return r.create().Replace(ramdisk)
}
