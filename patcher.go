package tipatch

import "go4.org/bytereplacer"

// PatchRamdisk applies Tipatch patches on the provided ramdisk.
func PatchRamdisk(ramdisk []byte) []byte {
	replacer := bytereplacer.New(
		// Preserve /data/media
		"\x00/media\x00", "\x00/.twrp\x00",
		// Change text in Backup screen for English
		"Data (excl. storage)", "Data (incl. storage)",
		// Change orange warning text when backing up for English
		"Backups of {1} do not include any files in internal storage such as pictures or downloads.", "Backups of {1} include files in internal storage such as pictures and downloads.          ",
	)

	return replacer.Replace(ramdisk)
}
