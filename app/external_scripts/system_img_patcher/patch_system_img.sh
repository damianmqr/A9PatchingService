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

	echo "ro.sf.lcd_density=360" >> build.prop

	if grep -q "^ro.product.model=" "build.prop"; then
      sed -i "s/^ro.product.model=.*/ro.product.model=A9/" build.prop
  else
      echo "ro.product.model=A9" >> build.prop
  fi

  if grep -q "^ro.product.brand=" "build.prop"; then
      sed -i "s/^ro.product.brand=.*/ro.product.brand=hisense/" build.prop
  else
      echo "ro.product.brand=hisense" >> build.prop
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
