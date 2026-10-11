"""Regenerate the ZUI component/style catalog from an APK.

Two independent passes, both read-only and decompiler-free:

    classes <apk>   class descriptors under Lzui/ and Lcom/zui/internal/app/,
                    read from the dex string tables
    keys <apk>      resource key names, read from the resources.arsc string pools
    keys <apk> <re> only keys matching a regular expression

The dex pass is exact. The arsc pass is a string-pool heuristic: it scans printable
runs, so a key can carry a printable length prefix. Strip leading non-identifier
characters before comparing.
"""

import re
import struct
import sys
import zipfile

CLASS_PREFIXES = ("Lzui/", "Lcom/zui/internal/app/")
ASCII_RUN = re.compile(rb"[\x20-\x7e]{4,}")
UTF16_RUN = re.compile(rb"(?:[\x20-\x7e]\x00){4,}")


def _uleb128(data, off):
    result = 0
    shift = 0
    while True:
        byte = data[off]
        off += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, off
        shift += 7


def _dex_strings(blob):
    string_ids_size, string_ids_off = struct.unpack_from("<II", blob, 56)
    for i in range(string_ids_size):
        (data_off,) = struct.unpack_from("<I", blob, string_ids_off + i * 4)
        _, pos = _uleb128(blob, data_off)
        end = blob.index(b"\x00", pos)
        yield blob[pos:end].decode("latin-1")


def _dex_entries(apk):
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                yield archive.read(name)


def _arsc_strings(apk):
    with zipfile.ZipFile(apk) as archive:
        blob = archive.read("resources.arsc")
    seen = set()
    for match in ASCII_RUN.finditer(blob):
        seen.add(match.group().decode("latin-1"))
    for match in UTF16_RUN.finditer(blob):
        seen.add(match.group().decode("utf-16-le"))
    return seen


def dump_classes(apk):
    found = set()
    for blob in _dex_entries(apk):
        for value in _dex_strings(blob):
            if value.startswith(CLASS_PREFIXES) and value.endswith(";"):
                found.add(value[1:-1])
    for name in sorted(found):
        print(name)
    print(f"# {len(found)} classes", file=sys.stderr)


def dump_keys(apk, pattern=None):
    want = re.compile(pattern) if pattern else None
    keys = {re.sub(r"^[^A-Za-z_]+", "", s) for s in _arsc_strings(apk)}
    keys = {k for k in keys if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_.]*", k)}
    if want:
        keys = {k for k in keys if want.search(k)}
    for key in sorted(keys):
        print(key)
    print(f"# {len(keys)} keys", file=sys.stderr)


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "classes":
        dump_classes(sys.argv[2])
    elif command == "keys":
        dump_keys(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    else:
        raise SystemExit(f"unknown command: {command}")
