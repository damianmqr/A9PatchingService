from smali_patcher import *
import sys
import re
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

def exit_now(err_code):
    if err_code != 0:
        logging.error(f"Exiting with error code: {err_code}")
    sys.exit(err_code)

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

keep_scale_count = 0
def patch_OverrideAnimatorScale(instruction):
    global keep_scale_count
    registers = instruction.get_n_free_registers(1)
    instruction.expand_before([
        f'const {registers[0]}, 0x3f800000',
        f'invoke-virtual {{{instruction.registers[0]}, {registers[0]}}}, {instruction.class_name}->overrideDurationScale(F)V',
    ])
    keep_scale_count += 1

def patch_services_jar():
    def add_pattern_to_initrc(property_name, property_value, pattern_seq, pattern_loop, do_open=False):
        with open("../d/system/etc/init/vndk.rc", "a") as init_file:
            init_file.write(f'\non property:{property_name}={property_value}\n')

            if not do_open:
                init_file.write('write /sys/class/leds/vibrator/reg "0x13 0x00"\n')

            for i, val in enumerate(pattern_seq):
                real_val = val if " " in val else f'0x{i:02x} {val}'
                init_file.write(f'write /sys/class/leds/vibrator/seq "{real_val}"\n')

            for i, val in enumerate(pattern_loop):
                real_val = val if " " in val else f'0x{i:02x} {val}'
                init_file.write(f'write /sys/class/leds/vibrator/loop "{real_val}"\n')

            if do_open:
                init_file.write('write /sys/class/leds/vibrator/reg "0x13 0x0f"\n')

    def get_smali_for_property_set(register1, register2, property_name, property_value):
            return [
                f'const-string {register1}, "{property_name}"',
                f'const-string {register2}, "{property_value}"',
                f'invoke-static {{{register1}, {register2}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',
            ]

    invokeVibrationLocked = None
    def extract_invokeVibrationLocked(instruction):
        nonlocal invokeVibrationLocked
        invokeVibrationLocked = instruction

    def patch_startVibrationLocked(method):
        add_pattern_to_initrc(
            "sys.linevibrator_on", "1",
            pattern_seq = ["0x04", "0x04", "0x04", "0x04", "0x04", "0x05", "0x05", "0x05"],
            pattern_loop = ["0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e"],
            do_open = True
        )
        add_pattern_to_initrc(
            "sys.linevibrator_on", "2",
            pattern_seq = ["0x04", "0x05", "0x04", "0x04", "0x04", "0x05", "0x05", "0x05"],
            pattern_loop = ["0x05", "0x05", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e", "0x0e"],
            do_open = False
        )
        add_pattern_to_initrc(
            "sys.linevibrator_on", "3",
            pattern_seq = ["0x01", "0x00"],
            pattern_loop = ["0x00", "0x00 0x00"],
            do_open=False
        )
        new_method = SmaliMethod(method.header, method.parent)
        new_method.name = "tempStartVibrationLocked"
        method.parent.items.append(('method', new_method))
        method.name = "originalStartVibrationLocked"
        new_method.add_instruction('.locals 5')
        for instruction in [
            f'iget-object v0, p1, {method.parent.base_dir}/Vibration;->callerInfo:{method.parent.base_dir}/Vibration$CallerInfo;',
            f'iget-object v0, v0, {method.parent.base_dir}/Vibration$CallerInfo;->attrs:Landroid/os/VibrationAttributes;',
            'invoke-virtual {v0}, Landroid/os/VibrationAttributes;->getUsage()I',
            'move-result v0',
            'const/16 v1, 0x0',
            'if-eq v0, v1, :usage_unknown',
            'const/16 v1, 0x11',
            'if-eq v0, v1, :usage_alarm',
            'const/16 v1, 0x21',
            'if-eq v0, v1, :usage_ringtone',
            'const/16 v1, 0x31',
            'if-eq v0, v1, :usage_notification',
            'const/16 v1, 0x41',
            'if-eq v0, v1, :usage_communication_request',
            'const/16 v1, 0x12',
            'if-eq v0, v1, :usage_touch',
            'const/16 v1, 0x22',
            'if-eq v0, v1, :usage_physical_emulation',
            'const/16 v1, 0x32',
            'if-eq v0, v1, :usage_hardware_feedback',
            'const/16 v1, 0x42',
            'if-eq v0, v1, :usage_accessibility',
            ':usage_unknown',
            ':usage_alarm',
            ':usage_ringtone',
            ':usage_communication_request',
            ':try_start_ring',
        ] + get_smali_for_property_set('v0', 'v1', 'sys.linevibrator_on', '1') + [
            ':try_end_ring',
            '.catchall {:try_start_ring .. :try_end_ring} :catch_all_usage',
            ':usage_notification',
            ':try_start_notif',
        ] + get_smali_for_property_set('v0', 'v1', 'sys.linevibrator_on', '1') + [
            ':try_end_notif',
            '.catchall {:try_start_notif .. :try_end_notif} :catch_all_usage',
            'goto :usage_end',
            ':usage_touch',
            ':usage_physical_emulation',
            ':usage_hardware_feedback',
            ':usage_accessibility',
            ':try_start_touch',
        ] + get_smali_for_property_set('v0', 'v1', 'sys.linevibrator_on', '2') + [
            ':try_end_touch',
            '.catchall {:try_start_touch .. :try_end_touch} :catch_all_usage',
            'goto :usage_end',
            ':catch_all_usage',
            'move-exception v0',
            'const-string v1, "Vibration Error"',
            'const-string v2, "Exception while writing to file"',
            'invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I',
            ':usage_end',
            f'invoke{invokeVibrationLocked.modifier} {{p0, p1}}, {method.parent.class_name}->{method.name}({method.parameters}){method.return_type}',
            'move-result-object v0',
            'return-object v0',
        ]:
            new_method.add_instruction(instruction)


    def patch_updatePowerStateInternal(instruction):
        max_brightness_value = "0x44fa0000"
        registers = instruction.next.get_n_free_registers(6)
        instruction.expand_after([
            f'iget-object {registers[0]}, p0, {instruction.class_name}->mCdsi:Lcom/android/server/display/color/ColorDisplayService$ColorDisplayServiceInternal;',
            f'if-eqz {registers[0]}, :cdsi_not_init',
            f'invoke-virtual {{{registers[0]}}}, Lcom/android/server/display/color/ColorDisplayService$ColorDisplayServiceInternal;->getColorTemperature()F',
            f'move-result {registers[1]}',

            f'iget {registers[2]}, p0, {instruction.class_name}->mChangedBrightnessValue:F',
            f'const-string {registers[3]}, "sys.linevibrator_touch"',
            f'invoke-static {{{registers[3]}}}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
            f'move-result-object {registers[3]}',
            ":try_number_type_start",
            f"invoke-static {{{registers[3]}}}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I",
            f"move-result {registers[3]}",
            ":try_number_type_end",
            ".catchall {:try_number_type_start .. :try_number_type_end} :catch_wrong_number",
            f"if-gez {registers[3]}, :skip_zero",
            f"const {registers[3]}, 0x0",
            ":skip_zero",
            f"const {registers[4]}, {hex(2201)}",
            f"if-ge {registers[3]}, {registers[4]}, :catch_wrong_number",
            f'int-to-float {registers[3]}, {registers[3]}',
            'goto :on_valid_number',
            ':catch_wrong_number',
            f'const {registers[3]}, {max_brightness_value}',
            ':on_valid_number',
            f'mul-float {registers[2]}, {registers[2]}, {registers[3]}', # registers[2] = mChangedBrightnessValue * MAX

            f'mul-float {registers[4]}, {registers[2]}, {registers[1]}', # registers[4] = mChangedBrightnessValue * MAX * getColorTemperature()F
            f'float-to-int {registers[4]}, {registers[4]}',

            f'const {registers[5]}, 0x3f800000', # 1.0f
            f'sub-float {registers[5]}, {registers[5]}, {registers[1]}', # registers[5] = 1.0f - getColorTemperature()F
            f'mul-float {registers[5]}, {registers[2]}, {registers[5]}', # registers[5] = mChangedBrightnessValue * MAX * (1.0f - getColorTemperature()F)
            f'float-to-int {registers[5]}, {registers[5]}',

            f'new-instance {registers[1]}, Ljava/lang/StringBuilder;',
            f'invoke-direct {{{registers[1]}}}, Ljava/lang/StringBuilder;-><init>()V',
            f'invoke-virtual {{{registers[1]}, {registers[4]}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
            f'invoke-virtual {{{registers[1]}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
            f'move-result-object {registers[1]}',
            f'const-string {registers[0]}, "sys.linevibrator_short"',
            f'invoke-static {{{registers[0]}, {registers[1]}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',

            f'new-instance {registers[1]}, Ljava/lang/StringBuilder;',
            f'invoke-direct {{{registers[1]}}}, Ljava/lang/StringBuilder;-><init>()V',
            f'invoke-virtual {{{registers[1]}, {registers[5]}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
            f'invoke-virtual {{{registers[1]}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
            f'move-result-object {registers[1]}',
            f'const-string {registers[0]}, "sys.linevibrator_open"',
            f'invoke-static {{{registers[0]}, {registers[1]}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',
            f':cdsi_not_init',
        ])

    def patch_onColorTemperatureChanged(method):
        registers = method.first_instruction.next.get_n_free_registers(7)
        method.first_instruction.expand_after([
             f'invoke-virtual {{p0}}, {method.parent.class_name}->isActivated()Z',
             f'move-result {registers[0]}',
             f'if-nez {registers[0]}, :cond_activated_int',
             f'const {registers[1]}, 0x3f800000',
             'goto :return_value_int',
             ':cond_activated_int',
             f'move {registers[1]}, p1',
             f'const {registers[0]}, 0x457f2000',
             f'const {registers[2]}, 0x45224000',
             f'int-to-float {registers[3]}, {registers[1]}',
             f'sub-float {registers[3]}, {registers[3]}, {registers[2]}',
             f'sub-float {registers[0]}, {registers[0]}, {registers[2]}',
             f'div-float {registers[1]}, {registers[3]}, {registers[0]}',
             ':return_value_int',

             f'const-string {registers[3]}, "sys.linevibrator_short"',
             f'invoke-static {{{registers[3]}}}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
             f'move-result-object {registers[3]}',
             f'invoke-static {{{registers[3]}}}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I',
             f'move-result {registers[3]}',

             f'const-string {registers[4]}, "sys.linevibrator_open"',
             f'invoke-static {{{registers[4]}}}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
             f'move-result-object {registers[4]}',
             f'invoke-static {{{registers[4]}}}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I',
             f'move-result {registers[4]}',

             f'add-int {registers[5]}, {registers[3]}, {registers[4]}', # total_brightness = white + yellow

             f'int-to-float {registers[6]}, {registers[5]}',
             f'mul-float {registers[6]}, {registers[6]}, {registers[1]}',
             f'float-to-int {registers[6]}, {registers[6]}',

             f'new-instance {registers[2]}, Ljava/lang/StringBuilder;',
             f'invoke-direct {{{registers[2]}}}, Ljava/lang/StringBuilder;-><init>()V',
             f'invoke-virtual {{{registers[2]}, {registers[6]}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
             f'invoke-virtual {{{registers[2]}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
             f'move-result-object {registers[2]}',
             f'const-string {registers[1]}, "sys.linevibrator_short"',
             f'invoke-static {{{registers[1]}, {registers[2]}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',

             f'new-instance {registers[2]}, Ljava/lang/StringBuilder;',
             f'invoke-direct {{{registers[2]}}}, Ljava/lang/StringBuilder;-><init>()V',
             f'sub-int {registers[5]}, {registers[5]}, {registers[6]}',
             f'invoke-virtual {{{registers[2]}, {registers[5]}}}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
             f'invoke-virtual {{{registers[2]}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
             f'move-result-object {registers[2]}',
             f'const-string {registers[1]}, "sys.linevibrator_open"',
             f'invoke-static {{{registers[1]}, {registers[2]}}}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',
        ])

    def patch_ColorDisplayServiceInternal(smali_file):
        base_class = smali_file.smali_class.class_name.split('$')[0]
        smali_file.smali_class.items.append((
            'method',
            SmaliMethod(
                '.method public getColorTemperature()F',
                smali_file.smali_class,
                initial_instructions = [
                    '.locals 4',
                    f'iget-object v0, p0, {smali_file.smali_class.class_name}->this$0:{base_class};',
                    f'invoke-static {{v0}}, {base_class};->-$$Nest$fgetmNightDisplayTintController({base_class};){base_class}$NightDisplayTintController;',
                    'move-result-object v0',
                    f'invoke-virtual {{v0}}, {base_class}$NightDisplayTintController;->isActivated()Z',
                    'move-result v1',
                    'if-nez v1, :cond_activated',
                    'const v0, 0x3f800000',
                    'goto :return_value',
                    ':cond_activated',
                    f'invoke-virtual {{v0}}, {base_class}$NightDisplayTintController;->getColorTemperature()I',
                    'move-result v0',
                    'const v1, 0x457f2000',
                    'const v2, 0x45224000',
                    'int-to-float v3, v0',
                    'sub-float v3, v3, v2',
                    'sub-float v1, v1, v2',
                    'div-float v0, v3, v1',
                    ':return_value',
                    'return v0',
                ]
            )
        ))
    ev_pattern = re.compile(r"[^{]{([^}{]*)}[^}]")
    def format_eval(string, **kwargs):
        all_matches = ev_pattern.findall(string)
        string = string.replace("{{", "{").replace("}}", "}")
        for match in all_matches:
            string = string.replace(f'{{{match}}}', str(eval(match, {'abs': abs, 'round': round, 'pow': pow, 'int': int, 'float': float, 'max': max, 'min': min, 'sum': sum}, kwargs)))
        return string

    def get_shader_variant(shader_name, centered=False, opacity = 0.91, bg_opacity = 0.0, mix_color = 0.0):
        with open(f'../shaders/{shader_name}.frag', 'r') as f:
            content = f.read()
        radius = 0.25 if centered else 0.12
        center_x = 0.5 if centered else round(radius + 0.05, 3)
        center_y = 1.0 if centered else round(1.9 - radius, 3)
        op_name = str(opacity).replace('.', '_')
        return format_eval(content, center_x = center_x, center_y = center_y, opacity = opacity, radius = radius, bg_opacity = bg_opacity, mix_color = mix_color, bg_mix_color = 1.0 - mix_color)

    shaders = [get_shader_variant(name, centered, opacity, bg_opacity, mix_color) for name in ["color_fade_frag", "color_fade_frag_pause"] for centered in [True, False] for opacity in [0.91, 0.67, 0.45] for bg_opacity in [1.0, 0.6, 0.2, 0.0] for mix_color in [1.0, 0.0] ]
    def find_resource_id(public_xml_path, resource_name, resource_type):
        tree = ET.parse(public_xml_path)
        root = tree.getroot()

        for public_elem in root.findall('public'):
            if public_elem.attrib.get('name') == resource_name and public_elem.attrib.get('type') == resource_type:
                return public_elem.attrib.get('id')

        return None

    def patch_ColorFadeInit(method):
        registers = method.first_instruction.next.get_n_free_registers(3)
        static_shader_list_smali = [
           f"const {registers[0]}, {hex(len(shaders))}",
           f"new-array {registers[1]}, {registers[0]}, [Ljava/lang/String;",
        ]
        for i, shader in enumerate(shaders):
            escaped_shader = "\"" + repr(shader)[1:-1] + "\""
            static_shader_list_smali.extend([
                f"const-string {registers[0]}, {escaped_shader}",
                f"const {registers[2]}, {hex(i)}",
                f"aput-object {registers[0]}, {registers[1]}, {registers[2]}",
            ])
        static_shader_list_smali.append(f"sput-object {registers[1]}, {method.parent.class_name}->SHADER_LIST:[Ljava/lang/String;")
        method.first_instruction.expand_after(static_shader_list_smali)

    def patch_readFile(method):
        framework_res_apk_path = None
        for root, dirs, files in os.walk("../d"):
            for file in files:
                if file == 'framework-res.apk':
                    framework_res_apk_path =  os.path.join(root, file)

        if framework_res_apk_path is None:
            logging.error("framework-res.apk not found.")
            exit_now(1)
        try:
            output_folder = 'decompiled_framework_res'
            os.makedirs(output_folder, exist_ok=True)
            subprocess.run(['apktool', 'd', "--no-src", framework_res_apk_path, '-o', output_folder, '-f'], check=True)

            public_xml_path = os.path.join(output_folder, 'res', 'values', 'public.xml')

            resource_id = find_resource_id(public_xml_path, 'color_fade_frag', 'raw')
            if not resource_id:
                logging.error("Shader resource not found.")
                exit_now(1)
        except subprocess.CalledProcessError:
            resource_id = "0x01100002"
            logging.warning("Couldn't extract framework-res! Hardcoding resource id for shader to 0x01100002, this may break static AoD!")
        registers = method.first_instruction.get_n_free_registers(2)
        method.first_instruction.expand_after([
            f"const {registers[0]}, {resource_id}",
            f"if-ne p2, {registers[0]}, :other_resource",
            f"const-string {registers[0]}, \"sys.linevibrator_type\"",
            f"invoke-static {{{registers[0]}}}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;",
            f"move-result-object {registers[0]}",
            ":try_number_type_start",
            f"invoke-static {{{registers[0]}}}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I",
            f"move-result {registers[0]}",
            ":try_number_type_end",
            ".catchall {:try_number_type_start .. :try_number_type_end} :catch_number_type",
            f"if-ltz {registers[0]}, :catch_number_type",
            f"const {registers[1]}, {hex(len(shaders))}",
            f"if-ge {registers[0]}, {registers[1]}, :catch_number_type",
            f"sget-object {registers[1]}, {method.parent.class_name}->SHADER_LIST:[Ljava/lang/String;",
            f"aget-object {registers[0]}, {registers[1]}, {registers[0]}",
            f"return-object {registers[0]}",
            ":catch_number_type",
            f"sget-object {registers[1]}, {method.parent.class_name}->SHADER_LIST:[Ljava/lang/String;",
            f"const {registers[0]}, 0x0",
            f"aget-object {registers[0]}, {registers[1]}, {registers[0]}",
            f"return-object {registers[0]}",
            ":other_resource",
        ])

    def patch_ColorFade(smali_file):
        smali_class = smali_file.smali_class
        smali_class.add_field(".field private static final SHADER_LIST:[Ljava/lang/String;")
        if not smali_class.has_method(method = MethodDetails(name="<clinit>")):
            smali_class.items.append((
                'method',
                SmaliMethod(
                    '.method static constructor <clinit>()V',
                    smali_class,
                    ['.locals 3', 'return-void'],
                )
            ))
        smali_class.for_method(
            method = MethodDetails(name = "<clinit>", return_type = "V"),
            action = patch_ColorFadeInit,
        )
        smali_class.for_method(
            method = MethodDetails(name = "readFile", return_type = "Ljava/lang/String;"),
            action = patch_readFile,
        )

    def patch_GetBrightness(instruction):
        value = "0x0" if "Adjustment" in instruction.method else "0x3f800000"
        instruction = instruction.next_known()
        if instruction.instruction_type != InstructionType.MOVE_RESULT:
            return
        register = instruction.next.get_n_free_registers(1)[0]
        instruction.expand_after([
            f"const {register}, {value}",
            f"sub-float {instruction.registers[0]}, {register}, {instruction.registers[0]}",
        ])

    def patch_SetBrightness(instruction):
        registers = instruction.get_n_free_registers(2)
        value = "0x0" if "Adjustment" in instruction.method else "0x3f800000"
        instruction.expand_before([
            f"const {registers[0]}, {value}",
            f"sub-float {registers[1]}, {registers[0]}, {instruction.registers[-1]}",
        ])
        instruction.registers[-1] = registers[1]

    def patch_GetSetBrightness(instruction):
        registers = instruction.get_n_free_registers(2)
        value = "0x0" if "Adjustment" in instruction.method else "0x3f800000"
        instruction.expand_before([
            f"const {registers[0]}, {value}",
            f"sub-float {registers[1]}, {registers[0]}, {instruction.registers[-1]}",
        ])
        instruction.registers[-1] = registers[1]
        instruction = instruction.next_known()
        if instruction.instruction_type != InstructionType.MOVE_RESULT or 'djustment' in instruction.parent.name:
            return
        register = instruction.next.get_n_free_registers(1)[0]
        instruction.expand_after([
            f"const {register}, 0x3f800000",
            f"sub-float {instruction.registers[0]}, {register}, {instruction.registers[0]}",
        ])

    def patch_AddFadeOffInterpolator(instruction):
        registers = instruction.get_n_free_registers(2)
        instruction.expand_after([
            f"new-instance {registers[0]}, Landroid/view/animation/DecelerateInterpolator;",
            f"const {registers[1]}, 0x40800000",
            f"invoke-direct {{{registers[0]}, {registers[1]}}}, Landroid/view/animation/DecelerateInterpolator;-><init>(F)V",
            f"invoke-virtual {{{instruction.registers[0]}, {registers[0]}}}, Landroid/animation/ObjectAnimator;->setInterpolator(Landroid/animation/TimeInterpolator;)V",
        ])

    def patch_interceptKeyBeforeQueueing(method):
        first_instruction = method.first_instruction
        first_instruction.expand_after([
            'move-object/from16 v0, p0',
            'move-object/from16 v1, p1',
            f'invoke-direct {{v0, v1}}, {method.parent.class_name}->handleWakeUpOnVolume(Landroid/view/KeyEvent;)Z',
            'move-result v0',
            'if-eqz v0, :dont_skip',
            'const v0, 0x0',
            'return v0',
            ':dont_skip',
        ])
        method.parent.items.append((
                    'method',
                    SmaliMethod(
                        '.method private handleWakeUpOnVolume(Landroid/view/KeyEvent;)Z',
                        method.parent,
                        initial_instructions = [
                            '.locals 4',

                            'const-string v0, "sys.wakeup_on_volume"',
                            'invoke-static {v0}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
                            'move-result-object v0',
                            'const-string v1, "1"',
                            'invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z',
                            'move-result v0',
                            'if-eqz v0, :return_no_action',

                            'invoke-virtual {p1}, Landroid/view/KeyEvent;->getKeyCode()I',
                            'move-result v0',
                            'const v1, 0x1a', # POWER
                            'if-eq v0, v1, :on_power_event',
                            'const v1, 0x18', # VOLUME UP
                            'if-eq v0, v1, :is_volume_event',
                            'const v1, 0x19', # VOLUME DOWN
                            'if-ne v0, v1, :return_no_action',

                            ':is_volume_event',
                            'invoke-virtual {p1}, Landroid/view/KeyEvent;->getAction()I',
                            'move-result v0',
                            'if-eqz v0, :on_key_down',
                            'const v1, 0x1',
                            'if-ne v0, v1, :return_no_action',
                            f'invoke-direct {{p0, p1}}, {method.parent.class_name}->handleVolumeKeyEventUp(Landroid/view/KeyEvent;)Z',
                            'move-result v0',
                            'return v0',

                            ':on_key_down',
                            f'invoke-direct {{p0, p1}}, {method.parent.class_name}->handleVolumeKeyEventDown(Landroid/view/KeyEvent;)Z',
                            'move-result v0',
                            'return v0',

                            ':on_power_event',
                            'const-string v2, "sys.linevibrator_touch"',
                            'invoke-static {v2}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
                            'move-result-object v0',
                            ':try_number_type_start',
                            'invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I',
                            'move-result v0',
                            ':try_number_type_end',
                            '.catchall {:try_number_type_start .. :try_number_type_end} :skip_flipping',
                            'if-gez v0, :skip_flipping',
                            'const v1, 0x0',
                            'sub-int v0, v1, v0',
                            'new-instance v1, Ljava/lang/StringBuilder;',
                            'invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V',
                            'invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
                            'invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
                            'move-result-object v1',
                            'invoke-static {v2, v1}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',
                            ':skip_flipping',

                            ':return_no_action',
                            'const/4 v0, 0x0',
                            'return v0',
                        ]
                    )
                ))

        method.parent.items.append((
            'method',
            SmaliMethod(
                '.method private handleVolumeKeyEventUp(Landroid/view/KeyEvent;)Z',
                method.parent,
                initial_instructions = [
                    '.locals 4',

                    # RELEASE WAKELOCK
                    f'iget-object v0, p0, {method.parent.class_name}->mVolumeWakeLock:Landroid/os/PowerManager$WakeLock;',
                    'invoke-virtual {v0}, Landroid/os/PowerManager$WakeLock;->isHeld()Z',
                    'move-result v1',
                    'if-eqz v1, :delay_up_key',
                    'invoke-virtual {v0}, Landroid/os/PowerManager$WakeLock;->release()V',

                    # POST MESSAGES TO TURN SCREEN OFF
                    f'iget-object v0, p0, {method.parent.class_name}->mHandler:Landroid/os/Handler;',

                    f'const v1, {hex(595)}',
                    'invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V',

                    f'const v1, {hex(596)}',
                    'invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V',

                    f'const v1, {hex(595)}',
                    f'const-wide v2, {hex(500)}',
                    'invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z',

                    f'const v1, {hex(596)}',
                    f'const-wide v2, {hex(2650)}',
                    'invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z',

                    # RETURN
                    'const/4 v0, 0x0',
                    'return v0',

                    ':delay_up_key',
                    # COPY KEYEVENT
                    'new-instance v0, Landroid/view/KeyEvent;',
                    'invoke-direct {v0, p1}, Landroid/view/KeyEvent;-><init>(Landroid/view/KeyEvent;)V',
                    f'iput-object v0, p0, {method.parent.class_name}->mLastUpKeyEvent:Landroid/view/KeyEvent;',

                    # DELAY SENDING UP EVENT
                    f'iget-object v0, p0, {method.parent.class_name}->mHandler:Landroid/os/Handler;',
                    f'const v1, {hex(601)}',
                    f'const-wide v2, {hex(250)}',
                    'invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z',

                    'const/4 v0, 0x1',
                    'return v0',
                ]
            )
        ))

        method.parent.items.append((
                    'method',
                    SmaliMethod(
                        '.method private handleVolumeKeyEventDown(Landroid/view/KeyEvent;)Z',
                        method.parent,
                        initial_instructions = [
                            '.locals 4',
                            # SKIP KEYGUARD CHANGES IF INTERACTIVE
                            f'iget-object v0, p0, {method.parent.class_name}->mPowerManager:Landroid/os/PowerManager;',
                            'invoke-virtual {v0}, Landroid/os/PowerManager;->isInteractive()Z',
                            'move-result v1',
                            'if-nez v1, :return_no_down_action',

                            # DISABLE BACKLIGHT
                            'const-string v2, "sys.linevibrator_touch"',
                            'invoke-static {v2}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
                            'move-result-object v0',
                            ':try_number_type_start',
                            'invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I',
                            'move-result v0',
                            ':try_number_type_end',
                            '.catchall {:try_number_type_start .. :try_number_type_end} :skip_flipping',
                            'if-lez v0, :skip_flipping',
                            'const v1, 0x0',
                            'sub-int v0, v1, v0',
                            'new-instance v1, Ljava/lang/StringBuilder;',
                            'invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V',
                            'invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
                            'invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
                            'move-result-object v1',
                            'invoke-static {v2, v1}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',
                            ':skip_flipping',

                            # MAKE KEYGUARD GO AWAY
                            f'invoke-virtual {{p0}}, {method.parent.class_name}->forceHideKeyguard()V',

                            # SKIP REST IF WAKELOCK IS HELD
                            f'iget-object v0, p0, {method.parent.class_name}->mVolumeWakeLock:Landroid/os/PowerManager$WakeLock;',
                            'invoke-virtual {v0}, Landroid/os/PowerManager$WakeLock;->isHeld()Z',
                            'move-result v1',
                            'if-nez v1, :return_no_down_action',

                            # WAKE UP AND ACQUIRE WAKELOCK
                            'invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J',
                            'move-result-wide v1',
                            f'invoke-direct {{p0, v1, v2}}, {method.parent.class_name}->wakeUpFromPowerKey(J)V',
                            f'const-wide v1, {hex(2300)}',
                            'invoke-virtual {v0, v1, v2}, Landroid/os/PowerManager$WakeLock;->acquire(J)V',

                            # COPY KEYEVENT
                            'new-instance v0, Landroid/view/KeyEvent;',
                            'invoke-direct {v0, p1}, Landroid/view/KeyEvent;-><init>(Landroid/view/KeyEvent;)V',
                            f'iput-object v0, p0, {method.parent.class_name}->mLastDownKeyEvent:Landroid/view/KeyEvent;',

                            # DELAY SENDING THE DOWN EVENT
                            f'iget-object v0, p0, {method.parent.class_name}->mHandler:Landroid/os/Handler;',
                            f'const v1, {hex(600)}',
                            f'const-wide v2, {hex(250)}',
                            'invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z',
                            'const/4 v0, 0x1',
                            'return v0',

                            ':return_no_down_action',
                            'const/4 v0, 0x0',
                            'return v0',
                        ]
                    )
                ))

        method.parent.items.append((
            'method',
            SmaliMethod(
                '.method public forceHideKeyguard()V',
                method.parent,
                initial_instructions = [
                    '.locals 3',
                    f'iget-object v0, p0, {method.parent.class_name}->mKeyguardDelegate:Lcom/android/server/policy/keyguard/KeyguardServiceDelegate;',
                    'if-eqz v0, :no_delegate',
                    'invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J',
                    'move-result-wide v1',
                    'invoke-virtual {v0, v1, v2}, Lcom/android/server/policy/keyguard/KeyguardServiceDelegate;->startKeyguardExitAnimation(J)V'
                    ':no_delegate',
                    'return-void',
                ]
            )
        ))

        method.parent.items.append((
            'method',
            SmaliMethod(
                '.method public sendPastKeyUpEvent()V',
                method.parent,
                initial_instructions = [
                    '.locals 3',
                    f'iget-object v1, p0, {method.parent.class_name}->mLastUpKeyEvent:Landroid/view/KeyEvent;',
                    'if-eqz v1, :no_up_event',
                    'invoke-static {}, Landroid/hardware/input/InputManager;->getInstance()Landroid/hardware/input/InputManager;',
                    'move-result-object v0',
                    'const/4 v2, 0x0',
                    'invoke-virtual {v0, v1, v2}, Landroid/hardware/input/InputManager;->injectInputEvent(Landroid/view/InputEvent;I)Z',
                    'const v0, 0x0',
                    f'iput-object v0, p0, {method.parent.class_name}->mLastUpKeyEvent:Landroid/view/KeyEvent;',
                    ':no_up_event',
                    'return-void',
                ]
            )
        ))

        method.parent.items.append((
            'method',
            SmaliMethod(
                '.method public sendPastKeyDownEvent()V',
                method.parent,
                initial_instructions = [
                    '.locals 3',
                    f'iget-object v1, p0, {method.parent.class_name}->mLastDownKeyEvent:Landroid/view/KeyEvent;',
                    'if-eqz v1, :no_down_event',
                    'invoke-static {}, Landroid/hardware/input/InputManager;->getInstance()Landroid/hardware/input/InputManager;',
                    'move-result-object v0',
                    'const/4 v2, 0x0',
                    'invoke-virtual {v0, v1, v2}, Landroid/hardware/input/InputManager;->injectInputEvent(Landroid/view/InputEvent;I)Z',
                    'const v0, 0x0',
                    f'iput-object v0, p0, {method.parent.class_name}->mLastDownKeyEvent:Landroid/view/KeyEvent;',
                    ':no_down_event',
                    'return-void',
                ]
            )
        ))

        method.parent.items.append((
            'method',
            SmaliMethod(
                '.method public flipMaxBrightness(I)V',
                method.parent,
                initial_instructions = [
                    '.locals 3',
                    'const-string v2, "sys.linevibrator_touch"',
                    'invoke-static {v2}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
                    'move-result-object v0',
                    ':try_number_type_start',
                    'invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I',
                    'move-result v0',
                    ':try_number_type_end',
                    '.catchall {:try_number_type_start .. :try_number_type_end} :skip_flipping',
                    'xor-int v0, v0, p1',
                    'if-ltz v0, :skip_flipping',
                    'const v1, 0x0',
                    'sub-int v0, v1, v0',
                    'new-instance v1, Ljava/lang/StringBuilder;',
                    'invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V',
                    'invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
                    'invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
                    'move-result-object v1',
                    'invoke-static {v2, v1}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',
                    ':skip_flipping',
                    'return-void',
                ]
            )
        ))

    def patch_phoneWindowManagerInit(instruction):
        registers = instruction.next.get_n_free_registers(2)
        instruction.expand_after([
            f'const {registers[0]}, 0x10000006',
            f'const-string {registers[1]}, "Sys::VolumeWakeLock"',
            f'invoke-virtual {{{instruction.registers[0]}, {registers[0]}, {registers[1]}}}, Landroid/os/PowerManager;->newWakeLock(ILjava/lang/String;)Landroid/os/PowerManager$WakeLock;',
            f'move-result-object {registers[0]}',
            f'iput-object {registers[0]}, {instruction.registers[1]}, {instruction.parent.parent.class_name}->mVolumeWakeLock:Landroid/os/PowerManager$WakeLock;',
            f'const {registers[0]}, 0x0',
            f'iput-object {registers[0]}, {instruction.registers[1]}, {instruction.parent.parent.class_name}->mLastDownKeyEvent:Landroid/view/KeyEvent;',
            f'iput-object {registers[0]}, {instruction.registers[1]}, {instruction.parent.parent.class_name}->mLastUpKeyEvent:Landroid/view/KeyEvent;',
        ])

    def patch_windowManagerHandler(method):
        method.first_instruction.get_n_free_registers(4)
        method.first_instruction.expand_after([
            'iget v0, p1, Landroid/os/Message;->what:I',
            f'const v1, {hex(595)}',
            'if-eq v0, v1, :handle_go_to_sleep',
            f'const v1, {hex(596)}',
            'if-eq v0, v1, :handle_fix_backlight',
            f'const v1, {hex(600)}',
            'if-eq v0, v1, :handle_down',
            f'const v1, {hex(601)}',
            'if-eq v0, v1, :handle_up',
            'goto :handle_other_messages',

            ':handle_down',
            f'iget-object v0, p0, {method.parent.class_name}->this$0:{method.parent.class_name.split("$")[0]};',
            f'invoke-virtual {{v0}}, {method.parent.class_name.split("$")[0]};->sendPastKeyDownEvent()V',
            'return-void',

            ':handle_up',
            f'iget-object v0, p0, {method.parent.class_name}->this$0:{method.parent.class_name.split("$")[0]};',
            f'invoke-virtual {{v0}}, {method.parent.class_name.split("$")[0]};->sendPastKeyUpEvent()V',
            'return-void',

            ':handle_fix_backlight',
            'const-string v2, "sys.linevibrator_touch"',
            'invoke-static {v2}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;',
            'move-result-object v0',
            ':try_number_type_start',
            'invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I',
            'move-result v0',
            ':try_number_type_end',
            '.catchall {:try_number_type_start .. :try_number_type_end} :skip_flipping',
            'if-gez v0, :skip_flipping',
            'const v1, 0x0',
            'sub-int v0, v1, v0',
            'new-instance v1, Ljava/lang/StringBuilder;',
            'invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V',
            'invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;',
            'invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;',
            'move-result-object v1',
            'invoke-static {v2, v1}, Landroid/os/SystemProperties;->set(Ljava/lang/String;Ljava/lang/String;)V',
            ':skip_flipping',
            'return-void',

            ':handle_go_to_sleep',
            f'iget-object v0, p0, {method.parent.class_name}->this$0:{method.parent.class_name.split("$")[0]};',
            f'iget-object v1, v0, {method.parent.class_name.split("$")[0]};->mPowerManager:Landroid/os/PowerManager;',
            'invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J',
            'move-result-wide v2',
            'invoke-virtual {v1, v2, v3}, Landroid/os/PowerManager;->goToSleep(J)V',
            f'invoke-virtual {{v0}}, {method.parent.class_name.split("$")[0]};->forceHideKeyguard()V',
            'return-void',

            ':handle_other_messages',
        ])

    JarPatcher(
        "d/system/framework/services.jar",
        [
            FilePatch(
                file_patterns = [r"PhoneWindowManager\.smali"],
                patches = [
                    InstructionPatch(
                        method = "interceptKeyBeforeQueueing",
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_READ,
                            field_name = Matcher.regex(r'm?[Hh]andle[Vv]olume[Kk]eys[Ii]n[Ww][Mm]'),
                            data_type = "Z",
                        ),
                        action = lambda inst: inst.replace(f"const {inst.registers[0]}, 0x1")
                    ),
                    InstructionPatch(
                        method = MethodDetails(
                            name = Matcher.regex('<?init>?')
                        ),
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_WRITE,
                            field_name = Matcher.regex(r'm?[Pp]ower[Mm]anager$'),
                            data_type = "Landroid/os/PowerManager;",
                        ),
                        action = patch_phoneWindowManagerInit
                    ),
                    InstructionPatch(
                        method = "interceptKeyBeforeQueueing",
                        action = patch_interceptKeyBeforeQueueing
                    ),
                    InstructionPatch(
                        action = lambda file: file.smali_class.add_field(".field private mVolumeWakeLock:Landroid/os/PowerManager$WakeLock;")
                    ),
                    InstructionPatch(
                        action = lambda file: file.smali_class.add_field(".field private mLastUpKeyEvent:Landroid/view/KeyEvent;")
                    ),
                    InstructionPatch(
                        action = lambda file: file.smali_class.add_field(".field private mLastDownKeyEvent:Landroid/view/KeyEvent;")
                    ),
                ],
            ),
            FilePatch(
                file_patterns = [r"PhoneWindowManager[a-zA-Z0-9\$]+Handler\.smali"],
                patches = [
                    InstructionPatch(
                        method = "handleMessage",
                        action = patch_windowManagerHandler
                    ),
                ],
            ),
            FilePatch(
                file_patterns = [r"BatterySaverPolicy.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_READ,
                            field_name = Matcher.regex(r'm?([Ee]nableNightMode|[Dd]isable[Aa][Oo][Dd])'),
                            data_type = "Z",
                        ),
                        action = lambda inst: inst.replace(f"const {inst.registers[0]}, 0x0")
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_WRITE,
                            field_name = Matcher.regex(r'm?([Ee]nableNightMode|[Dd]isable[Aa][Oo][Dd])'),
                            data_type = "Z",
                        ),
                        action = lambda inst: inst.insert_before(f"const {inst.registers[0]}, 0x0")
                    ),
                ]
            ),
            FilePatch(
                file_patterns = [r"DisplayPowerController[0-9]*\.smali"],
                patches = [
                    InstructionPatch(
                        method = "updatePowerStateInternal",
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            method = "onBrightnessChanged",
                        ),
                        action = lambda inst: inst.expand_after([
                            f"move-object/from16 {inst.registers[0]}, p0",
                            f"iput {inst.registers[1]}, {inst.registers[0]}, {inst.parent.parent.class_name}->mChangedBrightnessValue:F"
                        ])
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_WRITE,
                            field_name = "mColorFadeOffAnimator",
                        ),
                        action = patch_AddFadeOffInterpolator
                    ),
                    InstructionPatch(
                        method = MethodDetails(
                            access_modifiers = Matcher.contains('constructor'),
                        ),
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_WRITE,
                            field_name = "mLeadDisplayId",
                        ),
                        action = lambda inst: inst.expand_after([
                            f"const/4 {inst.registers[0]}, 0x0",
                            f"iput {inst.registers[0]}, {inst.registers[1]}, {inst.parent.parent.class_name}->mChangedBrightnessValue:F"
                        ])
                    ),
                    InstructionPatch(
                        action = lambda file: file.smali_class.add_field(".field private mChangedBrightnessValue:F")
                    ),
                    InstructionPatch(
                        method = "updatePowerState",
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            method = "updatePowerStateInternal",
                        ),
                        action = patch_updatePowerStateInternal,
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            return_type = "V",
                            method = "end",
                            class_name = Matcher.regex(r".*ObjectAnimator;?"),
                        ),
                        action = lambda inst: inst.prev_known().field_name == 'mColorFadeOffAnimator' and setattr(inst, 'method', 'start')
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            return_type = "V",
                            method = "start",
                            class_name = Matcher.regex(r".*android/animation/(Object|Value)Animator;?"),
                        ),
                        action = patch_OverrideAnimatorScale
                    ),
                ]
            ),
            FilePatch(
                file_patterns = [r".*[Ww]allpaper.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            return_type = "V",
                            method = "start",
                            class_name = Matcher.regex(r".*android/animation/(Object|Value)Animator;?"),
                        ),
                        action = patch_OverrideAnimatorScale
                    ),
                ],
            ),
            FilePatch(
                file_patterns = [r"ColorDisplayService\$.*[Nn]ight[Dd]isplay.*\.smali"],
                patches = [
                    InstructionPatch(
                        method = "onColorTemperatureChanged",
                        action = patch_onColorTemperatureChanged,
                    )
                ]
            ),
            FilePatch(
                file_patterns = [r"ColorDisplayService\$.*[Ii]nternal.*\.smali"],
                patches = [
                    InstructionPatch(action = patch_ColorDisplayServiceInternal),
                ]
            ),
            FilePatch(
                file_patterns = [r"Vibrator(Manager)?Service\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            method = "startVibrationLocked",
                        ),
                        action = extract_invokeVibrationLocked,
                    ),
                    InstructionPatch(
                        method = "startVibrationLocked",
                        action = patch_startVibrationLocked,
                    ),
                    InstructionPatch(
                        method = "tempStartVibrationLocked",
                        action = lambda method: setattr(method, 'name', 'startVibrationLocked'),
                    ),
                ]
            ),
            FilePatch(
                file_patterns = [r"VibrationScaler\.smali"],
                patches = [
                    InstructionPatch(
                        field = FieldDetails(
                            name = Matcher.regex(r"SCALE_FACTOR_[A-Z_]+"),
                            type = "F",
                            value = Matcher.regex(r"\d+\.\d+f"),
                        ),
                        action = lambda field: setattr(field, 'value', f'{float(str(field.value)[:-1])**2.0+0.3:.1f}f')
                    ),
                ]
            ),
            FilePatch(
                file_patterns = [r"ColorFade\.smali"],
                patches = [
                    InstructionPatch(
                        action = patch_ColorFade
                    ),
                ]
            ),
            FilePatch(
                file_patterns = [r"BurnInProtection.*\.smali"],
                patches = [
                    InstructionPatch(
                        method = MethodDetails(
                            name = "updateBurnInProtection",
                            return_type = "V",
                        ),
                        action = lambda method: method.replace_with_lines([
                            'return-void'
                        ])
                    )
                ]
            ),
            FilePatch(
                file_patterns = [r"AutomaticBrightnessController\S*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            InstructionType.METHOD_INVOKE,
                            method = Matcher.regex(r"(getBrightness|convertToFloatScale)"),
                            return_type = "F",
                            class_name = Matcher.regex(r".*BrightnessMappingStrategy.*"),
                        ),
                        action = patch_GetBrightness
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            InstructionType.METHOD_INVOKE,
                            method = Matcher.regex(r"(convertTo.*Nits|addUserDataPoint)"),
                            class_name = Matcher.regex(r".*BrightnessMappingStrategy.*"),
                            param_types = Matcher.regex(r".*F"),
                        ),
                        action = patch_SetBrightness
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            InstructionType.METHOD_INVOKE,
                            method = Matcher.regex(r"get.*Threshold.*"),
                            class_name = Matcher.regex(r".*HysteresisLevels.*"),
                            param_types = Matcher.regex(r".*F"),
                        ),
                        action = patch_GetSetBrightness
                    ),
                ],
            )
        ]
    ).patch(api = 29)

def patch_systemui():
    values_per_scrim_enum = {
        "AOD": {
            "mFrontAlpha": "0x0",
            "mFrontTint": "0x0",
        },
        "KEYGUARD": {
            "mBehindAlpha": "0x0",
            "mBehindTint": "0x0",
            "mNotifAlpha": "0X0",
        },
    }
    scrim_enum_triples = list((key1, key2, value) for key1, nested_dict in values_per_scrim_enum.items() for key2, value in nested_dict.items())
    wallpaper_flag_count = 0
    def patch_WallpaperFlags(instruction):
        nonlocal wallpaper_flag_count
        next_instruction = instruction.next_known()
        registers = next_instruction.next.get_n_free_registers(1)
        next_instruction.expand_after([
            f'iget-boolean {registers[0]}, {instruction.registers[0]}, {instruction.parent.parent.class_name}->mInAmbientMode:Z',
            f'if-eqz {registers[0]}, :ambient_cond_{wallpaper_flag_count}_normal',
            f'const {registers[0]}, 0x1',
            f'if-ne {registers[0]}, {next_instruction.registers[0]}, :ambient_cond_{wallpaper_flag_count}_normal',
            f'const {next_instruction.registers[0]}, 0x2',
            f':ambient_cond_{wallpaper_flag_count}_normal',
        ])
        wallpaper_flag_count+=1

    def patch_UnlockedScreenOffAnimationController(instruction):
        invoke_instruction = instruction.next_known()
        if invoke_instruction.instruction_type != InstructionType.METHOD_INVOKE or len(invoke_instruction.registers) == 0 or invoke_instruction.registers[-1] != instruction.registers[0]:
            return
        result_instruction = invoke_instruction.next_known()
        if result_instruction.instruction_type != InstructionType.MOVE_RESULT:
            return
        if "object" in result_instruction.modifier:
            result_instruction.insert_after(f"const-string {result_instruction.registers[0]}, \"1\"")
        else:
            result_instruction.insert_after(f"const {result_instruction.registers[0]}, 0x3f800000")

    def patch_ImageWallpaperInit(method):
        registers = method.first_instruction.next.get_n_free_registers(1)
        method.first_instruction.expand_after([
            f'const/4 {registers[0]}, 0x0',
            f'iput-boolean {registers[0]}, p0, {method.parent.class_name}->mInAmbientMode:Z',
        ])

    def patch_ImageWallpaperEngine(smali_file):
        smali_file.smali_class.add_field('.field private volatile mInAmbientMode:Z')
        smali_file.smali_class.items.append((
            'method',
            SmaliMethod(
                '.method public onAmbientModeChanged(ZJ)V',
                smali_file.smali_class,
                initial_instructions = [
                    '.locals 7',
                    f'iget-object v5, p0, {smali_file.smali_class.class_name}->mLock:Ljava/lang/Object;',
                    'monitor-enter v5',
                    f'iget-boolean p2, p0, {smali_file.smali_class.class_name}->mInAmbientMode:Z',
                    'if-eq p1, p2, :cond_no_update',
                    f'iput-boolean p1, p0, {smali_file.smali_class.class_name}->mInAmbientMode:Z',
                    f'iget-object v0, p0, {smali_file.smali_class.class_name}->mWallpaperManager:Landroid/app/WallpaperManager;',
                    f'iget-object v1, p0, {smali_file.smali_class.class_name}->this$0:{smali_file.smali_class.class_name.rsplit("$", 1)[0]};',
                    f'iget-object v1, v1, {smali_file.smali_class.class_name.rsplit("$", 1)[0]};->mUserTracker:Lcom/android/systemui/settings/UserTracker;',
                    'check-cast v1, Lcom/android/systemui/settings/UserTrackerImpl;',
                    'invoke-virtual {v1}, Lcom/android/systemui/settings/UserTrackerImpl;->getUserId()I',
                    'move-result v1',
                    'const v2, 0x0',
                    'const v4, 0x1',
                    f'invoke-virtual {{p0}}, {smali_file.smali_class.class_name}->getWallpaperFlags()I',
                    'move-result v3',
                    'const v6, 0x3',
                    'if-eq v6, v3, :cond_no_update',
                    'invoke-virtual {v0, v1, v2, v3, v4}, Landroid/app/WallpaperManager;->getBitmapAsUser(IZIZ)Landroid/graphics/Bitmap;',
                    'move-result-object v0',
                    'if-eqz v0, :cond_no_update',
                    f'invoke-virtual {{p0, v0}}, {smali_file.smali_class.class_name}->drawFrameOnCanvas(Landroid/graphics/Bitmap;)V',
                    ':cond_no_update',
                    'monitor-exit v5',
                    'return-void',
                ]
            )
        ))
        smali_file.smali_class.for_instruction(
            InstructionDetails(
                instruction_type = InstructionType.METHOD_INVOKE,
                method = "getWallpaperFlags",
            ),
            action = patch_WallpaperFlags,
        )
        smali_file.smali_class.for_method(
            MethodDetails(name = "<init>", return_type = "V"),
            action = patch_ImageWallpaperInit,
        )

    def patch_RemoveInvokeWithResult(instruction, result = "0x1"):
        next_instruction = instruction.next_known()
        instruction.remove()
        if next_instruction.instruction_type == InstructionType.MOVE_RESULT:
            next_instruction.replace(f'const {next_instruction.registers[0]}, {result}')

    JarPatcher(
        "d/system/system_ext/priv-app/SystemUI/SystemUI.apk",
        [
            FilePatch(
                file_patterns = [r"Doze(Sensors|Triggers).*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_WRITE,
                            field_name = Matcher.regex(r"mListening((TouchScreen|Prox)\S*Sensors?)?"),
                            data_type = "Z",
                        ),
                        action = lambda instruction: instruction.insert_before(f'const {instruction.registers[0]}, 0x0'),
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_READ,
                            field_name = Matcher.regex(r"mListening((TouchScreen|Prox)\S*Sensors?)?"),
                            data_type = "Z",
                        ),
                        action = lambda instruction: instruction.replace(f'const {instruction.registers[0]}, 0x0'),
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            method = Matcher.regex(r"(register|resume)"),
                            class_name = Matcher.regex(r'.*(Proximity|Threshold)Sensor;?'),
                        ),
                        action = lambda instruction: instruction.remove()
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            method = Matcher.regex(r"(add|remove)Callback"),
                            class_name = Matcher.regex(r'.*DevicePostureController;?'),
                        ),
                        action = lambda instruction: instruction.remove()
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            method = Matcher.regex(r"(un)?register(Settings|Content)Observer(ForUser)?"),
                            class_name = Matcher.regex(r'.*(Settings|Sensor);?'),
                        ),
                        action = lambda instruction: instruction.remove()
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            method = "requestTriggerSensor",
                            class_name = Matcher.regex(r'.*SensorManager;?'),
                        ),
                        action = patch_RemoveInvokeWithResult
                    ),
                ],
            ),
            FilePatch(
                file_patterns = [r"UnlockedScreenOffAnimationController.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.CONSTANT,
                            constant_value = Matcher.regex(rf'(?i)"?animator_duration_scale"?'),
                        ),
                        action = patch_UnlockedScreenOffAnimationController,
                    ),
                ],
            ),
            FilePatch(
                file_patterns = [r".*Clock.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_READ,
                            field_name = Matcher.regex(r'm?[Dd]oz(e|ing)([wW]eight|[cC]olor)'),
                        ),
                        action = lambda inst: setattr(inst, 'field_name', re.sub(r'm?[Dd]oz(e|ing)', 'lockScreen', inst.field_name))
                    )
                ]
            ),
            FilePatch(
                file_patterns = [r".*[Kk]ey[Gg]uard.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_WRITE,
                            field_name = Matcher.regex(r'[\w]*[Tt]ext[Cc]olor[\w]*'),
                            data_type = Matcher.regex(r'\S*/ColorStateList;'),
                        ),
                        action = lambda inst: inst.expand_before([
                            f"const {inst.registers[0]}, 0xff444444",
                            f"invoke-static {{{inst.registers[0]}}}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;",
                            f"move-result-object {inst.registers[0]}",
                        ])
                    ),
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_READ,
                            field_name = Matcher.regex(r'[a-zA-Z]*(D|d)arkAmount'),
                            data_type = "F",
                        ),
                        action = lambda inst: inst.replace(f"const {inst.registers[0]}, 0x0")
                    )
                ]
            ),
            FilePatch(
                file_patterns = [r".*Icon.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_READ,
                            field_name = Matcher.regex(r'm?(D|d)ozeAmount'),
                            data_type = "F",
                        ),
                        action = lambda inst: inst.replace(f"const {inst.registers[0]}, 0x0")
                    )
                ]
            ),
            FilePatch(
                file_patterns = [r"Scrim.*\.smali", r"Keyguard.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.FIELD_READ,
                            field_name = Matcher.regex(r'[a-zA-Z]*(S|s)upportsAmbientMode'),
                            data_type = "Z",
                        ),
                        action = lambda inst: inst.replace(f"const/4 {inst.registers[0]}, 0x1")
                    )
                ]
            ),
            FilePatch(
                file_patterns = [r".*Notification.*Doze.*\.smali"],
                patches = [
                    InstructionPatch(
                        method = "updateGrayscale",
                        action = lambda method: method.replace_with_lines(["return-void"])
                    )
                ]
            ),
            FilePatch(
                file_patterns = [r".*ScrimState.*\.smali"],
                patches = list(
                    InstructionPatch(
                        method = "<init>",
                        instruction = InstructionDetails(
                          instruction_type = InstructionType.CONSTANT,
                          constant_value = Matcher.regex(rf'"?{enum}"?'),
                        ),
                        action = lambda inst, key=key, value=value: inst.parent.parent.for_instruction(
                            InstructionDetails(
                              instruction_type = InstructionType.FIELD_WRITE,
                              field_name = key,
                            ),
                            lambda nested_insr, value=value: nested_insr.insert_before(f"const{nested_insr.modifier} {nested_insr.registers[0]}, {value}")
                        )
                    ) for (enum, key, value) in scrim_enum_triples
                )
            ),
            FilePatch(
                file_patterns = [
                    r".*[Ww]allpaper.*\.smali",
                    r".*[Kk]eyguard.*\.smali",
                    r".*[Ss]crim.*\.smali",
                    r".*([Ss]tatus|[Ll]ight)[Bb]ar.*\.smali",
                ],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                            instruction_type = InstructionType.METHOD_INVOKE,
                            return_type = "V",
                            method = "start",
                            class_name = Matcher.regex(r".*android/animation/(Object|Value)Animator;?"),
                        ),
                        action = patch_OverrideAnimatorScale
                    ),
                ],
            ),
            FilePatch(
                file_patterns = [r".*KeyguardViewMediator.*\.smali"],
                patches = [
                    InstructionPatch(
                        instruction = InstructionDetails(
                          instruction_type = InstructionType.CONSTANT,
                          constant_value = Matcher.regex(r'"?com.android.systemui:BOUNCER_DOZING"?'),
                        ),
                        action = lambda inst: inst.next_known().remove() if inst.next_known() is not None and inst.next_known().method == "wakeUp" else None
                    ),
                ]
            ),
            FilePatch(
                file_patterns = [r".*ImageWallpaper\$[a-zA-Z]*Engine\.smali"],
                patches = [
                    InstructionPatch(
                        action = patch_ImageWallpaperEngine
                    )
                ]
            ),
        ]
    ).patch(install = ["d/system/system_ext/priv-app/SystemUI/SystemUI.apk"], sign = True, api = 29)

def patch_AddTintToCall():
    namespaces = {
        'android': 'http://schemas.android.com/apk/res/android',
        'app': 'http://schemas.android.com/apk/res-auto'
    }

    for prefix, uri in namespaces.items():
        ET.register_namespace(prefix, uri)

    with open('res/layout/swipe_up_down_method.xml', 'r') as f:
        content_xml = ET.fromstring(f.read())

    content_xml.set('{http://schemas.android.com/apk/res/android}background', '#66000000')

    for elem in content_xml.iter():
        margin_start = elem.get('{http://schemas.android.com/apk/res/android}layout_marginStart')
        margin_end = elem.get('{http://schemas.android.com/apk/res/android}layout_marginEnd')

        if margin_start:
            elem.set('{http://schemas.android.com/apk/res/android}paddingStart', margin_start)
            elem.attrib.pop('{http://schemas.android.com/apk/res/android}layout_marginStart', None)

        if margin_end:
            elem.set('{http://schemas.android.com/apk/res/android}paddingEnd', margin_end)
            elem.attrib.pop('{http://schemas.android.com/apk/res/android}layout_marginEnd', None)

    with open('res/layout/swipe_up_down_method.xml', 'w') as file:
        ET.ElementTree(content_xml).write(file, encoding="unicode", xml_declaration=True)


def patch_CallUI():
    JarPatcher(
        "d/system/product/priv-app/Dialer/Dialer.apk",
        [
            FunctionPatch(action=patch_AddTintToCall)
        ]
    ).patch(sign = True, use_res = True, use_src = False, api = 29)

def update_build_prop():
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

def update_vndk_rc():
    logging.info("Updating vndk init script...")
    vndk_rc_path = "d/system/etc/init/vndk.rc"
    with open(vndk_rc_path, "r") as file:
        lines = file.readlines()

    service_entries = [
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/cmd overlay enable me.phh.treble.overlay.misc.aod_systemui\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/service call SurfaceFlinger 1008 i32 1\n",
        "    start a9_eink_server\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/sh -c 'existing=$(/system/bin/settings get secure enabled_accessibility_services); new_service=\"com.lmqr.ha9_comp_service/.A9AccessibilityService\"; if ! echo \"$existing\" | /system/bin/grep -q -F \"$new_service\"; then if [ -n \"$existing\" ]; then updated=\"$existing:$new_service\"; else updated=\"$new_service\"; fi; /system/bin/settings put secure enabled_accessibility_services \"$updated\"; fi'\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/appops set com.lmqr.ha9_comp_service SYSTEM_ALERT_WINDOW allow\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chmod 444 /sys/class/leds/aw99703-bl-1/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chmod 444 /sys/class/leds/aw99703-bl-2/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chown root:root /sys/class/leds/aw99703-bl-1/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chown root:root /sys/class/leds/aw99703-bl-2/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chcon u:object_r:sysfs_leds:s0 /sys/class/backlight/aw99703-bl-1/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chcon u:object_r:sysfs_leds:s0 /sys/class/backlight/aw99703-bl-2/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chmod 444 /sys/class/leds/ktd3137-bl-3/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chmod 444 /sys/class/leds/ktd3137-bl-4/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chown root:root /sys/class/leds/ktd3137-bl-3/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chown root:root /sys/class/leds/ktd3137-bl-4/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chcon u:object_r:sysfs_leds:s0 /sys/class/backlight/ktd3137-bl-3/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/chcon u:object_r:sysfs_leds:s0 /sys/class/backlight/ktd3137-bl-4/brightness\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/sh -c \"if [ ! -f /data/local/tmp/reset_carriers_done ]; then /system/bin/touch /data/local/tmp/reset_carriers_done; /system/bin/content delete --uri content://telephony/carriers/restore; fi\"\n",
        "    exec_background u:r:phhsu_daemon:s0 root -- /system/bin/sh -c \"if [ ! -f /data/local/tmp/disable_ambient_display ]; then /system/bin/touch /data/local/tmp/disable_ambient_display; /system/bin/settings put secure doze_enabled 0; fi\"\n",
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
            file.write("    write /sys/class/backlight/ktd3137-bl-3/brightness ${sys.linevibrator_open}\n")
            file.write("    write /sys/class/backlight/aw99703-bl-1/brightness ${sys.linevibrator_open}\n\n")

        if not any("on property:sys.linevibrator_short=*" in line for line in lines):
            file.write("\non property:sys.linevibrator_short=*\n")
            file.write("    write /sys/class/backlight/ktd3137-bl-4/brightness ${sys.linevibrator_short}\n")
            file.write("    write /sys/class/backlight/aw99703-bl-2/brightness ${sys.linevibrator_short}\n\n")

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
        replace_file("d/system/priv-app/TrebleApp/TrebleApp.apk")
        replace_file("d/system/product/overlay/treble-overlay-Hisense-HLTE556N.apk")
        replace_file("d/system/bin/a9_eink_server", perms = 0o755, owner = "root:2000", secontext = "u:object_r:phhsu_exec:s0")
        replace_file("d/system/priv-app/a9service.apk")
        replace_file("d/system/priv-app/org.fdroid.fdroid.privileged.apk")
        replace_file("d/system/priv-app/com.google.android.gms.apk")
        replace_file("d/system/priv-app/com.android.vending.apk")
        replace_file("d/system/app/F-Droid.apk")
        replace_file("d/system/app/ims-caf-u.apk")
        replace_file("d/system/etc/hosts")
        update_build_prop()
        try:
            patch_CallUI()
            pass
        except subprocess.CalledProcessError:
            logging.warning('Dialer app patching error, skipping.')
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