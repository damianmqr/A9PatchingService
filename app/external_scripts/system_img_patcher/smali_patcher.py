from string import Formatter
from abc import ABC, abstractmethod
import os
import shutil
import difflib
import re
import logging
import subprocess
from typing import List, Callable, Optional, Any
from dataclasses import dataclass
from enum import Enum, auto

class MatchStrategy(Enum):
    ANY = auto()
    EXACT = auto()
    REGEX = auto()
    CONTAINS = auto()
    CHOICE = auto()

@dataclass(frozen=True)
class Matcher:
    strategy: MatchStrategy
    value: Optional[Any] = None

    def matches(self, other: Any) -> bool:
        if isinstance(other, Matcher):
            other = other.value
        if self.strategy == MatchStrategy.ANY:
            return True
        elif self.strategy == MatchStrategy.CHOICE:
            return any(v.matches(other) for v in self.value)
        elif self.strategy == MatchStrategy.EXACT:
            return other == self.value
        elif self.strategy == MatchStrategy.REGEX:
            return bool(re.match(self.value, str(other)))
        elif self.strategy == MatchStrategy.CONTAINS:
            for v in other:
                if isinstance(self.value, Matcher):
                    if self.value.matches(v):
                        return True
                elif self.value == v:
                    return True
        return False

    @staticmethod
    def any():
        return Matcher(MatchStrategy.ANY)

    @staticmethod
    def exact(value: str):
        return Matcher(MatchStrategy.EXACT, value)

    @staticmethod
    def contains(value: str):
        return Matcher(MatchStrategy.CONTAINS, value)

    @staticmethod
    def regex(pattern: str):
        return Matcher(MatchStrategy.REGEX, pattern)

    @staticmethod
    def choice(*patterns: str):
        return Matcher(MatchStrategy.CHOICE, [pattern if isinstance(pattern, Matcher) else Matcher(MatchStrategy.EXACT, pattern) for pattern in patterns])

    def __str__(self):
        if self.strategy == MatchStrategy.EXACT:
            return str(self.value)
        return f"Matcher({self.strategy.name}, {self.value})"

    def __repr__(self):
        return self.__str__()

class Details:
    def matches(self, other):
        fields = self.__dataclass_fields__
        for field in fields:
            value = getattr(self, field)
            if value is None:
                continue

            other_value = getattr(other, field)
            if isinstance(value, Matcher):
                if not value.matches(other_value):
                    return False
            elif other_value != value:
                return False
        return True

@dataclass
class InstructionDetails(Details):
    instruction_type: Matcher = None
    modifier: Matcher = None
    class_name: Matcher = None
    field_name: Matcher = None
    data_type: Matcher = None
    registers: list = None
    method: Matcher = None
    param_types: Matcher = None
    return_type: Matcher = None
    label: Matcher = None
    constant_value: Matcher = None

@dataclass
class FieldDetails(Details):
    name: Matcher = None
    modifiers: Matcher = None
    type: Matcher = None
    value: Matcher = None

@dataclass
class MethodDetails(Details):
    name: Matcher = None
    parameters: Matcher = None
    return_type: Matcher = None
    access_modifiers: Matcher = None

class InstructionType(Enum):
    FIELD_READ = auto()
    FIELD_WRITE = auto()
    METHOD_INVOKE = auto()
    CONSTANT = auto()
    BRANCH = auto()
    LABEL = auto()
    NEW_INSTANCE = auto()
    NEW_ARRAY = auto()
    MOVE_RESULT = auto()
    MOVE = auto()
    EMPTY = auto()
    UNKNOWN = auto()

    def matches(self, other):
        if isinstance(other, list):
            return any(map(self.matches, other))
        return other == self

def run_command(command, check = True):
    logging.info(f"Running command: {command}")
    try:
        result = subprocess.run(command, shell=True, check=check, capture_output=True, text=True)
        logging.info(f"Command '{command}' executed successfully.")
        return result.stdout
    except subprocess.CalledProcessError as e:
        logging.error(f"Error executing command '{command}': {e.stderr}")
        logging.error(f"Output: {e.output}")
        raise

class JarPatcher:
    def __init__(self, target_file, patchers = []):
        self.target_file = target_file
        no_dir_file = target_file.rsplit('/', 1)[-1]
        self.file_name, self.file_extensions = no_dir_file.rsplit('.', 1)
        self.temp_dir_name = f"temp_{self.file_name}"
        self.patchers = patchers

    def add_smali_patcher(self, patcher):
        self.patchers.append(patcher)

    def patch(self, install = [], sign = False, use_src = True, use_res = False, copy_meta_inf = True, api = None):
        os.makedirs(self.temp_dir_name, exist_ok = True)

        for f in install:
            run_command(f"apktool if {f}")
        decompile_command = f"apktool d{' -s' if not use_src else ''}{' -r' if not use_res else ''} -f {self.target_file} -o {self.temp_dir_name}"
        run_command(decompile_command)

        os.chdir(self.temp_dir_name)
        try:
            for patcher in self.patchers:
                patcher.apply()
            for file in os.listdir("."):
                if os.path.isfile(file) and file.endswith('.diff'):
                    shutil.move(file, f'../{self.file_name}_{file}')

        except Exception as ex:
            logging.error(ex)
            os.chdir('..')
            raise ex

        os.chdir('..')

        temp_apk_name = f"{self.file_name}-TMP.{self.file_extensions}"
        build_command = f"apktool b {self.temp_dir_name}{' -c' if copy_meta_inf else ''}{' -api ' + str(api) if api else ''} -o {temp_apk_name}"

        run_command(build_command)

        try:
            run_command(f"zipalign -p -f -v 4 {temp_apk_name} {temp_apk_name}.apk")
            shutil.move(f"{temp_apk_name}.apk", temp_apk_name)
        except subprocess.CalledProcessError:
            logging.warning("Failed to align the apk, to use it ensure zipalign is in PATH.")

        if sign:
            run_command(f"apksigner sign --key ../platform.pk8 --cert ../platform.x509.pem {temp_apk_name}")


        shutil.move(temp_apk_name, self.target_file)
        shutil.rmtree(self.temp_dir_name)

@dataclass(frozen=True)
class InstructionPatch:
    action: Callable
    method: Matcher = None
    instruction: Matcher = None
    field: Matcher = None
    def __post_init__(self):
        if isinstance(self.method, str):
            object.__setattr__(self, 'method', MethodDetails(name = self.method))

class FilePatch:
    def __init__(self, file_patterns: List[str], patches: List[InstructionPatch]):
        self.file_patterns = file_patterns
        self.patches = patches

    def apply(self):
        patterns = [re.compile(pattern) for pattern in self.file_patterns]
        matching_files = []

        for root, dirs, files in os.walk("."):
            for file in files:
                if any(pattern.match(file) for pattern in patterns):
                    matching_files.append(os.path.join(root, file))

        for file in matching_files:
            smali_file = SmaliFile(file)

            with open(file, 'r') as f:
                cont_temp = f.read()

            for patch in self.patches:
                if patch.field is not None:
                    if smali_file.smali_class:
                        for f in smali_file.smali_class.get_fields(patch.field):
                            patch.action(f)

                if patch.method is None and patch.instruction is not None:
                    smali_file.for_instruction(patch.instruction, patch.action)
                elif patch.instruction is None and patch.method is not None:
                    smali_file.for_method(
                        patch.method,
                        patch.action
                    )
                elif patch.instruction is not None and patch.method is not None:
                    smali_file.for_method(
                        patch.method,
                        action=lambda m: m.for_instruction(patch.instruction, patch.action)
                    )
                elif patch.field is None:
                    patch.action(smali_file)

            smali_file.save_file()

class FunctionPatch:
    def __init__(self, action):
        self.action = action

    def apply(self):
        self.action()

class SmaliPiece:
    def __getattr__(self, name):
        if name == 'details':
            return None
        return getattr(self.details, name)
    def __setattr__(self, name, value):
        if name != 'details' and name in dir(self.details):
            setattr(self.details, name, value)
        else:
            super().__setattr__(name, value)

def compare_changes(old, new):
    diff = list(difflib.unified_diff(
        [line for line in old if len(line.strip()) > 0],
        [line for line in new if len(line.strip()) > 0],
        fromfile="OLD",
        tofile="NEW",
        lineterm=''
    ))

    return diff

class SmaliFile:
    def __init__(self, filename: str):
        self.filename = filename
        self.smali_class = None
        self.load_file()

    def load_file(self):
        with open(self.filename, 'r') as file:
            self.content = file.read()
        self.smali_class = SmaliClass(self.content)

    def save_file(self, filename: str = None):
        if filename is None:
            filename = self.filename
        new_content = str(self.smali_class)
        if self.content != new_content:
            diff = compare_changes(self.content.splitlines(), new_content.splitlines())
            if len(diff) > 0:
                filename_no_dir = os.path.split(filename)[1]
                logging.info(f'Modified file {filename_no_dir}')
                with open(filename_no_dir.rsplit('.', 1)[0]+'.diff', 'w') as file:
                    for line in diff:
                        file.write(line + '\n')
                    file.write('-' * 60 + '\n')
                self.content = new_content
                with open(filename, 'w') as file:
                    file.write(new_content)

    def for_method(self, method: Matcher, action: Callable[['SmaliMethod'], None]):
        self.smali_class.for_method(method, action)

    def for_instruction(self, instruction_details: InstructionDetails, action: Callable[['SmaliInstruction'], None]):
        self.smali_class.for_instruction(instruction_details, action)

    def __str__(self):
        return str(self.smali_class)

class SmaliClass(SmaliPiece):
    def __init__(self, content: str):
        self.items: List[tuple] = []
        self.class_name = ""
        self.base_dir = ""
        self.class_modifiers = []
        self.parse_content(content)

    def for_method(self, method: Matcher, action: Callable[['SmaliMethod'], None]):
        for item_name, searched_method in self.items:
            if item_name != 'method':
                continue
            if method.matches(searched_method.details):
                action(searched_method)

    def has_method(self, method: Matcher):
        for item_name, searched_method in self.items:
            if item_name != 'method':
                continue
            if method.matches(searched_method.details):
                return True
        return False

    def for_instruction(self, instruction_details: InstructionDetails, action: Callable[['SmaliInstruction'], None]):
        for item_name, method in self.items:
            if item_name != 'method':
                continue
            method.for_instruction(instruction_details, action)

    def get_fields(self, field_details):
        for item_name, field in self.items:
            if item_name != 'field':
                continue
            if field.matches(field_details):
                yield field

    def add_field(self, field):
        if isinstance(field, str):
            field = SmaliField(field, self)
        field = ('field', field)
        for i in range(len(self.items)):
            if self.items[i][0] == 'field':
                self.items.insert(i+1, field)
                return
            if self.items[i][0] == 'method':
                self.items.insert(i, field)
                return
        self.items.append(field)

    def parse_content(self, content: str):
        class_regex = r'\.class\s+([\w\s]+\s+)?([^;\s]+);'
        class_match = re.search(class_regex, content)
        if class_match:
            self.class_name = class_match.group(2)
            class_split = self.class_name.split('/')
            if len(class_split) > 1:
                self.base_dir = '/'.join(class_split[:-1])
            if class_match.group(1):
                self.class_modifiers = [mod for mod in map(str.strip, class_match.group(1).split()) if len(mod) > 0]
            else:
                self.class_modifiers = []
            content = re.sub(class_regex+'\n', '', content)

        lines = content.splitlines()
        current_method = None
        for raw_line in lines:
            line = raw_line.strip()
            if line.startswith('.method'):
                current_method = SmaliMethod(line, self)
                self.items.append(('method', current_method))
            elif line.startswith('.end method'):
                current_method = None
            elif current_method:
                current_method.add_instruction(raw_line)
            elif line.startswith('.field'):
                self.items.append(('field', SmaliField(raw_line, self)))
            elif not line.startswith('.class'):
                self.items.append(('unknown', raw_line))

    def __str__(self):
        modifiers = ' '.join(self.class_modifiers) + (' ' if len(self.class_modifiers) > 0 else '')
        class_header = f".class {modifiers}{self.class_name};"
        return f"{class_header}\n" + "\n".join(str(item[1]) for item in self.items) + ("\n" if len(self.items) > 0 else "")


class SmaliMethod(SmaliPiece):
    def __init__(self, header: str, parent: SmaliClass, initial_instructions: list = []):
        self.header = header
        self.parent = parent
        self.locals = None
        self.access_modifiers = []
        self.parse_header(header)

        self.first_instruction = SmaliInstruction("", self)
        self.last_instruction = SmaliInstruction("", self)
        self.first_instruction.next = self.last_instruction
        self.last_instruction.prev = self.first_instruction

        for instruction in initial_instructions:
            self.add_instruction(instruction)

    def parse_header(self, header_line: str):
        method_regex = r'\.method\s+(?P<modifiers>(\S+[\t \f]+)*)(?P<name>\S+)\((?P<parametes>.*?)\)(?P<return_type>\S+)'
        match = re.search(method_regex, header_line)
        if match:
            if match.group(1):
                access_modifiers = [mod.strip() for mod in match.group('modifiers').split() if mod.strip()]
            else:
                access_modifiers = []
            self.details = MethodDetails(
                name = match.group('name'),
                parameters = match.group('parametes'),
                return_type = match.group('return_type'),
                access_modifiers = access_modifiers
            )
        else:
            self.details = MethodDetails()

    def add_instruction(self, line: str):
        if len(line.strip()) == 0:
            return

        if line.strip().startswith('.locals'):
            self.locals = int(re.search(r'\.locals\s+(\d+)', line).group(1))
        else:
            new_instruction = SmaliInstruction(line, self)
            last_real_instruction = self.last_instruction.prev
            last_real_instruction.next = new_instruction
            new_instruction.prev = last_real_instruction
            new_instruction.next = self.last_instruction
            self.last_instruction.prev = new_instruction

    def clear(self):
        self.first_instruction.next = self.last_instruction
        self.last_instruction.prev = self.first_instruction
        self.locals = 0

    def replace_with_lines(self, lines, locals=0):
        self.clear()
        for line in lines:
            self.add_instruction(line)
        self.locals = locals


    def construct_header(self):
        access_modifiers_str = ' '.join(self.details.access_modifiers) + (' ' if len(self.details.access_modifiers) > 0 else '')
        return f".method {access_modifiers_str}{self.details.name}({self.details.parameters}){self.details.return_type}"

    def __str__(self):
        locals_str = f"    .locals {self.locals}\n" if self.locals is not None else ""
        instructions_str = '\n'.join(str(instr) for instr in self.iterate_instructions())
        second_break = '\n' if len(instructions_str) + len(locals_str) > 0 else ''
        method_str = f"{self.construct_header()}\n{locals_str}{instructions_str}{second_break}.end method"
        return method_str

    def iterate_instructions(self):
        current = self.first_instruction.next
        while current != self.last_instruction:
            yield current
            current = current.next

    def for_instruction(self, instruction_details: InstructionDetails, action: Callable[['SmaliInstruction'], None]):
        for instruction in self.iterate_instructions():
            if instruction.matches(instruction_details):
                action(instruction)


class SmaliInstruction(SmaliPiece):
    def __init__(self, line: str, parent: 'SmaliMethod'):
        self.prev: Optional['SmaliInstruction'] = None
        self.next: Optional['SmaliInstruction'] = None
        self.indent = len(line) - len(line.lstrip())
        if self.indent < 4:
            self.indent = 4
        self.original_line: str = line.strip()
        self.parent = parent
        self.details: InstructionDetails = InstructionDetails()
        self.operation: str = ''
        self.operands: str = ''
        self.parse_instruction()

    def expand_after(self, instruction_list):
        for instruction in instruction_list[::-1]:
            self.insert_after(instruction)

    def insert_after(self, instruction: 'SmaliInstruction'):
        if isinstance(instruction, str):
            if len(instruction.strip()) == 0:
                return
            instruction = SmaliInstruction(instruction, self.parent)
        instruction.next = self.next
        instruction.prev = self
        if self.next:
            self.next.prev = instruction
        self.next = instruction

    def next_known(self):
        next_instruction = self.next
        while next_instruction is not None:
            if next_instruction.details.instruction_type not in [InstructionType.UNKNOWN, InstructionType.EMPTY]:
                return next_instruction
            next_instruction = next_instruction.next
        return None

    def prev_known(self):
        prev_instruction = self.prev
        while prev_instruction is not None:
            if prev_instruction.details.instruction_type not in [InstructionType.UNKNOWN, InstructionType.EMPTY]:
                return prev_instruction
            prev_instruction = prev_instruction.prev
        return None

    def expand_before(self, instruction_list):
          for instruction in instruction_list:
              self.insert_before(instruction)

    def insert_before(self, instruction: 'SmaliInstruction'):
        if isinstance(instruction, str):
            if len(instruction.strip()) == 0:
                return
            instruction = SmaliInstruction(instruction, self.parent)
        instruction.prev = self.prev
        instruction.next = self
        if self.prev:
            self.prev.next = instruction
        self.prev = instruction

    def remove(self):
        if self.prev:
            self.prev.next = self.next
        if self.next:
            self.next.prev = self.prev
        return self.next

    def replace_multiple(self, instruction_list):
        self.expand_after(instruction_list)
        return self.remove()

    def replace(self, instruction: 'SmaliInstruction'):
        if isinstance(instruction, str):
            if len(instruction.strip()) == 0:
                return
            instruction = SmaliInstruction(instruction, self.parent)
        instruction.prev = self.prev
        instruction.next = self.next
        if self.prev:
            self.prev.next = instruction
        if self.next:
            self.next.prev = instruction

    def parse_instruction(self):
        if not self.original_line:
            self.details = InstructionDetails(
                instruction_type=InstructionType.EMPTY,
            )
            return

        if self.original_line.startswith(':'):
            self.details = InstructionDetails(
                instruction_type=InstructionType.LABEL,
                label=self.original_line
            )
            return

        parts = self.original_line.split(maxsplit=1)
        self.operation = parts[0].strip()
        self.operands = parts[1].strip() if len(parts) > 1 else ''
        self.classify_instruction()

    def classify_instruction(self):
        if self.operation.startswith(('iget', 'iput', 'sget', 'sput')):
            self.extract_field_access()
        elif self.operation.startswith('invoke'):
            self.extract_method_invoke()
        elif self.operation.startswith('const'):
            self.extract_constant()
        elif self.operation.startswith('new-array'):
            self.extract_new_array()
        elif self.operation.startswith('new-instance'):
            self.extract_new_instance()
        elif self.operation.startswith(('goto', 'if-')):
            self.extract_branch_or_condition()
        elif self.operation.startswith('move-result'):
            self.extract_move_result()
        elif self.operation.startswith('move'):
            self.extract_move()
        else:
            self.extract_unknown()

    def extract_move_result(self):
        match = re.match(r'^(?P<op>\S+)\s+(?P<reg>[pv]\d+)', self.original_line)
        if match:
            op = match.group('op')
            self.details = InstructionDetails(
                instruction_type=InstructionType.MOVE_RESULT,
                modifier=op[11:],
                registers=[match.group('reg')],
            )
        else:
            logging.warning(f'Unrecognised move-result pattern: {self.original_line}')
            self.extract_unknown()

    def extract_move(self):
        match = re.match(r'^(?P<op>\S+)\s+(?P<regs>([pv]\d+,?\s*)+)', self.original_line)
        if match:
            op = match.group('op')
            registers = [r.strip() for r in match.group('regs').strip(' \t\f,').split(',')]
            self.details = InstructionDetails(
                instruction_type=InstructionType.MOVE,
                modifier=op[4:],
                registers=registers,
            )
        else:
            logging.warning(f'Unrecognised move pattern: {self.original_line}')
            self.extract_unknown()

    def extract_field_access(self):
        match = re.match(r'^(?P<op>\S+)\s+(?P<regs>([pv]\d+,\s*)+)\s+(?P<class>\S+);->(?P<field>[^\s\:]+):(?P<type>\S+)', self.original_line)
        if match:
            op = match.group('op')
            instruction_type = InstructionType.FIELD_WRITE if 'put' in op else InstructionType.FIELD_READ
            registers = [r.strip() for r in match.group('regs').strip(' \t\f,').split(',')]
            self.details = InstructionDetails(
                instruction_type=instruction_type,
                modifier=op[4:],
                registers=registers,
                class_name=match.group('class'),
                field_name=match.group('field'),
                data_type=match.group('type'),
            )
        else:
            logging.warning(f'Unrecognised field access pattern: {self.original_line}')
            self.extract_unknown()

    def extract_method_invoke(self):
        match = re.match(r'^(?P<op>\S+)\s+\{(?P<regs>[^}]*)\},\s+(?P<class>\S+);->(?P<method>\S+)\((?P<params>[^)]*)\)(?P<ret>\S+)', self.original_line)
        if match:
            registers = [r.strip() for r in match.group('regs').strip(' \t\f,').split(',')]
            op = match.group('op')
            self.details = InstructionDetails(
                instruction_type=InstructionType.METHOD_INVOKE,
                modifier=op[6:],
                registers=registers,
                class_name=match.group('class'),
                method=match.group('method'),
                param_types=match.group('params'),
                return_type=match.group('ret')
            )
        else:
            logging.warning(f'Unrecognised method invoke pattern: {self.original_line}')
            self.extract_unknown()

    def extract_constant(self):
        match = re.match(r'^(?P<op>\S+)\s+(?P<reg>[pv]\d+),\s+(?P<value>.+)', self.original_line)
        if match:
            op = match.group('op')
            self.details = InstructionDetails(
                instruction_type=InstructionType.CONSTANT,
                modifier=op[5:],
                registers=[match.group('reg')],
                constant_value=match.group('value')
            )
        else:
            logging.warning(f'Unrecognised constant pattern: {self.original_line}')
            self.extract_unknown()

    def extract_new_array(self):
        match = re.match(r'^(\S+)\s+(?P<regs>\w+),\s+(?P<size_reg>\w+),\s+(?P<array_type>\[\S+)', self.original_line)
        if match:
            registers = [match.group('regs')]
            registers.append(match.group('size_reg'))
            self.details = InstructionDetails(
                instruction_type=InstructionType.NEW_ARRAY,
                registers=registers,
                data_type=match.group('array_type')
            )
        else:
            logging.warning(f'Unrecognised new array pattern: {self.original_line}')
            self.extract_unknown()

    def extract_new_instance(self):
        match = re.match(r'^(new-instance)\s+(?P<reg>\w+),\s+(?P<class>\S+)', self.original_line)
        if match:
            self.details = InstructionDetails(
                instruction_type=InstructionType.NEW_INSTANCE,
                registers=[match.group('reg')],
                class_name=match.group('class')
            )
        else:
            logging.warning(f'Unrecognised new instance pattern: {self.original_line}')
            self.extract_unknown()

    def extract_branch_or_condition(self):
        match = re.match(r'^(?P<op>\S+)\s+(?P<regs>([pv]\d+,\s*)+)?\s*(?P<label>:\S+)', self.original_line)
        if match:
            if match.group('regs'):
                registers = [r.strip() for r in match.group('regs').strip(' \t\f,').split(',')]
            else:
                registers = []
            self.details = InstructionDetails(
                instruction_type=InstructionType.BRANCH,
                registers=registers,
                label=match.group('label')
            )
        else:
            logging.warning(f'Unrecognised branch or condition pattern: {self.original_line}')
            self.extract_unknown()

    def extract_unknown(self):
        self.details = InstructionDetails(
            instruction_type=InstructionType.UNKNOWN,
            registers=re.findall(r'[{\s,]([pv]\d+)[,}\s]', f" {self.original_line} "),
        )

    def find_unsafe_registers(self):
        current_instruction = self.prev
        used_labels = set()
        used_registers = set()

        while current_instruction is not None:
            if current_instruction.details.instruction_type == InstructionType.LABEL:
                used_labels.add(current_instruction.details.label)
            if current_instruction.details.registers is not None:
                used_registers.update(current_instruction.details.registers)
            current_instruction = current_instruction.prev

        potential_labels = set()
        potential_registers = set()
        safe_registers = set()
        current_instruction = self
        before_jump = True

        while current_instruction is not None:
            if before_jump \
               and current_instruction.details.registers is not None \
               and current_instruction.details.instruction_type in {
                InstructionType.FIELD_READ,
                InstructionType.CONSTANT,
                InstructionType.NEW_INSTANCE,
                InstructionType.NEW_ARRAY,
                InstructionType.MOVE,
               }:
                reg = current_instruction.details.registers[0]
                if reg not in potential_registers:
                    safe_registers.add(reg)

            if current_instruction.details.instruction_type == InstructionType.LABEL:
                potential_labels.add(current_instruction.details.label)

            elif current_instruction.details.label and current_instruction.details.label in used_labels:
                before_jump = False
                used_labels |= potential_labels
                used_registers |= potential_registers

            if current_instruction.details.registers is not None:
                potential_registers.update(current_instruction.details.registers)

            current_instruction = current_instruction.next

        if before_jump:
            return potential_registers.difference(safe_registers)
        else:
            return used_registers.difference(safe_registers)

    def get_n_free_registers(self, n):
        free_registers = []
        unsafe_registers = self.find_unsafe_registers()
        for i in range(self.parent.locals + n + 2):
            if len(free_registers) >= n:
                return free_registers
            register = f'v{i}'
            if register not in unsafe_registers:
                free_registers.append(register)
                if i >= self.parent.locals:
                    self.parent.locals = i + 1

    def __str__(self):
        from_type = self.str_from_type()
        if len(from_type) == 0:
            return ''
        return (' ' * self.indent) + from_type

    def str_from_type(self):
        if self.details.instruction_type.matches([InstructionType.FIELD_WRITE, InstructionType.FIELD_READ]):
            op = self.operation[0] + ("put" if self.details.instruction_type == InstructionType.FIELD_WRITE else "get")
            return f"{op}{self.details.modifier} {', '.join(self.details.registers)}, {self.details.class_name};->{self.details.field_name}:{self.details.data_type}"
        elif self.details.instruction_type.matches(InstructionType.METHOD_INVOKE):
            registers = ', '.join(self.details.registers)
            return f"invoke{self.details.modifier} {{{registers}}}, {self.details.class_name};->{self.details.method}({self.details.param_types}){self.details.return_type}"
        elif self.details.instruction_type.matches(InstructionType.CONSTANT):
            return f"const{self.details.modifier} {self.details.registers[0]}, {self.details.constant_value}"
        elif self.details.instruction_type.matches(InstructionType.NEW_ARRAY):
            return f"{self.operation} {self.details.registers[0]}, {self.details.registers[1]}, {self.details.data_type}"
        elif self.details.instruction_type.matches(InstructionType.NEW_INSTANCE):
            return f"{self.operation} {self.details.registers[0]}, {self.details.class_name}"
        elif self.details.instruction_type.matches(InstructionType.BRANCH):
            registers = ', '.join(self.details.registers) + (', ' if self.details.registers and len(self.details.registers) > 0 else '')
            return f"{self.operation} {registers}{self.details.label}"
        elif self.details.instruction_type.matches(InstructionType.LABEL):
            return self.details.label
        elif self.details.instruction_type.matches(InstructionType.MOVE_RESULT):
            return f"move-result{self.details.modifier} {self.details.registers[0]}"
        elif self.details.instruction_type.matches(InstructionType.MOVE):
            registers = ', '.join(self.details.registers)
            return f"move{self.details.modifier} {registers}"
        return self.original_line

    def matches(self, search_details: InstructionDetails) -> bool:
        return search_details.matches(self.details)

class SmaliField(SmaliPiece):
    def __init__(self, line: str, parent: Any):
        match = re.match(r'\.field\s+(?P<modifiers>(\S+[\t \f]+)*)(?P<name>[^\s:]+):(?P<type>[^\s=]+)(\s*=\s*(?P<value>[^\n]+))?', line)
        if match:
            self.line = None
            self.details = FieldDetails(
                name = match.group('name'),
                type = match.group('type'),
                modifiers = list(map(str.strip, match.group('modifiers').strip().split())),
                value = match.group('value').strip() if match.group('value') is not None else None
            )
        else:
            self.line = line
            self.details = FieldDetails()
        self.parent = parent

    def matches(self, search_details: FieldDetails) -> bool:
        return search_details.matches(self.details)

    def __str__(self):
        if self.line:
            return self.line
        value_str = f' = {self.details.value}' if isinstance(self.details.value, str) and len(self.details.value) > 0 else ''
        modifiers_str = (' '.join(self.details.modifiers) if self.details.modifiers is not None else '') + (' ' if len(self.details.modifiers) > 0 else '')
        return f'.field {modifiers_str}{self.details.name}:{self.details.type}{value_str}'

def replace_file(dst, perms = 0o644, owner = "root:root", secontext = "u:object_r:system_file:s0", src = None):
    logging.info(f"Replacing {dst}...")
    if src is None:
        _, file_name = os.path.split(dst)
        src = f"../{file_name}"

    if not os.path.isfile(src):
        logging.warning(f"Replacement file {src} doesn't exist. Skipping.")
        return False

    if os.path.isfile(dst):
        logging.info(f"Removing old file.")
        os.remove(dst)

    shutil.copy(src, dst)
    os.chmod(dst, perms)
    subprocess.run(["chown", owner, dst])
    subprocess.run(["setfattr", "-n", "security.selinux", "-v", secontext, dst])
    return True
