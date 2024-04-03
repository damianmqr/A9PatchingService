Automatically patch system.img for use with Hisense A9, adding eink features support

To run the script on linux and patch your own treble based system.img you simply have to:

- Download the system.img you want to use (for example from Andy Yan's GSI builds https://sourceforge.net/projects/andyyan-gsi/files/)
- Download and extract a9_system_patcher.zip from https://github.com/damianmqr/a9_accessibility_service/releases/latest
- Go in terminal to the folder containing unzipped files
- Run `sudo bash patch_system_img.sh /path/to/system.img`
- system_patched.img will be saved in the same directory, ready to be flashed

Flashing the resulting file is done the same as any other system.img
**(make sure your bootloader is unlocked! and you have disabled vbmeta verification by reflashing with `fastboot flash vbmeta --disable-verity --disable-verification vbmeta.img`)**

- reboot to fastbootd using `adb reboot fastboot` (or any other method)
- run `fastboot flash system system_patched.img`
- after that's done, run `fastboot -w`
- run `fastboot reboot` and give it a few minutes

E-ink features Usage:

**Single Press E-Ink Button** - Refresh Screen

**Double Press E-Ink Button** - Open EInk Menu with settings for Per-App refresh modes.

The code and releases are provided “as is,” without any express or implied warranty of any kind including warranties of merchantability, non-infringement, title, or fitness for a particular purpose.
