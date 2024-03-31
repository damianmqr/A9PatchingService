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

static const char* kTAG = "a9EinkService";
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, kTAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, kTAG, __VA_ARGS__))

#define SOCKET_NAME "0a9_eink_socket"
#define BUFFER_SIZE 256

int valid_number(const char *s) {
    if(strlen(s) > 4 || strlen(s) == 0)
        return 0;
    
    while (*s)
        if (isdigit(*s++) == 0) return 0;

    return 1;
}

void disableHWOverlays() {
    int status = system("service call SurfaceFlinger 1008 i32 1 > /dev/null");
    if (status == -1) {
        LOGE("Failed to disable HW overlays");
    }
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
        disableHWOverlays();
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