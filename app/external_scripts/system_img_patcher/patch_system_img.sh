#!/bin/bash
#Derived from https://github.com/Abdelhay-Ali/huawei-creator/tree/android-13
#https://github.com/Iceows/huawei-creator/blob/6e92d1cfc53cbd0ee825826a20317cf9f2b57806/run-huawei-ab.sh

#Usage:
#sudo bash patch_system_img.sh [/path/to/system.img]

set -ex
srcFile="$1"

cp "$srcFile" s-ab-raw.img

mkdir -p d
e2fsck -y -f s-ab-raw.img
resize2fs s-ab-raw.img 5000M
e2fsck -E unshare_blocks -y -f s-ab-raw.img
mount -o loop,rw s-ab-raw.img d
(
  cd d/system

  declare -A properties=(
    [ro.product.brand]="Crosscall"
    [ro.product.device]="qssi"
    [ro.product.manufacturer]="QUALCOMM"
    [ro.product.model]="HLTE556N"
    [ro.product.name]="HLTE556N"
    [ro.product.device]="HLTE556N"
    [ro.sf.lcd_density]="360"
    [ro.build.selinux]="1"
    [ro.build.tags]="release-keys"
    [ro.secure]="1"
    [ro.debuggable]="0"
    [ro.build.type]="user"
    [persist.sys.overlay.aod]="true"
  )

  for prop in "${!properties[@]}"; do
    value=${properties[$prop]}
    if grep -q "^$prop=" "build.prop"; then
      sed -i "s|^$prop=.*|$prop=$value|" "build.prop"
    else
      echo "$prop=$value" >>"build.prop"
    fi
  done

  TREBLE_APK="priv-app/TrebleApp/TrebleApp.apk"
  if [ -f "$TREBLE_APK" ]; then
    echo "TrebleApp.apk found. Replacing to disable hw overlays."
    cp -f "../../TrebleApp.apk" "$TREBLE_APK"

    if [ $? -eq 0 ]; then
      echo "Replaced successfully."
      chmod 644 "$TREBLE_APK"
      chown root:root "$TREBLE_APK"
      setfattr -n security.selinux -v u:object_r:system_file:s0 "$TREBLE_APK"
    else
      echo "WARNING: Couldn't replace TrebleApp"
    fi
  fi

  OVERLAY_DIRECTORY="product/overlay/"
  HISENSE_OVERLAY="treble-overlay-Hisense-HLTE556N.apk"
  TARGET_FILE_OVERLAY="${OVERLAY_DIRECTORY}${HISENSE_OVERLAY}"

  if [ -d "${OVERLAY_DIRECTORY}" ]; then
    if [ -f "../../${HISENSE_OVERLAY}" ]; then
      cp "../../${HISENSE_OVERLAY}" "${TARGET_FILE_OVERLAY}"

      if [ -f "${TARGET_FILE_OVERLAY}" ]; then

        chmod 644 "${TARGET_FILE_OVERLAY}"
        chown root:root "${TARGET_FILE_OVERLAY}"
        setfattr -n security.selinux -v u:object_r:system_file:s0 "${TARGET_FILE_OVERLAY}"
      fi
    fi
  fi

  cp ../../a9_eink_server bin/
  chmod +x bin/a9_eink_server
  chown root:2000 bin/a9_eink_server
  setfattr -n security.selinux -v u:object_r:phhsu_exec:s0 bin/a9_eink_server

  cp ../../a9service.apk app/
  chmod 644 app/a9service.apk
  chown root:root app/a9service.apk
  setfattr -n security.selinux -v u:object_r:system_file:s0 app/a9service.apk

  sed -i '1s|^|service a9_eink_server /system/bin/a9_eink_server\n    disabled\n\n|' etc/init/vndk.rc
  sed -i '/.*on property:sys.boot_completed=1/a\ \ \ \ exec_background u:r:phhsu_daemon:s0 root -- /system/bin/cmd overlay enable me.phh.treble.overlay.misc.aod_systemui' etc/init/vndk.rc
  sed -i '/.*on property:sys.boot_completed=1/a\ \ \ \ exec_background u:r:phhsu_daemon:s0 root -- /system/bin/service call SurfaceFlinger 1008 i32 1' etc/init/vndk.rc
  sed -i '/.*on property:sys.boot_completed=1/a\ \ \ \ start a9_eink_server' etc/init/vndk.rc
  sed -i '/.*on property:sys.boot_completed=1/a\ \ \ \ exec_background u:r:phhsu_daemon:s0 root -- /system/bin/settings put secure enabled_accessibility_services com.lmqr.ha9_comp_service/.A9AccessibilityService' etc/init/vndk.rc
  sed -i '/.*on property:sys.boot_completed=1/a\ \ \ \ exec_background u:r:phhsu_daemon:s0 root -- /system/bin/appops set com.lmqr.ha9_comp_service SYSTEM_ALERT_WINDOW allow' etc/init/vndk.rc
  cd ../..

)

sleep 1

umount d

e2fsck -f -y s-ab-raw.img || true
resize2fs -M s-ab-raw.img

mv s-ab-raw.img system_patched.img
