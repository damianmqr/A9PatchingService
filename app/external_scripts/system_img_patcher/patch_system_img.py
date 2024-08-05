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


def generate_pattern_smali_code(property_name, property_value, pattern_seq, pattern_loop, do_open=False):
    init_rc_lines = [
        f'on property:{property_name}={property_value}\n'
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
        '    const-string {reg2}, "' + property_value + '"\n',
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
        property_value = "1",
        pattern_seq = [
            "0x04", "0x04", "0x04", "0x04", "0x04", "0x05", "0x05", "0x05",
        ],
        pattern_loop = [
            "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e",
        ],
        do_open = True
    )

    notification_pattern_init, notification_pattern_smali = generate_pattern_smali_code(
        property_name = "sys.linevibrator_on",
        property_value = "2",
        pattern_seq = [
            "0x04", "0x05", "0x04", "0x04", "0x04", "0x05", "0x05", "0x05"
        ],
        pattern_loop = [
            "0x05", "0x05", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e"
        ],
        do_open = False
    )

    touch_pattern_init, touch_pattern_smali = generate_pattern_smali_code(
        property_name = "sys.linevibrator_on",
        property_value = "3",
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

override_battery_saver = {
    "m?[Ee]nableNightMode": "0x0",
    "m?[Dd]isable[Aa][Oo][Dd]": "0x0",
}

def patch_battery_saver(smali_file_path):
    logging.info("Patching Battery Saver...")

    with open(smali_file_path, 'r') as file:
        contents = file.read()
    for k, v in override_battery_saver.items():
        iget_pattern = re.compile(
            rf'iget-boolean (\w+), (\w+), (L[\w/]+/[\w]*BatterySaver[\w\$]*Policy[\w\$]*;->{k}:Z)'
        )

        def replace_iget(match):
            first_register = match.group(1)
            full_class_path = match.group(3)
            return f'const/4 {first_register}, {v}\n'

        iput_pattern = re.compile(
            rf'iput-boolean (\w+), (\w+), (L[\w/]+/BatterySaverPolicy\$Policy;->{k}:Z)'
        )

        def replace_iput(match):
            first_register = match.group(1)
            second_register = match.group(2)
            full_class_path = match.group(3)
            return f'const/4 {first_register}, {v}\n' \
                   f'    iput-boolean {first_register}, {second_register}, {full_class_path}\n'

        contents = iget_pattern.sub(replace_iget, contents)
        contents = iput_pattern.sub(replace_iput, contents)
        logging.info(f"Set {k} to {v}.")

    with open(smali_file_path, 'w') as file:
        file.write(contents)

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

def patch_brightness_setting(smali_file_path):
    logging.info("Patching BrightnessSetting...")

    with open(smali_file_path, 'r') as file:
        contents = file.readlines()

    new_contents = []

    inside_method = False
    inside_internal_method = False
    inside_constructor = False
    iput_pattern = re.compile(r'\s*iput (\w+), (\w+), (L[\w/]+;->mLeadDisplayId:I\s+)')
    found_field = False
    class_pattern = re.compile(r'\s*.class\s*[a-z]*\s+([a-zA-Z0-9\/\$]+;)\s*')
    class_name = None
    locals_pattern = re.compile(r'^(\s*\.locals\s+)(\d+)(\s*)$')
    locals_count = 0
    brightness_invoke_pattern = re.compile(r'\s*invoke-virtual\s*\{\s*([pv]\d+),\s*([pv]\d+),\s*([pv]\d+),\s*([pv]\d+).*\},\s*L.*;->onBrightnessChanged\(FFI\)V\s*')

    for line in contents:
        if class_name is None:
            class_match = class_pattern.match(line)
            if class_match:
                class_name = class_match.group(1)

        new_contents.append(line)
        if not found_field and ".field private" in line and ":F" in line:
            found_field = True
            new_contents.append('\n.field private mChangedBrightnessValue:F\n\n')

        if ".method constructor" in line:
            inside_constructor = True
        elif not inside_internal_method and ".method" in line and "updatePowerStateInternal()V" in line:
            inside_internal_method = True
        elif not inside_method and ".method" in line and "updatePowerState()V" in line:
            inside_method = True
        elif ".end method" in line:
            inside_constructor = False
            inside_internal_method = False
            inside_method = False
        elif inside_internal_method:
            brightness_invoke_match = brightness_invoke_pattern.match(line)
            if brightness_invoke_match:
                r1 = brightness_invoke_match.group(2)
                r2 = brightness_invoke_match.group(1)
                new_contents.append(f'    move-object/from16 {r2}, p0\n')
                new_contents.append(f'    iput {r1}, {r2}, {class_name}->mChangedBrightnessValue:F\n')
        elif inside_method:
            locals_match = locals_pattern.match(line)
            if locals_match:
                locals_count = int(locals_match.group(2))
                new_contents[-1]=f"{locals_match.group(1)}{locals_count + 6}{locals_match.group(3)}\n"
            if 'invoke' in line and ';->updatePowerStateInternal()V' in line:
                max_brightness_value = "0x44c80000"

                r1 = f'v{locals_count}'
                r2 = f'v{locals_count + 1}'
                r3 = f'v{locals_count + 2}'
                r4 = f'v{locals_count + 3}'
                r5 = f'v{locals_count + 4}'
                r6 = f'v{locals_count + 5}'

                new_contents.extend([
                    f'    iget-object {r1}, p0, {class_name}->mCdsi:Lcom/android/server/display/color/ColorDisplayService$ColorDisplayServiceInternal;\n',
                    f'    if-eqz {r1}, :cdsi_not_init\n',
                    f'    invoke-virtual {{{r1}}}, Lcom/android/server/display/color/ColorDisplayService$ColorDisplayServiceInternal;->getColorTemperature()F\n',
                    f'    move-result {r2}\n',

                    f'    iget {r3}, p0, {class_name}->mChangedBrightnessValue:F\n',
                    f'    const {r4}, {max_brightness_value}\n',
                    f'    mul-float {r3}, {r3}, {r4}\n', # r3 = mChangedBrightnessValue * MAX

                    f'    mul-float {r5}, {r3}, {r2}\n', # r5 = mChangedBrightnessValue * MAX * getColorTemperature()F
                    f'    float-to-int {r5}, {r5}\n'

                    f'    const {r6}, 0x3f800000\n', # 1.0f
                    f'    sub-float {r6}, {r6}, {r2}\n', # r6 = 1.0f - getColorTemperature()F
                    f'    mul-float {r6}, {r3}, {r6}\n', # r6 = mChangedBrightnessValue * MAX * (1.0f - getColorTemperature()F)
                    f'    float-to-int {r6}, {r6}\n'

                    f'    new-instance {r2}, Ljava/lang/StringBuilder;\n',
                    f'    invoke-direct {{{r2}}}, Ljava/lang/StringBuilder;-><init>()V\n',
                    f'    invoke-virtual {{{r2}, {r5}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;\n',
                    f'    invoke-virtual {{{r2}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n',
                    f'    move-result-object {r2}\n',
                    f'    const-string {r1}, "sys.linevibrator_short"\n',
                    f'    invoke-static {{{r1}, {r2}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V\n',

                    f'    new-instance {r2}, Ljava/lang/StringBuilder;\n',
                    f'    invoke-direct {{{r2}}}, Ljava/lang/StringBuilder;-><init>()V\n',
                    f'    invoke-virtual {{{r2}, {r6}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;\n',
                    f'    invoke-virtual {{{r2}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n',
                    f'    move-result-object {r2}\n',
                    f'    const-string {r1}, "sys.linevibrator_open"\n',
                    f'    invoke-static {{{r1}, {r2}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V\n'
                    f'    :cdsi_not_init\n',
                ])
        elif inside_constructor:
            iput_match = iput_pattern.match(line)
            if iput_match:
                r1, r2 = iput_match.group(1), iput_match.group(2)
                new_contents.append(f'    const/4 {r1}, 0x0\n')
                new_contents.append(f'    iput {r1}, {r2}, {class_name}->mChangedBrightnessValue:F\n')

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

def patch_color_display_service_internal(smali_file_path):
    logging.info("Patching DisplayService...")

    with open(smali_file_path, "r") as f:
        contents = f.readlines()

    class_pattern = re.compile(r'\s*.class\s*[a-z]*\s+([a-zA-Z0-9\/\$]+);\s*')
    class_name = None
    locals_pattern = re.compile(r'^(\s*\.locals\s+)(\d+)(\s*)$')
    locals_count = 0
    brightness_invoke_pattern = re.compile(r'\s*invoke-virtual\s*\{\s*([pv]\d+),\s*([pv]\d+),\s*([pv]\d+),\s*([pv]\d+).*\},\s*L.*;->onBrightnessChanged\(FFI\)V\s*')

    for line in contents:
        class_match = class_pattern.match(line)
        if class_match:
            class_name = class_match.group(1)
            break

    base_class_name = class_name.split('$')[0]
    contents.extend([
        '.method public getColorTemperature()F\n',
        '    .locals 4\n',
        f'    iget-object v0, p0, {class_name};->this$0:{base_class_name};\n',
        f'    invoke-static {{v0}}, {base_class_name};->-$$Nest$fgetmNightDisplayTintController({base_class_name};){base_class_name}$NightDisplayTintController;\n',
        '    move-result-object v0\n',
        f'    invoke-virtual {{v0}}, {base_class_name}$NightDisplayTintController;->isActivated()Z\n',
        '    move-result v1\n',
        '    if-nez v1, :cond_activated\n',
        '    const v0, 0x3f800000\n',
        '    goto :return_value\n',
        ':cond_activated\n',
        f'    invoke-virtual {{v0}}, {base_class_name}$NightDisplayTintController;->getColorTemperature()I\n',
        '    move-result v0\n',
        '    const v1, 0x457f2000\n',
        '    const v2, 0x45224000\n',
        '    int-to-float v3, v0\n',
        '    sub-float v3, v3, v2\n',
        '    sub-float v1, v1, v2\n',
        '    div-float v0, v3, v1\n',
        ':return_value\n',
        '    return v0\n',
        '.end method\n'
    ])


    with open(smali_file_path, "w") as f:
        f.writelines(contents)

    logging.info("Patching complete.")

def patch_color_display_service_night_controller(smali_file_path):
    logging.info("Patching DisplayService...")

    with open(smali_file_path, "r") as f:
        contents = f.readlines()
    new_contents = []

    class_pattern = re.compile(r'\s*.class\s*[a-z]*\s+([a-zA-Z0-9\/\$]+;)\s*')
    class_name = None
    locals_pattern = re.compile(r'^(\s*\.locals\s+)(\d+)(\s*)$')
    inside_method = False

    for line in contents:
        new_contents.append(line)
        class_match = class_pattern.match(line)
        if class_match:
            class_name = class_match.group(1)
        if ".method" in line and "onColorTemperatureChanged" in line:
            inside_method = True
        elif ".end method" in line:
            inside_method = False
        elif inside_method:
            locals_match = locals_pattern.match(line)
            if locals_match:
                locals_count = int(locals_match.group(2))
                new_contents[-1]=f"{locals_match.group(1)}{locals_count + 7}{locals_match.group(3)}\n"
                r0 = f"v{locals_count}"
                r1 = f"v{locals_count + 1}"
                r2 = f"v{locals_count + 2}"
                r3 = f"v{locals_count + 3}"
                r4 = f"v{locals_count + 4}"
                r5 = f"v{locals_count + 5}"
                r6 = f"v{locals_count + 6}"

                new_contents.extend([
                    f'    invoke-virtual {{p0}}, {class_name}->isActivated()Z\n',
                    f'    move-result {r0}\n',
                    f'    if-nez {r0}, :cond_activated_int\n',
                    f'    const {r1}, 0x3f800000\n',
                    '    goto :return_value_int\n',
                    ':cond_activated_int\n',
                    f'    move {r1}, p1\n',
                    f'    const {r0}, 0x457f2000\n',
                    f'    const {r2}, 0x45224000\n',
                    f'    int-to-float {r3}, {r1}\n',
                    f'    sub-float {r3}, {r3}, {r2}\n',
                    f'    sub-float {r0}, {r0}, {r2}\n',
                    f'    div-float {r1}, {r3}, {r0}\n',
                    ':return_value_int\n',

                    f'    const-string {r3}, "sys.linevibrator_short"\n',
                    f'    invoke-static {{{r3}}}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;\n',
                    f'    move-result-object {r3}\n',
                    f'    invoke-static {{{r3}}}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I\n',
                    f'    move-result {r3}\n',

                    f'    const-string {r4}, "sys.linevibrator_open"\n',
                    f'    invoke-static {{{r4}}}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;\n',
                    f'    move-result-object {r4}\n',
                    f'    invoke-static {{{r4}}}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I\n',
                    f'    move-result {r4}\n',

                    f'    add-int {r5}, {r3}, {r4}\n', # total_brightness = white + yellow

                    f'    int-to-float {r6}, {r5}\n',
                    f'    mul-float {r6}, {r6}, {r1}\n',
                    f'    float-to-int {r6}, {r6}\n',

                    f'    new-instance {r2}, Ljava/lang/StringBuilder;\n',
                    f'    invoke-direct {{{r2}}}, Ljava/lang/StringBuilder;-><init>()V\n',
                    f'    invoke-virtual {{{r2}, {r6}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;\n',
                    f'    invoke-virtual {{{r2}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n',
                    f'    move-result-object {r2}\n',
                    f'    const-string {r1}, "sys.linevibrator_short"\n',
                    f'    invoke-static {{{r1}, {r2}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V\n',

                    f'    new-instance {r2}, Ljava/lang/StringBuilder;\n',
                    f'    invoke-direct {{{r2}}}, Ljava/lang/StringBuilder;-><init>()V\n',
                    f'    sub-int {r5}, {r5}, {r6}\n'
                    f'    invoke-virtual {{{r2}, {r5}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;\n',
                    f'    invoke-virtual {{{r2}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n',
                    f'    move-result-object {r2}\n',
                    f'    const-string {r1}, "sys.linevibrator_open"\n',
                    f'    invoke-static {{{r1}, {r2}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V\n'
                ])

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

def patch_color_fade(smali_file_path):
    logging.info("Patching ColorFade.smali...")

    with open(smali_file_path, "r") as f:
        contents = f.readlines()
    new_contents = []
    inside_method = False

    for line in contents:
        new_contents.append(line)
        if ".end method" in line:
            inside_method = False
        elif ".method" in line and "draw(F)" in line:
            inside_method = True
        elif inside_method and ".locals" in line:
            new_contents.append("    const v0, 0x3e99999a\n")
            new_contents.append("    mul-float p1, p1, v0\n")
            new_contents.append("    const v0, 0x3f333333\n")
            new_contents.append("    add-float p1, p1, v0\n")

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)
    logging.info("Patching complete.")

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
            if os.path.isfile(target_file_overlay):
                logging.info("Removing old overlay.")
                os.remove(target_file_overlay)
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
    target_path = "d/system/priv-app/a9service.apk"
    if os.path.isfile("d/system/app/a9service.apk"):
        os.remove("d/system/app/a9service.apk")
    shutil.copy("../a9service.apk", target_path)
    os.chmod(target_path, 0o644)
    subprocess.run(["chown", "root:root", target_path])
    subprocess.run(["setfattr", "-n", "security.selinux", "-v", "u:object_r:system_file:s0", target_path])

def copy_ims_apk():
    logging.info("Adding the IMS apk...")
    target_path = "d/system/app/ims-caf-u.apk"
    if not os.path.isfile("../ims-caf-u.apk"):
        logging.warning("IMS apk not found, skipping")
        return
    properties["persist.vendor.vilte_support"] = "0"
    shutil.copy("../ims-caf-u.apk", target_path)
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
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/appops set com.lmqr.ha9_comp_service SYSTEM_ALERT_WINDOW allow\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chmod 444 /sys/class/leds/aw99703-bl-1/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chmod 444 /sys/class/leds/aw99703-bl-2/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chown root:root /sys/class/leds/aw99703-bl-1/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chown root:root /sys/class/leds/aw99703-bl-2/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chcon u:object_r:sysfs_leds:s0 /sys/class/backlight/aw99703-bl-1/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chcon u:object_r:sysfs_leds:s0 /sys/class/backlight/aw99703-bl-2/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/sh -c \"if [ ! -f /data/local/tmp/reset_carriers_done ]; then /system/bin/touch /data/local/tmp/reset_carriers_done; /system/bin/content delete --uri content://telephony/carriers/restore; fi\"\n"
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

        if not any("on property:sys.linevibrator_open=*" in line for line in lines):
            file.write("\non property:sys.linevibrator_open=*\n")
            file.write("    write /sys/class/backlight/aw99703-bl-1/brightness ${sys.linevibrator_open}\n\n")


        if not any("on property:sys.linevibrator_short=*" in line for line in lines):
            file.write("\non property:sys.linevibrator_short=*\n")
            file.write("    write /sys/class/backlight/aw99703-bl-2/brightness ${sys.linevibrator_short}\n\n")

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

    smali_files = find_smali(temp_dir, ["DisplayPowerController.*\.smali"])
    if len(smali_files) == 0:
        logging.error("DisplayBrightnessController.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_brightness_setting(smali_file)

    smali_files = find_smali(temp_dir, ["ColorDisplayService\$.*[Ii]nternal.*\.smali"])
    if len(smali_files) == 0:
        logging.error("ColorDisplayService.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_color_display_service_internal(smali_file)

    smali_files = find_smali(temp_dir, ["ColorDisplayService\$.*[Nn]ight[Dd]isplay.*\.smali"])
    if len(smali_files) == 0:
        logging.error("ColorDisplayService.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_color_display_service_night_controller(smali_file)

    smali_files = find_smali(temp_dir, ["ColorFade\.smali"])
    if len(smali_files) == 0:
        logging.error("ColorFade.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_color_fade(smali_file)

    logging.info("Repacking services.jar...")
    try:
        run_command(f"apktool b {temp_dir} -c -api 29 -o services.jar")
    except subprocess.CalledProcessError as e:
        logging.error(f"Failed to repack services.jar: {e.stderr}")
        exit_now(1)
    shutil.move("services.jar", jar_file)
    shutil.rmtree(temp_dir)

def patch_scrim_controller(smali_file_path):
    logging.info("Patching Scrim Controller...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    new_contents = []
    inside_method = False
    iget_pattern = re.compile(
        r'[\s]*iget-boolean (\w+), (\w+), (L[\w/]+/[\w\$]*;->[a-zA-Z]*(S|s)upportsAmbientMode:Z[\s]*)'
    )

    for line in old_contents:
        if ".end method" in line:
            inside_method = False
        if inside_method == False:
            iget_match = iget_pattern.match(line)
            if iget_match:
                new_contents.append(f"    const/4 {iget_match.group(1)}, 0x1\n")
            else:
                new_contents.append(line)
        if ".method" in line and "shouldFadeAwayWallpaper" in line:
            inside_method = True
            new_contents.append("    .locals 0\n")
            new_contents.append("    const/4 p0, 0x0\n")
            new_contents.append("    return p0\n")

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")


def patch_keyguard(smali_file_path):
    logging.info("Patching Keyguard...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    new_contents = []
    iget_pattern = re.compile(
        r'[\s]*iget (\w+), (\w+), (L[\w/]+/[\w\$]*;->[a-zA-Z]*(D|d)arkAmount:F[\s]*)'
    )

    for line in old_contents:
        iget_match = iget_pattern.match(line)
        if iget_match:
            new_contents.append(f"    const {iget_match.group(1)}, 0x0\n")
        else:
            new_contents.append(line)


    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

def patch_clock(smali_file_path):
    logging.info("Patching Clock...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    new_contents = []
    iget_pattern_1 = re.compile(
        r'([\s]*iget \w+, \w+, L[\w\/]+\/[\w\$]*;->)(m?[Dd]oz(e|ing))([cC]olor\w*:I[\s]*)'
    )
    iget_pattern_2 = re.compile(
        r'([\s]*iget \w+, \w+, L[\w\/]+\/[\w\$]*;->)(m?[Dd]oz(e|ing))([wW]eight\w*:I[\s]*)'
    )

    for line in old_contents:
        iget_match = iget_pattern_1.match(line)
        if iget_match:
            new_contents.append(f"{iget_match.group(1)}lockScreen{iget_match.group(4)}")
        else:
            iget_match = iget_pattern_2.match(line)
            if iget_match:
                new_contents.append(f"{iget_match.group(1)}lockScreen{iget_match.group(4)}")
            else:
                new_contents.append(line)

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

def patch_notification_doze(smali_file_path):
    logging.info("Patching Notification Doze...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    new_contents = []
    inside_method = False

    for line in old_contents:
        if ".end method" in line:
            inside_method = False
        if inside_method == False:
            new_contents.append(line)
        if ".method" in line and "updateGrayscale" in line:
            inside_method = True
            new_contents.append("    .locals 0\n")
            new_contents.append("    return-void\n")

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

def patch_doze_icon_helper(smali_file_path):
    logging.info("Patching Doze Icon...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    new_contents = []
    iget_pattern = re.compile(
        r'[\s]*iget (\w+), (\w+), (L[\w/]+/[\w\$]*;->m?(D|d)ozeAmount:F[\s]*)'
    )

    for line in old_contents:
        iget_match = iget_pattern.match(line)
        if iget_match:
            new_contents.append(f"    const {iget_match.group(1)}, 0x0\n")
        else:
            new_contents.append(line)


    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

def patch_kg_indication(smali_file_path):
    logging.info("Patching Keyguard Indication...")

    with open(smali_file_path, "r") as f:
        old_contents = f.readlines()

    new_contents = []
    iput_pattern = re.compile(
        r'[\s]*iput-object (\w+), (\w+), (L[\w/]+/[\w\$]*;->[\w]*[Tt]ext[Cc]olor[\w]*:[\w/]+ColorStateList;[\s]*)'
    )

    for line in old_contents:
        iput_match = iput_pattern.match(line)
        if iput_match:
            new_contents.append(f"    const {iput_match.group(1)}, 0xff444444\n")
            new_contents.append(f"    invoke-static {{{iput_match.group(1)}}}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;\n")
            new_contents.append(f"    move-result-object {iput_match.group(1)}\n")
        new_contents.append(line)

    with open(smali_file_path, "w") as f:
        f.writelines(new_contents)

    logging.info("Patching complete.")

values_per_scrim_enum = {
    "AOD": {
        "mFrontAlpha:F": "0x0",
        "mFrontTint:F": "0x0",
        "mAnimationDuration:J": "0x0",
    },
    "KEYGUARD": {
        "mBehindAlpha:F": "0x0",
        "mBehindTint:F": "0x0",
        "mAnimationDuration:J": "0x0",
        "mAnimateChange:Z": "0x0",
        "mNotifAlpha:F": "0X0",
    },
    "SHADE_LOCKED": {
        "mBehindAlpha:F": "0x0",
        "mBehindTint:F": "0x0",
        "mAnimateChange:Z": "0x0",
        "mNotifAlpha:F": "0X0",
    },
    "OFF": {
        "mBehindAlpha:F": "0x0",
        "mBehindTint:F": "0x0",
        "mAnimationDuration:J": "0x0",
        "mAnimateChange:Z": "0x0",
        "mNotifAlpha:F": "0X0",
        "mFrontAlpha:F": "0x0",
        "mFrontTint:F": "0x0",
    },
}

def patch_scrim_state(smali_file_path):
    logging.info("Patching Scrim State...")

    with open(smali_file_path, "r") as f:
        contents = f.readlines()

    in_constructor = False
    enum_name = None
    str_pattern = re.compile(
        r'\s*const-string \w+, "(\w+)"\s*'
    )
    for line in contents:
        if ".end method" in line:
            in_constructor = False
        if ".method" in line and "<init>()V" in line:
            in_constructor = True
        if in_constructor:
            str_match = str_pattern.match(line)
            if str_match:
                enum_name = str_match.group(1)
                break

    if enum_name not in values_per_scrim_enum:
        logging.info(f"Skipping {enum_name}.")
        return

    for k, v in values_per_scrim_enum[enum_name].items():
        logging.info(f"Setting {k} to {v}.")
        iput_pattern = re.compile(
            fr'[\s]*iput([\w-]*) (\w+), (\w+), (L[\w/]+/ScrimState[\w\$]*;->{k}[\s]*)'
        )
        for i in range(len(contents)):
            iput_match = iput_pattern.match(contents[i])
            if iput_match:
                contents[i] = f"    const{iput_match.group(1)} {iput_match.group(2)}, {v}\n{contents[i]}"
                logging.info("Successfully set.")

    with open(smali_file_path, "w") as f:
        f.writelines(contents)

    logging.info("Patching complete.")

def patch_systemui():
    jar_file = "d/system/system_ext/priv-app/SystemUI/SystemUI.apk"
    if not os.path.exists(jar_file):
        logging.error("SystemUI.apk not found!")
        exit_now(1)

    logging.info("Unpacking SystemUI.apk...")
    temp_dir = "systemui_temp"
    os.makedirs(temp_dir, exist_ok=True)
    run_command(f"apktool if {jar_file}")
    run_command(f"apktool d -r -f {jar_file} -o {temp_dir}")

    smali_files = find_smali(temp_dir, ["Scrim.*\.smali", "Keyguard.*\.smali"])
    if len(smali_files) == 0:
        logging.error("ScrimController.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_scrim_controller(smali_file)

    smali_files = find_smali(temp_dir, [".*[Kk]ey[Gg]uard.*\.smali"])
    if len(smali_files) == 0:
        logging.error("Keyguard.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_keyguard(smali_file)

    smali_files = find_smali(temp_dir, [".*Clock.*\.smali"])
    if len(smali_files) == 0:
        logging.error("ClockController.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_clock(smali_file)

    smali_files = find_smali(temp_dir, [".*Notification.*Doze.*\.smali"])
    if len(smali_files) == 0:
        logging.error("NotificationDozeHelper.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_notification_doze(smali_file)

    smali_files = find_smali(temp_dir, [".*Icon.*\.smali"])
    if len(smali_files) == 0:
        logging.error("Icon.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_doze_icon_helper(smali_file)

    smali_files = find_smali(temp_dir, [".*Key[Gg]uard.*[Ii]ndication.*\.smali"])
    if len(smali_files) == 0:
        logging.error("KeyguardIndicationController.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_kg_indication(smali_file)

    smali_files = find_smali(temp_dir, [".*ScrimState.*\.smali"])
    if len(smali_files) == 0:
        logging.error("ScrimState.smali not found!")
        exit_now(1)

    for smali_file in smali_files:
        patch_scrim_state(smali_file)

    logging.info("Repacking SystemUI.apk...")
    try:
        run_command(f"apktool b {temp_dir} -c -api 29 -o SystemUI.apk")
    except subprocess.CalledProcessError as e:
        logging.error(f"Failed to repack SystemUI.apk: {e.stderr}")
        exit_now(1)
    logging.info("Signing SystemUI.apk")
    try:
        run_command("apksigner sign --key ../platform.pk8 --cert ../platform.x509.pem SystemUI.apk")
    except subprocess.CalledProcessError as e:
        logging.error(f"Failed to sign SystemUI.apk: {e.stderr}")
        exit_now(1)
    shutil.move("SystemUI.apk", jar_file)
    shutil.rmtree(temp_dir)

def patch_framework_res():
    logging.info("Patching framework-res.apk")
    os.mkdir("framework_res_tmp")

    run_command("apktool if d/system/framework/framework-res.apk")
    run_command("apktool d -s -f d/system/framework/framework-res.apk -o framework_res_tmp")

    shutil.copy('../color_fade_frag.frag', 'framework_res_tmp/res/raw/color_fade_frag.frag')

    run_command("apktool b framework_res_tmp -o framework-res.apk")
    logging.info("Patching complete.")

    logging.info("Singing framework-res.apk")
    try:
        run_command("apksigner sign --key ../platform.pk8 --cert ../platform.x509.pem framework-res.apk")
    except subprocess.CalledProcessError as e:
        logging.error(f"Failed to sign framework-res.apk: {e.stderr}")
        exit_now(1)
    shutil.move("framework-res.apk", "d/system/framework/framework-res.apk")
    shutil.rmtree("framework_res_tmp")

    logging.info("Patching complete.")

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
        replace_treble_app()
        copy_hisense_overlay()
        copy_a9_eink_server()
        copy_a9service_apk()
        copy_ims_apk()
        update_build_prop()
        patch_framework_res()
        patch_systemui()
        patch_services_jar()
        update_vndk_rc()

    run_command("e2fsck -f -y s-ab-raw.img || true")
    run_command("resize2fs -M s-ab-raw.img")

    os.rename("s-ab-raw.img", "system_patched.img")
    shutil.move("system_patched.img", "../system_patched.img")

    logging.info("Process completed. The patched system image is system_patched.img")

if __name__ == "__main__":
    main()
