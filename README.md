Automatically patch system.img for use with Hisense A9, adding eink features support
For advanced users, check xda for pre-patched images.

To run the script on linux and patch your own treble based system.img you simply have to:

- Download a treble-droid based system.img you want to use
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

Default E-ink features Usage:

**Single Press E-Ink Button** - Refresh Screen

**Double Press E-Ink Button** - Open E-Ink Menu with settings for Per-App refresh modes.

These mappings can be easily changed in the E-Ink Settings app

## Licensing

### MIT License
Most of the project is licensed under the MIT License unless specified otherwise

The code and releases are provided “as is,” without any express or implied warranty of any kind including warranties of merchantability, non-infringement, title, or fitness for a particular purpose.

### Apache License 2.0
The file `HardwareGestureDetector.kt` includes code derived from AOSP. The original code is subject to the following license:

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

Modifications and additions to the original code are licensed under the MIT License
