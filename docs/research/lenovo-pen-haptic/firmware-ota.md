# Pen Firmware OTA Pipeline (Reproduced)

The firmware update flow of `com.lenovo.penservice` is plain HTTPS with no signature or
auth headers (Retrofit + OkHttp, BASE_URL `https://ota.lenovo.com/`). The pipeline below
was reproduced with `curl` on 2026-09-21 and fetched firmware 1.74 for a Lenovo Tab Pen
Pro (Parker) with SN `HVE10CZ7`.

## Step 1 — query available package

```
GET https://ota.lenovo.com/engine/config
    ?ChecksumType=sha256
    &locale=en
    &curfirmwarever=TB-styluspen_USR_S000001_20211018_V1
    &deviceid=<pen SN>
    &devicemodel=<penModel>
    &action=querynewfirmwar
```

- `action` is intentionally the server's misspelled literal `querynewfirmwar`.
- `curfirmwarever` is a fixed protocol-version constant; firmware uses
  `_20211018_V1`, handwriting language packs use `_20211025_V1`.
- Response is JSON. `update_packages_data` carries the latest `version_code`
  (e.g. 174 -> "V1.74") and `data_package_name`
  (e.g. `com.lenovo.topaz_prc_wifi_LenovoTabPenPro-V1`).

## penModel composition

`BtPenModels.getPenModel()` builds:

```
penNameNoSpace + "_" + penHwModel + "_" + penPid + "_" + penVid + "_" + padModelNoSpace + "_" + region
```

Real device example (Tab Pad TB710FU, PRC):

```
LenovoTabPenPro_PARKER-V1_0x61A1_0x17EF_TB710FU_prc
```

Region suffix is lowercase `prc` / `row` (from SystemProps), not uppercase. The fastest
way to obtain the exact string is logcat:

```
adb shell "logcat -d | grep 'penModel:'"
```

`deviceid` is the pen SN stored in the app prefs
(`/data/data/com.lenovo.penservice/shared_prefs/com.lenovo.penservice.preferences.xml`,
keys `pen_sn`, `pen_hw_model`, `pen_hw_version`, `pen_pid`, `pen_vid`, `pen_name`).

## Step 2 — obtain the download URL

```
POST https://ota.lenovo.com/engine/upgrade?<same query as step 1>
Content-Type: application/json

{"update_packages": <update_packages from step 1>,
 "update_packages_data": <update_packages_data from step 1>}
```

Response is XML (`@ResponseConverter("xml")` in `PenService`):

```xml
<firmwareupdate>
  <packageappdatas>
    <packageappdata>
      <versionname>1.74</versionname>
      <filename>Parker_20260409_ota_package_1_74.zip</filename>
      <sha256>ef0b456e4222289e75362863ffe810354c4e08de7a05617a6f187a27d22b96ad</sha256>
      <size>304177</size>
      <downloadurl>https://ota-cdn.lenovo.com/package/&lt;id&gt;.zip</downloadurl>
    </packageappdata>
  </packageappdatas>
</firmwareupdate>
```

## Step 3 — download

Plain GET of `downloadurl`; the app sends a `RANGE` header for resumable downloads.
Verify SHA256. The zip contains:

- `manifest.json` — Nordic DFU manifest
- `PARKER.bin` — bare ARM Cortex-M image (initial SP 0x2000C738, reset 0x27494 Thumb,
  vector table intact, not encrypted)
- `PARKER.dat` — Nordic DFU init packet

A copy of the 1.74 package lives in `firmware/`.

## Notes

- Failure code `FUS-0112 Invalid device model` means the `devicemodel` string is wrong;
  the endpoint itself needs no other credentials.
- The in-app check compares pen hardware version `V1.74` against `zipHwVersion`
  (`"V" + version_code` with a dot inserted after the first digit) and skips download
  when already latest — a direct API query does not have this client-side gate.
- Firmware update transport to the pen is Nordic DFU (`no.nordicsemi.android.dfu`
  bundled in the app; `FirmwareUpgradeService`), driven over the same BLE GATT channel
  the haptic service uses.
