package tipatch

// Boot image format constants
const (
	BootMagic         = "ANDROID!"
	BootMagicSize     = 8
	BootNameSize      = 16
	BootArgsSize      = 512
	BootExtraArgsSize = 1024
)

// BootMagicBytes is the image header magic number, in byte array form
var BootMagicBytes = [...]byte{'A', 'N', 'D', 'R', 'O', 'I', 'D', '!'}

// General constants
const (
	MinImageSize = 2097152 // 2 MiB
)

// Image represents the contents of a boot image.
type Image struct {
	Board     string
	Cmdline   string
	osVersion uint32

	base          uint32
	kernelOffset  uint32
	ramdiskOffset uint32
	secondOffset  uint32
	tagsOffset    uint32
	pageSize      uint32
	Kernel        []byte
	Ramdisk       []byte
	Second        []byte
	DeviceTree    []byte
}

// RawImage directly correlates to the Android boot image header.
type RawImage struct {
	// Android header magic
	Magic [BootMagicSize]byte

	// Size of the kernel in bytes
	KernelSize uint32
	// Kernel physical load address
	KernelAddr uint32

	// Size of the ramdisk in bytes
	RamdiskSize uint32
	// Ramdisk physical load address
	RamdiskAddr uint32

	// Size of the second stage bootloader in bytes
	SecondSize uint32
	// Second stage bootloader physical load address
	SecondAddr uint32

	// Kernel tags physical load address
	TagsAddr uint32
	// Flash page size
	PageSize uint32
	// Size of the device tree in bytes
	DtSize uint32

	/* OS version and security patch level
	 * For version A.B.C, patch level Y-M-D
	 * ver = A << 14 | B << 7 | C		 (7 bits for each ABC)
	 * lvl = ((Y - 2000) & 127) << 4 | M (7 bits for Y, 4 bits for M)
	 * os_version = ver < 11 | lvl */
	OSVersion uint32

	// Product/board name
	Board [BootNameSize]byte
	// Kernel command line
	Cmdline [BootArgsSize]byte

	// Timestamp/checksum/SHA-1/...
	ID [32]byte

	// Supplemental cmdline data for compatibility with older formats
	ExtraCmdline [BootExtraArgsSize]byte
}
