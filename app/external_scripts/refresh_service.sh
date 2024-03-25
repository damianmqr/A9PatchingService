#!/system/bin/sh

sleep 15
(
trap '' HUP
FIFO_PATH="$(awk '/^com.lmqr.ha9_comp_service/ {print $4}' /data/system/packages.list)/files/refresh_screen_fifo"

rm -f "$FIFO_PATH"
mkfifo "$FIFO_PATH"
chmod 600 "$FIFO_PATH"
chown "$(awk '/^com.lmqr.ha9_comp_service/ {print $2}' /data/system/packages.list)" "$FIFO_PATH"

setenforce 0

# Infinite loop to read from the FIFO
while true
do
  if read -r line; then
      case "$line" in
        'setup')
          service call SurfaceFlinger 1008 i32 1
          settings put global window_animation_scale 0
          settings put global transition_animation_scale 0
          settings put global animator_duration_scale 0
          setenforce 1
        ;;
        'r')# Force refresh
          echo 1 > "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_force_clear"
        ;;
        'c')# Clear
          echo 515 > "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode"
        ;;
        'b')# Balanced
          echo 513 > "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode"
        ;;
        's')# Smooth
          echo 518 > "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode"
        ;;
        'p')# Speed
          echo 521 > "/sys/devictrap '' HUPes/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode"
        ;;
        "bl")# Block display
          echo 0 > "/sys/class/leds/aw99703-bl-2/brightness"
          chmod 444 "/sys/class/leds/aw99703-bl-2/brightness"
        ;;
        "un")# Unblock display
          chmod 644 "/sys/class/leds/aw99703-bl-2/brightness"
        ;;
        "bl1")# Block display
          echo 0 > "/sys/class/leds/aw99703-bl-1/brightness"
          chmod 444 "/sys/class/leds/aw99703-bl-1/brightness"
        ;;
        "un1")# Unblock display
          chmod 644 "/sys/class/leds/aw99703-bl-1/brightness"
        ;;
        "sb1"[0-9]|"sb1"[0-9][0-9]|"sb1"[0-9][0-9][0-9]|"sb1"[0-9][0-9][0-9][0-9])# Set brightness 1
            BR="${line#???}"
            if [ "$BR" -lt 2200 ]; then
              echo "$BR" > "/sys/class/leds/aw99703-bl-1/brightness"
            fi
        ;;
        "sb2"[0-9]|"sb2"[0-9][0-9]|"sb2"[0-9][0-9][0-9]|"sb2"[0-9][0-9][0-9][0-9])# Set brightness 2
            BR="${line#???}"
            if [ "$BR" -lt 2200 ]; then
              echo "$BR" > "/sys/class/leds/aw99703-bl-2/brightness"
            fi
        ;;
      esac
  fi
done <"$FIFO_PATH") &