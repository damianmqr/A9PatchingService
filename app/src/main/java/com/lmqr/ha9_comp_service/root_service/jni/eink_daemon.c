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

static const char* kTAG = "a9EinkService";
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, kTAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, kTAG, __VA_ARGS__))

#define SOCKET_NAME "0a9_eink_socket"
#define BUFFER_SIZE 512

int valid_number(const char *s) {
    if(strlen(s) > 4 || strlen(s) == 0)
        return 0;

    while (*s)
        if (isdigit(*s++) == 0) return 0;

    return 1;
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

void setYellowBrightness(const char* brightness) {
    const char* ledPath = "/sys/class/leds/aw99703-bl-1/brightness";
    if(!valid_number(brightness)){
        LOGE("Error writing to %s: Invalid Number\n", ledPath);
        return;
    }
    int fd = open(ledPath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
        return;
    }
    if (write(fd, brightness, strlen(brightness)) == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
    }
    close(fd);
}

void setWhiteBrightness(const char* brightness) {
    const char* ledPath = "/sys/class/leds/aw99703-bl-2/brightness";
    if(!valid_number(brightness)){
        LOGE("Error writing to %s: Invalid Number\n", ledPath);
        return;
    }
    int fd = open(ledPath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
        return;
    }
    if (write(fd, brightness, strlen(brightness)) == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
    }
    close(fd);
}

void setYellowBrightnessAlt(const char* brightness) {
    const char* ledPath = "/sys/class/backlight/aw99703-bl-1/brightness";
    if(!valid_number(brightness)){
        LOGE("Error writing to %s: Invalid Number\n", ledPath);
        return;
    }
    int fd = open(ledPath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
        return;
    }
    if (write(fd, brightness, strlen(brightness)) == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
    }
    close(fd);
}

void setWhiteBrightnessAlt(const char* brightness) {
    const char* ledPath = "/sys/class/backlight/aw99703-bl-2/brightness";
    if(!valid_number(brightness)){
        LOGE("Error writing to %s: Invalid Number\n", ledPath);
        return;
    }
    int fd = open(ledPath, O_WRONLY);
    if (fd == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
        return;
    }
    if (write(fd, brightness, strlen(brightness)) == -1) {
        LOGE("Error writing to %s: %s\n", ledPath, strerror(errno));
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

void setShutoffImage(const char *source_path) {
    const char* TARGET_FILE = "/kdebuginfo/edpd/bitmap_low.raw";
    struct stat statbuf;
    int source_fd, target_fd;
    char buffer[4096];
    ssize_t bytes_read, bytes_written;

    if (stat(source_path, &statbuf) != 0) {
        LOGE("Failed to get source file stats");
        return;
    }

    if (statbuf.st_size != 2715904) {
        LOGE("Shutoff screen size does not match the expected size");
        return;
    }

    source_fd = open(source_path, O_RDONLY);
    if (source_fd < 0) {
        LOGE("Failed to open source file");
        return;
    }

    target_fd = open(TARGET_FILE, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (target_fd < 0) {
        LOGE("Failed to open target file");
        close(source_fd);
        return;
    }

    while ((bytes_read = read(source_fd, buffer, sizeof(buffer))) > 0) {
        bytes_written = write(target_fd, buffer, bytes_read);
        if (bytes_written != bytes_read) {
            LOGE("Failed to write all bytes to target file");
            close(source_fd);
            close(target_fd);
            return;
        }
    }

    close(source_fd);
    close(target_fd);

    if (chown(TARGET_FILE, 1000, 1000) != 0) {
        perror("Failed to change file owner");
        return;
    }

    if (chmod(TARGET_FILE, S_IRUSR | S_IWUSR) != 0) {
        perror("Failed to change file permissions");
        return;
    }

    if (setxattr(TARGET_FILE, "security.selinux", "u:object_r:kdebuginfo_data_file:s0", 31, 0) != 0) {
        perror("Failed to set SELinux context");
        return;
    }
}

void blockYellowBrightness() {
    chmod("/sys/class/leds/aw99703-bl-1/brightness", 0444);
}

void blockWhiteBrightness() {
    chmod("/sys/class/leds/aw99703-bl-2/brightness", 0444);
}

void unblockYellowBrightness() {
    chmod("/sys/class/leds/aw99703-bl-1/brightness", 0644);
}

void unblockWhiteBrightness() {
    chmod("/sys/class/leds/aw99703-bl-2/brightness", 0644);
}

void processCommand(const char* command) {
    if (strcmp(command, "setup") == 0) {
        ;
    } else if (strcmp(command, "cm") == 0) {
        epdCommitBitmap();
    } else if (strcmp(command, "bl") == 0) {
        setWhiteBrightness("0");
        blockWhiteBrightness();
    } else if (strcmp(command, "un") == 0) {
        unblockWhiteBrightness();
    } else if (strcmp(command, "bl1") == 0) {
        setYellowBrightness("0");
        blockYellowBrightness();
    } else if (strcmp(command, "un1") == 0) {
        unblockYellowBrightness();
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
    } else if (strncmp(command, "sb1", 3) == 0) {
        if(valid_number(command+3))
            setYellowBrightness(command+3);
    } else if (strncmp(command, "sb2", 3) == 0) {
        if(valid_number(command+3))
            setWhiteBrightness(command+3);
    }  else if (strncmp(command, "sa1", 3) == 0) {
        if(valid_number(command+3))
            setYellowBrightnessAlt(command+3);
    } else if (strncmp(command, "sa2", 3) == 0) {
        if(valid_number(command+3))
            setWhiteBrightnessAlt(command+3);
    } else if (strncmp(command, "stw", 3) == 0) {
        if(valid_number(command+3))
            setWhiteThreshold(command+3);
    } else if (strncmp(command, "stb", 3) == 0) {
        if(valid_number(command+3))
            setBlackThreshold(command+3);
    } else if (strncmp(command, "sco", 3) == 0) {
        if(valid_number(command+3))
            setContrast(command+3);
    } else if (strncmp(command, "sso", 3) == 0) {
        setShutoffImage(command+3);
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