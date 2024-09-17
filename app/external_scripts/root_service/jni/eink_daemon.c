#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <errno.h>
#include <android/log.h>
#include <ctype.h>
#include <sys/xattr.h>
#include <sys/wait.h>
#include <signal.h>

static const char* kTAG = "a9EinkService";
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, kTAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, kTAG, __VA_ARGS__))

#define SOCKET_NAME "0a9_eink_socket"
#define BUFFER_SIZE 512

static const char* theme_styles[] = {
        "TONAL_SPOT",
        "VIBRANT",
        "RAINBOW",
        "EXPRESSIVE",
        "FRUIT_SALAD",
        "SPRITZ"
};

int valid_number(const char *s) {
    if(strlen(s) > 4 || strlen(s) == 0)
        return 0;

    while (*s)
        if (isdigit(*s++) == 0) return 0;

    return 1;
}

int valid_hex_color(const char *s) {
    if (strlen(s) != 6)
        return 0;

    while (*s)
        if (!isxdigit(*s++)) return 0;

    return 1;
}

void sanitize_input(int theme_style_index, const char* hex_color, char* sanitized_theme_style, char* sanitized_hex_color) {
    if (theme_style_index < 0 || theme_style_index > 5) {
        theme_style_index = 5; // Default to monotone
    }
    strcpy(sanitized_theme_style, theme_styles[theme_style_index]);

    if (!valid_hex_color(hex_color)) {
        strcpy(sanitized_hex_color, "333333");
    } else {
        strcpy(sanitized_hex_color, hex_color);
    }
}

void generate_json_string(char* json_str, const char* theme_style, const char* hex_color) {
    sprintf(json_str, "{\"android.theme.customization.theme_style\":\"%s\",\"android.theme.customization.color_source\":\"preset\",\"android.theme.customization.system_palette\":\"%s\"}", theme_style, hex_color);
}

void execute_settings_command(const char* json_str) {
    pid_t pid = fork();
    if (pid == 0) {
        execl("/system/bin/settings", "settings", "put", "secure", "theme_customization_overlay_packages", json_str, (char *)NULL);
        LOGE("execlp failed: %s", strerror(errno));
        exit(EXIT_FAILURE);
    } else if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
    } else {
        wait(NULL);
    }
}

void applyThemeCustomization(int theme_style_index, const char* hex_color) {
    char sanitized_theme_style[20];
    char sanitized_hex_color[7];
    char json_str[BUFFER_SIZE];

    sanitize_input(theme_style_index, hex_color, sanitized_theme_style, sanitized_hex_color);
    generate_json_string(json_str, sanitized_theme_style, sanitized_hex_color);
    execute_settings_command(json_str);
}

void epdForceClear() {
    const char* filePath = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_force_clear";
    int fd = open(filePath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", filePath, strerror(errno));
        return;
    }
    if (write(fd, "1", 1) == -1) {
        LOGE("Error writing to %s: %s\n", filePath, strerror(errno));
    }
    close(fd);
}

void epdCommitBitmap() {
    const char* filePath = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_commit_bitmap";
    int fd = open(filePath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", filePath, strerror(errno));
        return;
    }
    if (write(fd, "1", 1) == -1) {
        LOGE("Error writing to %s: %s\n", filePath, strerror(errno));
    }
    close(fd);
}

void writeToEpdDisplayMode(const char* value) {
    const char* filePath = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode";
    if(!valid_number(value)){
        LOGE("Error writing to %s: Invalid Number\n", filePath);
        return;
    }

    int fd = open(filePath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", filePath, strerror(errno));
        return;
    }
    if (write(fd, value, strlen(value)) == -1) {
        LOGE("Error writing to %s: %s\n", filePath, strerror(errno));
    }
    close(fd);
}

void setWhiteThreshold(const char* brightness) {
    const char* whiteThresholdPath ="/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_white_threshold";
    if(!valid_number(brightness)){
        LOGE("Error writing to %s: Invalid Number\n", whiteThresholdPath);
        return;
    }
    int fd = open(whiteThresholdPath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", whiteThresholdPath, strerror(errno));
        return;
    }
    if (write(fd, brightness, strlen(brightness)) == -1) {
        LOGE("Error writing to %s: %s\n", whiteThresholdPath, strerror(errno));
    }
    close(fd);
}

void setBlackThreshold(const char* brightness) {
    const char* blackThresholdPath = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_black_threshold";
    if(!valid_number(brightness)){
        LOGE("Error writing to %s: Invalid Number\n", blackThresholdPath);
        return;
    }
    int fd = open(blackThresholdPath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", blackThresholdPath, strerror(errno));
        return;
    }
    if (write(fd, brightness, strlen(brightness)) == -1) {
        LOGE("Error writing to %s: %s\n", blackThresholdPath, strerror(errno));
    }
    close(fd);
}

void setContrast(const char* brightness) {
    const char* contrastPath = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_contrast";
    if(!valid_number(brightness)){
        LOGE("Error writing to %s: Invalid Number\n", contrastPath);
        return;
    }
    int fd = open(contrastPath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", contrastPath, strerror(errno));
        return;
    }
    if (write(fd, brightness, strlen(brightness)) == -1) {
        LOGE("Error writing to %s: %s\n", contrastPath, strerror(errno));
    }
    close(fd);
}

void writeLockscreenProp(const char* value) {
    if(!valid_number(value)){
        LOGE("Error setting static lockscreen: Invalid Number\n");
        return;
    }
    pid_t pid = fork();
    if (pid == 0) {
        execl("/system/bin/setprop", "setprop", "sys.linevibrator_type", value, (char *)NULL);
        LOGE("execlp failed: %s", strerror(errno));
        exit(EXIT_FAILURE);
    } else if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
    } else {
        wait(NULL);
    }
}

void writeMaxBrightnessProp(const char* value) {
    if(!valid_number(value)){
        LOGE("Error setting static lockscreen: Invalid Number\n");
        return;
    }
    pid_t pid = fork();
    if (pid == 0) {
        execl("/system/bin/setprop", "setprop", "sys.linevibrator_touch", value, (char *)NULL);
        LOGE("execlp failed: %s", strerror(errno));
        exit(EXIT_FAILURE);
    } else if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
    } else {
        wait(NULL);
    }
}

void writeWakeOnVolumeProp(const char* value) {
    if(!valid_number(value)){
        LOGE("Error setting static lockscreen: Invalid Number\n");
        return;
    }
    pid_t pid = fork();
    if (pid == 0) {
        execl("/system/bin/setprop", "setprop", "sys.wakeup_on_volume", value, (char *)NULL);
        LOGE("execlp failed: %s", strerror(errno));
        exit(EXIT_FAILURE);
    } else if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
    } else {
        wait(NULL);
    }
}

void processCommand(const char* command) {
    if (strcmp(command, "cm") == 0) {
        epdCommitBitmap();
    } else if (strcmp(command, "r") == 0) {
        epdForceClear();
    } else if (strcmp(command, "c") == 0) {
        writeToEpdDisplayMode("515");
    } else if (strcmp(command, "b") == 0) {
        writeToEpdDisplayMode("513");
    } else if (strcmp(command, "s") == 0) {
        writeToEpdDisplayMode("518");
    } else if (strcmp(command, "p") == 0) {
        writeToEpdDisplayMode("521");
    } else if (strncmp(command, "stw", 3) == 0) {
        if(valid_number(command+3))
            setWhiteThreshold(command+3);
    } else if (strncmp(command, "stl", 3) == 0) {
        if(valid_number(command+3))
            writeLockscreenProp(command+3);
    } else if (strncmp(command, "stb", 3) == 0) {
        if(valid_number(command+3))
            setBlackThreshold(command+3);
    } else if (strncmp(command, "sco", 3) == 0) {
        if(valid_number(command+3))
            setContrast(command+3);
    } else if (strncmp(command, "smb", 3) == 0) {
        if(valid_number(command+3))
            writeMaxBrightnessProp(command+3);
    } else if (strncmp(command, "wov", 3) == 0) {
        if(valid_number(command+3))
            writeWakeOnVolumeProp(command+3);
    } else if (strncmp(command, "theme", 5) == 0) {
        int theme_style_index;
        char hex_color[7];
        if (sscanf(command + 5, "%d %6s", &theme_style_index, hex_color) == 2) {
            applyThemeCustomization(theme_style_index, hex_color);
        } else {
            LOGE("Invalid theme command format");
        }
    } else {
        LOGE("Unknown command: %s", command);
    }
}

_Noreturn void setupServer() {
    int server_sockfd, client_sockfd;
    struct sockaddr_un server_addr;
    char buffer[BUFFER_SIZE];
    socklen_t socket_length = sizeof(server_addr.sun_family) + strlen(SOCKET_NAME);

    server_sockfd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_sockfd < 0) {
        LOGE("Socket creation failed: %s", strerror(errno));
        exit(EXIT_FAILURE);
    }

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sun_family = AF_UNIX;
    strncpy(server_addr.sun_path, SOCKET_NAME, sizeof(server_addr.sun_path) - 1);
    server_addr.sun_path[0] = 0;

    if (bind(server_sockfd, (struct sockaddr*)&server_addr, socket_length) < 0) {
        LOGE("Socket bind failed: %s", strerror(errno));
        close(server_sockfd);
        exit(EXIT_FAILURE);
    }

    if (listen(server_sockfd, 50) < 0) {
        LOGE("Socket listen failed: %s", strerror(errno));
        close(server_sockfd);
        exit(EXIT_FAILURE);
    }

    LOGI("Server started listening.");

    while (1) {
        client_sockfd = accept(server_sockfd, NULL, NULL);
        if (client_sockfd < 0) {
            LOGE("Socket accept failed: %s", strerror(errno));
            continue;
        }

        while (1) {
            memset(buffer, 0, BUFFER_SIZE);
            ssize_t num_read = read(client_sockfd, buffer, BUFFER_SIZE - 1);
            if (num_read > 0) {
                buffer[num_read] = '\0';
                char* cmd = strtok(buffer, "\n");
                while (cmd != NULL) {
                    processCommand(cmd);
                    cmd = strtok(NULL, "\n");
                }
            } else if (num_read == 0) {
                LOGI("Client disconnected");
                break;
            } else {
                LOGE("Socket read failed: %s", strerror(errno));
                break;
            }
        }

        close(client_sockfd);
    }

    close(server_sockfd);
}

int main(void) {
    signal(SIGHUP, SIG_IGN);
    setupServer();
    return 0;
}