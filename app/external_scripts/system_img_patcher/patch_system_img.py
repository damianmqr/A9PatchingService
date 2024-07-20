#!/usr/bin/env python
import os
import re
import sys
import subprocess
import shutil
import logging

properties = {
    # Phone information
    "ro.product.brand": "Crosscall",
    "ro.product.device": "HLTE556N",
    "ro.product.manufacturer": "QUALCOMM",
    "ro.product.model": "HLTE556N",
    "ro.product.name": "HLTE556N",
    "ro.sf.lcd_density": "360",
    # Fix root detection
    "ro.build.selinux": "1",
    "ro.build.tags": "release-keys",
    "ro.secure": "1",
    "ro.debuggable": "0",
    "ro.build.type": "user",
    # Enable AOD
    "persist.sys.overlay.aod": "true",
    # Disable blur
    "ro.launcher.blur.appLaunch": "0",
    "ro.surface_flinger.supports_background_blur": "0",
    "ro.sf.blurs_are_expensive": "1",
    "persist.sys.sf.disable_blurs": "1",
    # Disable animations
    "debug.sf.nobootanimation": "1",
    "persist.sys.rotation.animation": "0",
    "sys.disable_ext_animation": "1",
    # Recent apps
    "ro.recents.grid": "true",
}

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

class MountImage:
    """Context manager for mounting and unmounting a system image."""

    def __init__(self, image_path, mount_point):
        self.image_path = image_path
        self.mount_point = mount_point

    def __enter__(self):
        run_command(f'mount -o loop,rw "{self.image_path}" {self.mount_point}')
        logging.info(f"Mounted {self.image_path} to {self.mount_point}")
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        run_command(f'umount {self.mount_point}')
        logging.info(f"Unmounted {self.image_path} from {self.mount_point}")


def generate_pattern_smali_code(property_name, pattern_seq, pattern_loop, do_open=False):
    init_rc_lines = [
        f'on property:{property_name}=set\n'
    ]

    if not do_open:
        init_rc_lines.extend([
            '    write /sys/class/leds/vibrator/reg "0x13 0x00"\n'
        ])

    for i, val in enumerate(pattern_seq):
        if " " in val:
            init_rc_lines.append(f'    write /sys/class/leds/vibrator/seq "{val}"\n')
        else:
            init_rc_lines.append(f'    write /sys/class/leds/vibrator/seq "0x{i:02x} {val}"\n')

    for i, val in enumerate(pattern_loop):
        if " " in val:
            init_rc_lines.append(f'    write /sys/class/leds/vibrator/loop "{val}"\n')
        else:
            init_rc_lines.append(f'    write /sys/class/leds/vibrator/loop "0x{i:02x} {val}"\n')

    if do_open:
        init_rc_lines.extend([
            '    write /sys/class/leds/vibrator/reg "0x13 0x0f"\n'
        ])
    
    smali_code = [
        '    const-string {reg1}, "' + property_name + '"\n',
        '    const-string {reg2}, "set"\n',
        '    invoke-static {{{reg1}, {reg2}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V\n'
    ]
    
    return ''.join(init_rc_lines), ''.join(smali_code)


def run_command(command, check=True):
    logging.info(f"Running command: {command}")
    try:
        result = subprocess.run(command, shell=True, check=check, capture_output=True, text=True)
        logging.info(f"Command '{command}' executed successfully.")
        return result.stdout
    except subprocess.CalledProcessError as e:
        logging.error(f"Error executing command '{command}': {e.stderr}")
        logging.error(f"Output: {e.output}")
        raise


def exit_now(err_code):
    if err_code != 0:
        logging.error(f"Exiting with error code: {err_code}")
    sys.exit(err_code)


def safe_subprocess_run(command, raise_error=True):
    try:
        return subprocess.check_output(command, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        logging.error(f"Command '{e.cmd}' returned non-zero exit status {e.returncode}: {e.output}")
        if raise_error:
            raise
    except OSError as e:
        logging.error(f"OS error: {e.strerror} ({e.errno})")
        if raise_error:
            raise
    return False


def safe_copy(orig, dest):
    shutil.copyfile(orig, dest)
    try:
        shutil.copystat(orig, dest)
    except OSError:
        logging.warning("shutil.copystat has failed.")


def safe_file_delete(file_path):
    if os.path.exists(file_path):
        os.remove(file_path)


def find_smali(search_dir, wanted_patterns):
    patterns = [re.compile(pattern) for pattern in wanted_patterns]
    matching_files = []

    for root, dirs, files in os.walk(search_dir):
        for file in files:
            if any(pattern.match(file) for pattern in patterns):
                matching_files.append(os.path.join(root, file))

    return matching_files

def patch_vibrator_service(smali_file_path, init_file_path):
    logging.info("Patching Vibrator service...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    method_signature = None
    class_name = None
    base_dir = None
    new_contents = []

    for line in old_contents:
        if line.startswith(".class"):
            class_name = line.split()[-1].strip()
            base_dir = '/'.join(class_name.split('/')[:-1])
            break

    if not class_name or not base_dir:
        logging.error("Class definition not found!")
        exit_now(1)

    for line in old_contents:
        if ".method private startVibrationLocked" in line:
            method_signature = line.strip()
            new_contents.append(line.replace("startVibrationLocked", "originalStartVibrationLocked"))
        else:
            new_contents.append(line)

    if not method_signature:
        logging.error("Method signature not found!")
        exit_now(1)

    params_start = method_signature.index('(')
    params_end = method_signature.index(')')
    params = method_signature[params_start:params_end + 1]
    return_type_start = params_end + 1
    return_type = method_signature[return_type_start:].strip()

    ring_pattern_init, ring_pattern_smali = generate_pattern_smali_code(
        property_name = "sys.linevibrator_on",
        pattern_seq = [
            "0x04", "0x04", "0x04", "0x04", "0x04", "0x05", "0x05", "0x05",
        ],
        pattern_loop = [
            "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e",
        ],
        do_open = True
    )

    notification_pattern_init, notification_pattern_smali = generate_pattern_smali_code(
        property_name = "sys.linevibrator_short",
        pattern_seq = [
            "0x04", "0x05", "0x04", "0x04", "0x04", "0x05", "0x05", "0x05"
        ],
        pattern_loop = [
            "0x05", "0x05", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e"
        ],
        do_open = False
    )

    touch_pattern_init, touch_pattern_smali = generate_pattern_smali_code(
        property_name = "sys.linevibrator_type",
        pattern_seq = [
            "0x01", "0x00"
        ],
        pattern_loop = [
            "0x00", "0x00 0x00"
        ],
        do_open = False
    )

    new_method = f"""
{method_signature}
    .locals 5
    .prologue
    .line 0

    const-string v0, "VibratorManagerService"
    const-string v1, "Custom startVibrationLocked method called"

    invoke-static {{v0, v1}}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    iget-object v0, p1, {base_dir}/Vibration;->callerInfo:{base_dir}/Vibration$CallerInfo;

    iget-object v0, v0, {base_dir}/Vibration$CallerInfo;->attrs:Landroid/os/VibrationAttributes;

    invoke-virtual {{v0}}, Landroid/os/VibrationAttributes;->getUsage()I

    move-result v0

    const/16 v1, 0x0
    if-eq v0, v1, :usage_unknown
    const/16 v1, 0x11
    if-eq v0, v1, :usage_alarm
    const/16 v1, 0x21
    if-eq v0, v1, :usage_ringtone
    const/16 v1, 0x31
    if-eq v0, v1, :usage_notification
    const/16 v1, 0x41
    if-eq v0, v1, :usage_communication_request
    const/16 v1, 0x12
    if-eq v0, v1, :usage_touch
    const/16 v1, 0x22
    if-eq v0, v1, :usage_physical_emulation
    const/16 v1, 0x32
    if-eq v0, v1, :usage_hardware_feedback
    const/16 v1, 0x42
    if-eq v0, v1, :usage_accessibility

    :usage_unknown
    :usage_alarm
    :usage_ringtone
    :usage_communication_request
    
    :try_start_ring
{ring_pattern_smali.format(reg1='v0', reg2='v1')}

    :try_end_ring
    .catchall {{:try_start_ring .. :try_end_ring}} :catch_all_usage

    :usage_notification

    :try_start_notif
{notification_pattern_smali.format(reg1='v0', reg2='v1')}

    :try_end_notif
    .catchall {{:try_start_notif .. :try_end_notif}} :catch_all_usage

    goto :usage_end

    :usage_touch
    :usage_physical_emulation
    :usage_hardware_feedback
    :usage_accessibility
    :try_start_touch
{touch_pattern_smali.format(reg1='v0', reg2='v1')}

    :try_end_touch
    .catchall {{:try_start_touch .. :try_end_touch}} :catch_all_usage

    goto :usage_end

    :catch_all_usage
    move-exception v0
    const-string v1, "Vibration Error"

    const-string v2, "Exception while writing to file"

    invoke-static {{v1, v2, v0}}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    :usage_end
    invoke-direct {{p0, p1}}, {class_name}->originalStartVibrationLocked{params}{return_type}

    move-result-object v0

    return-object v0
.end method
"""
    new_contents.append(new_method)

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    with open(init_file_path, "r") as file:
        init_lines = file.readlines()
    
    with open(init_file_path, "w") as file:
        for line in init_lines:
            file.write(line)

        file.write('\n')

        for init_property in [ring_pattern_init, notification_pattern_init, touch_pattern_init]:
            p_lines = init_property.splitlines(True)
            if p_lines[0] not in init_lines:
                file.writelines(p_lines)
                file.write('\n')

def patch_battery_saver(smali_file_path):
    logging.info("Patching Battery Saver...")

    with open(smali_file_path, 'r') as file:
        contents = file.read()

    iget_pattern = re.compile(
        r'iget-boolean (\w+), (\w+), (L[\w/]+/[\w]*BatterySaver[\w\$]*Policy[\w\$]*;->m?(E|e)nableNightMode:Z)'
    )

    def replace_iget(match):
        first_register = match.group(1)
        full_class_path = match.group(3)
        return f'const/4 {first_register}, 0x0\n'

    iput_pattern = re.compile(
        r'iput-boolean (\w+), (\w+), (L[\w/]+/BatterySaverPolicy\$Policy;->m?(E|e)nableNightMode:Z)'
    )

    def replace_iput(match):
        first_register = match.group(1)
        second_register = match.group(2)
        full_class_path = match.group(3)
        return f'const/4 {first_register}, 0x0\n' \
               f'    iput-boolean {first_register}, {second_register}, {full_class_path}\n'

    new_contents = iget_pattern.sub(replace_iget, contents)
    new_contents = iput_pattern.sub(replace_iput, new_contents)

    with open(smali_file_path, 'w') as file:
        file.write(new_contents)

    logging.info("Patching complete.")

def patch_vibration_scaler(smali_file_path):
    logging.info("Patching Vibrator scaler...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    new_contents = []

    pattern = re.compile(r'^(\.field private static final SCALE_FACTOR_[A-Z_]+:F = )(\d+\.\d+)f$')

    for line in old_contents:
        match = pattern.match(line)
        if match:
            prefix = match.group(1)
            value = float(match.group(2))
            new_value = value * value + 0.3
            new_line = f"{prefix}{new_value:.1f}f\n"
            new_contents.append(new_line)
        else:
            new_contents.append(line)

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

def update_build_prop():
    logging.info("Updating build.prop...")
    with open("d/system/build.prop", "r") as file:
        lines = file.readlines()

    with open("d/system/build.prop", "w") as file:
        for line in lines:
            key = line.split("=")[0]
            if key in properties:
                file.write(f"{key}={properties[key]}\n")
            else:
                file.write(line)

        for key, value in properties.items():
            if key not in set([line.split("=")[0] for line in lines]):
                file.write(f"{key}={value}\n")

def replace_treble_app():
    logging.info("Replacing TrebleApp...")
    treble_apk = "d/system/priv-app/TrebleApp/TrebleApp.apk"
    if os.path.isfile(treble_apk):
        shutil.copyfile("../TrebleApp.apk", treble_apk)
        os.chmod(treble_apk, 0o644)
        subprocess.run(["chown", "root:root", treble_apk])
        subprocess.run(["setfattr", "-n", "security.selinux", "-v", "u:object_r:system_file:s0", treble_apk])

def copy_hisense_overlay():
    overlay_directory = "d/system/product/overlay/"
    hisense_overlay = "../treble-overlay-Hisense-HLTE556N.apk"
    target_file_overlay = os.path.join(overlay_directory, hisense_overlay)

    if os.path.isdir(overlay_directory):
        if os.path.isfile(f"{hisense_overlay}"):
            logging.info("Adding overlay.")
            shutil.copy(f"{hisense_overlay}", target_file_overlay)
            os.chmod(target_file_overlay, 0o644)
            subprocess.run(["chown", "root:root", target_file_overlay])
            subprocess.run(["setfattr", "-n", "security.selinux", "-v", "u:object_r:system_file:s0", target_file_overlay])

def copy_a9_eink_server():
    logging.info("Adding the E-Ink server...")
    target_path = "d/system/bin/a9_eink_server"
    shutil.copy("../a9_eink_server", target_path)
    os.chmod(target_path, 0o755)
    subprocess.run(["chown", "root:2000", target_path])
    subprocess.run(["setfattr", "-n", "security.selinux", "-v", "u:object_r:phhsu_exec:s0", target_path])

def copy_a9service_apk():
    logging.info("Adding the E-Ink accessibility service...")
    target_path = "d/system/app/a9service.apk"
    shutil.copy("../a9service.apk", target_path)
    os.chmod(target_path, 0o644)
    subprocess.run(["chown", "root:root", target_path])
    subprocess.run(["setfattr", "-n", "security.selinux", "-v", "u:object_r:system_file:s0", target_path])

def update_vndk_rc():
    logging.info("Updating vndk init script...")
    vndk_rc_path = "d/system/etc/init/vndk.rc"
    with open(vndk_rc_path, "r") as file:
        lines = file.readlines()

    service_entries = [
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/cmd overlay enable me.phh.treble.overlay.misc.aod_systemui\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/service call SurfaceFlinger 1008 i32 1\n",
        "    start a9_eink_server\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/settings put secure enabled_accessibility_services com.lmqr.ha9_comp_service/.A9AccessibilityService\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/appops set com.lmqr.ha9_comp_service SYSTEM_ALERT_WINDOW allow\n"
    ]

    found_boot_completed = False

    with open(vndk_rc_path, "w") as file:
        if not any("service a9_eink_server /system/bin/a9_eink_server" in line for line in lines):
            file.write("service a9_eink_server /system/bin/a9_eink_server\n    disabled\n\n")
        
        for line in lines:
            file.write(line)
            if not found_boot_completed and "on property:sys.boot_completed=1" in line:
                found_boot_completed = True
                for entry in service_entries:
                    if entry not in lines:
                        file.write(entry)

def patch_services_jar():
    jar_file = "d/system/framework/services.jar"
    if not os.path.exists(jar_file):
        logging.error("services.jar not found!")
        exit_now(1)

    logging.info("Unpacking services.jar...")
    temp_dir = "services_temp"
    os.makedirs(temp_dir, exist_ok=True)
    run_command(f"apktool d -r -f {jar_file} -o {temp_dir}")

    smali_files = find_smali(temp_dir, ["VibratorManagerService.smali", "VibratorService.smali"])
    if len(smali_files) == 0:
        logging.error("VibratorManagerService.smali or VibratorService.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_vibrator_service(smali_file, "d/system/etc/init/vndk.rc")

    smali_files = find_smali(temp_dir, ["VibrationScaler.smali"])
    if len(smali_files) == 0:
        logging.error("VibratorScaler.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_vibration_scaler(smali_file)
    
    smali_files = find_smali(temp_dir, ['^BatterySaverPolicy(\$.*|)\.smali$'])
    if len(smali_files) == 0:
        logging.error("BatterySaverPolicy.smali not found!")
        exit_now(1)
    
    for smali_file in smali_files:
        patch_battery_saver(smali_file)

    logging.info("Repacking services.jar...")
    try:
        run_command(f"apktool b {temp_dir} -c -api 31 -o services.jar")
    except subprocess.CalledProcessError as e:
        logging.error(f"Failed to repack services.jar: {e.stderr}")
        exit_now(1)
    shutil.move("services.jar", jar_file)
    shutil.rmtree(temp_dir)

def main():
    if len(sys.argv) != 2:
        logging.error("Usage: sudo python patch_system_img.py [/path/to/system.img]")
        exit_now(1)

    src_file = os.path.abspath(sys.argv[1])
    if not os.path.exists(src_file):
        logging.error(f"File not found: {src_file}")
        exit_now(1)

    if not os.path.exists("TMP"):
        os.makedirs("TMP")
    os.chdir("TMP")

    shutil.copy(src_file, 's-ab-raw.img')
    os.makedirs('d', exist_ok=True)
    run_command('e2fsck -y -f s-ab-raw.img')
    run_command('resize2fs s-ab-raw.img 5000M')
    run_command('e2fsck -E unshare_blocks -y -f s-ab-raw.img')

    with MountImage('s-ab-raw.img', 'd'):
        update_build_prop()
        replace_treble_app()
        copy_hisense_overlay()
        copy_a9_eink_server()
        copy_a9service_apk()
        patch_services_jar()
        update_vndk_rc()

    run_command("e2fsck -f -y s-ab-raw.img || true")
    run_command("resize2fs -M s-ab-raw.img")

    os.rename("s-ab-raw.img", "system_patched.img")
    shutil.move("system_patched.img", "../system_patched.img")

    logging.info("Process completed. The patched system image is system_patched.img")

if __name__ == "__main__":
    main()
