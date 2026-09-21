# Lenovo Pen Haptic Strength Research

Research on the ZUI stylus-pen haptic feedback chain, aimed at understanding where the
vibration-strength limit lives and what would be needed to lift it. This is analysis
material only — nothing here is wired into the ZTool hook modules yet.

## Directory contents

```text
docs/research/lenovo-pen-haptic/
  README.md            This file
  haptic-chain.md      Level setting chain: UI -> Settings -> system service -> BLE -> firmware
  firmware-ota.md      Reproducible pipeline to fetch pen firmware from ota.lenovo.com
  firmware/            Downloaded OTA package contents (version 1.74)
    manifest.json      Nordic DFU package manifest
    PARKER.bin         ARM Cortex-M firmware image (303,576 bytes)
    PARKER.dat         Nordic DFU init packet (141 bytes)
```

## Status

- [x] App-side scope of `com.lenovo.penservice` mapped
- [x] Haptic level chain traced end-to-end on the tablet side (app + services.jar)
- [x] Firmware download pipeline reproduced locally (SHA256 verified)
- [ ] IDA analysis of `PARKER.bin`: level -> waveform table, saturation behavior above level 5
- [ ] Prototype hook decision (services.jar parameter check vs. firmware-only outcome)
