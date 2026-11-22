#!/usr/bin/env python3
"""Discover and verify the official RuStore client versionCode for Wy Store."""

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import struct
import subprocess
import sys
import tempfile
import urllib.error
from urllib.parse import urlparse
import urllib.request
import zipfile


REPOSITORY = re.compile(r"[0-9A-Za-z_.-]+/[0-9A-Za-z_.-]+")
VERSION_NAME = re.compile(r"[0-9]+(?:\.[0-9]+)+")
APK_URL = re.compile(r"https://[A-Za-z0-9.-]*rustore\.ru/[^\s\"'<>\\]+\.apk(?:\?[^\s\"'<>\\]*)?")
MOBILE_APK_VERSION = re.compile(r"/RuStore-mobile-([0-9]+(?:\.[0-9]+)+)\.apk(?:$|\?)", re.IGNORECASE)

DISCOVERY_URLS = (
    "https://www.rustore.ru/instruction",
    "https://www.rustore.ru/",
)
OVERALL_INFO_URL = "https://backapi.rustore.ru/applicationData/overallInfo/{}"
DOWNLOAD_LINK_URL = "https://backapi.rustore.ru/applicationData/v2/download-link"
PROBE_PACKAGE = "com.vkontakte.android"

EXPECTED_PACKAGE = "ru.vk.store"
# Official RuStore client signing certificate (APK Signature Scheme v2/v3).
EXPECTED_CERT_SHA256 = "661f20828ef780de0b79bc59f26a30864316355f30e4f91cfa14a20791839914"
# Below 1059001 RuStore falls back to legacy .apk download links where split-only apps 404.
MIN_VERSION_CODE = 1_059_001
MAX_APK_BYTES = 250 * 1024 * 1024
USER_AGENT = "WyStore/version-check"

REPORT_BRANCH = "release-reports"
REPORT_PATH = "rustore-version.json"
COMMIT_IDENTITY = {"name": "wyrtensi", "email": "wyrtensi@gmail.com"}

# Android binary XML (AXML) constants from frameworks/base/libs/androidfw/ResourceTypes.h.
RES_STRING_POOL_TYPE = 0x0001
RES_XML_TYPE = 0x0003
RES_XML_RESOURCE_MAP_TYPE = 0x0180
RES_XML_START_ELEMENT_TYPE = 0x0102
STRING_POOL_UTF8_FLAG = 0x0100

ATTR_VERSION_CODE = 0x0101021B
ATTR_VERSION_NAME = 0x0101021C
ATTR_VERSION_CODE_MAJOR = 0x01010576

TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
APK_SIG_SCHEME_V2_ID = 0x7109871A
APK_SIG_SCHEME_V3_ID = 0xF05368C0


def is_trusted_rustore_url(url):
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower()
    return parsed.scheme == "https" and (host == "rustore.ru" or host.endswith(".rustore.ru"))


class _TrustedRuStoreRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not is_trusted_rustore_url(newurl):
            raise ValueError(f"Refusing to follow redirect to untrusted host: {newurl}")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _rustore_ssl_context():
    """Default system CAs plus the Russian Trusted CAs bundled with Wy Store for *.rustore.ru."""
    context = ssl.create_default_context()
    raw_dir = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "raw"
    for cert_name in ("russian_trusted_root_ca.cer", "russian_trusted_sub_ca.cer"):
        cert_path = raw_dir / cert_name
        if cert_path.is_file():
            context.load_verify_locations(cafile=str(cert_path))
    return context


def _rustore_opener():
    return urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=_rustore_ssl_context()),
        _TrustedRuStoreRedirectHandler(),
    )


def http_request(url, *, headers=None, data=None, method="GET", timeout=30, max_bytes=None):
    """Issue a single HTTPS request and return (status, final_url, body_bytes)."""
    request = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    opener = _rustore_opener() if is_trusted_rustore_url(url) else urllib.request.build_opener()
    try:
        with opener.open(request, timeout=timeout) as response:
            final_url = response.geturl()
            body = response.read(max_bytes) if max_bytes is not None else response.read()
            return response.status, final_url, body
    except urllib.error.HTTPError as error:
        body = error.read(4096)
        return error.code, error.geturl(), body


def extract_apk_url(html):
    """Extract the official mobile RuStore APK URL and advertised versionName from page HTML."""
    candidates = []
    for match in APK_URL.findall(html):
        if not is_trusted_rustore_url(match):
            continue
        path = urlparse(match).path.lower()
        if "rustoretv" in path or "tv.apk" in path:
            continue
        if match not in candidates:
            candidates.append(match)
    for url in candidates:
        version_match = MOBILE_APK_VERSION.search(url)
        if version_match:
            return url, version_match.group(1)
    for url in candidates:
        if "rustore" in urlparse(url).path.lower():
            return url, None
    raise ValueError("Could not find an official mobile RuStore APK link on the RuStore page.")


def discover_apk_url(urls=DISCOVERY_URLS):
    last_error = None
    for page_url in urls:
        try:
            status, final_url, body = http_request(
                page_url,
                headers={"User-Agent": USER_AGENT},
                timeout=30,
            )
            if status != 200:
                raise ValueError(f"Discovery page {page_url} returned HTTP {status}.")
            if not is_trusted_rustore_url(final_url):
                raise ValueError(f"Discovery page redirected to untrusted URL: {final_url}")
            return extract_apk_url(body.decode("utf-8", errors="replace"))
        except (OSError, ValueError) as error:
            last_error = error
    raise ValueError(f"Failed to discover official RuStore APK URL: {last_error}")


def download_apk(url, destination):
    if not is_trusted_rustore_url(url):
        raise ValueError(f"Refusing to download APK from untrusted URL: {url}")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    opener = _rustore_opener()
    total = 0
    with opener.open(request, timeout=120) as response:
        if response.status != 200:
            raise ValueError(f"APK download failed with HTTP {response.status}.")
        if not is_trusted_rustore_url(response.geturl()):
            raise ValueError(f"APK download redirected to untrusted URL: {response.geturl()}")
        with destination.open("wb") as stream:
            while chunk := response.read(1024 * 1024):
                total += len(chunk)
                if total > MAX_APK_BYTES:
                    raise ValueError(f"Downloaded APK exceeds maximum allowed size ({MAX_APK_BYTES} bytes).")
                stream.write(chunk)
    if total == 0:
        raise ValueError("Downloaded APK is empty.")
    return destination


def _parse_string_pool(data, offset, size):
    if size < 28 or offset + size > len(data):
        raise ValueError("Truncated AXML string pool chunk.")
    (
        _chunk_type,
        header_size,
        _chunk_size,
        string_count,
        _style_count,
        flags,
        strings_start,
        _styles_start,
    ) = struct.unpack_from("<HHIIIIII", data, offset)
    if header_size < 28 or header_size + string_count * 4 > size:
        raise ValueError("Invalid AXML string pool header.")
    utf8 = bool(flags & STRING_POOL_UTF8_FLAG)
    offsets_base = offset + header_size
    data_base = offset + strings_start
    chunk_end = offset + size
    strings = []
    for index in range(string_count):
        str_offset = struct.unpack_from("<I", data, offsets_base + index * 4)[0]
        pos = data_base + str_offset
        if pos >= chunk_end:
            raise ValueError("String pool offset points past chunk end.")
        if utf8:
            # UTF-8 entries store character count first, then encoded byte length.
            first = data[pos]
            pos += 2 if (first & 0x80) else 1
            if pos >= chunk_end:
                raise ValueError("Truncated UTF-8 string length in AXML string pool.")
            second = data[pos]
            if second & 0x80:
                if pos + 1 >= chunk_end:
                    raise ValueError("Truncated 2-byte UTF-8 length in AXML string pool.")
                byte_len = ((second & 0x7F) << 8) | data[pos + 1]
                pos += 2
            else:
                byte_len = second
                pos += 1
            if pos + byte_len > chunk_end:
                raise ValueError("Truncated UTF-8 string bytes in AXML string pool.")
            strings.append(data[pos : pos + byte_len].decode("utf-8", errors="replace"))
        else:
            if pos + 2 > chunk_end:
                raise ValueError("Truncated UTF-16 string length in AXML string pool.")
            first = struct.unpack_from("<H", data, pos)[0]
            if first & 0x8000:
                if pos + 4 > chunk_end:
                    raise ValueError("Truncated 4-byte UTF-16 length in AXML string pool.")
                second = struct.unpack_from("<H", data, pos + 2)[0]
                char_len = ((first & 0x7FFF) << 16) | second
                pos += 4
            else:
                char_len = first
                pos += 2
            byte_len = char_len * 2
            if pos + byte_len > chunk_end:
                raise ValueError("Truncated UTF-16 string bytes in AXML string pool.")
            strings.append(data[pos : pos + byte_len].decode("utf-16-le", errors="replace"))
    return strings


def parse_manifest_axml(data):
    """Parse Android binary XML (AndroidManifest.xml) and return (package, versionCode, versionName)."""
    if len(data) < 8:
        raise ValueError("AndroidManifest.xml is too short to be valid AXML.")
    chunk_type, header_size, total_size = struct.unpack_from("<HHI", data, 0)
    if chunk_type != RES_XML_TYPE or header_size < 8 or total_size > len(data):
        raise ValueError("AndroidManifest.xml does not have a valid AXML header.")

    strings = []
    resource_ids = []
    pos = header_size
    while pos + 8 <= total_size:
        ctype, cheader, csize = struct.unpack_from("<HHI", data, pos)
        if csize < 8 or cheader < 8 or cheader > csize or pos + csize > total_size:
            raise ValueError("Malformed chunk in AndroidManifest.xml.")
        if ctype == RES_STRING_POOL_TYPE:
            strings = _parse_string_pool(data, pos, csize)
        elif ctype == RES_XML_RESOURCE_MAP_TYPE:
            count = (csize - cheader) // 4
            resource_ids = list(struct.unpack_from(f"<{count}I", data, pos + cheader))
        elif ctype == RES_XML_START_ELEMENT_TYPE:
            ext = pos + cheader
            if ext + 20 > pos + csize:
                raise ValueError("Truncated start element in AndroidManifest.xml.")
            ns_idx, name_idx, attr_start, attr_size, attr_count = struct.unpack_from("<iiHHH", data, ext)
            tag_name = strings[name_idx] if 0 <= name_idx < len(strings) else ""
            if ns_idx == -1 and tag_name == "manifest":
                package_name = None
                version_code = None
                version_code_major = 0
                version_name = None
                attrs_base = ext + attr_start
                if attr_size < 20 or attrs_base + attr_count * attr_size > pos + csize:
                    raise ValueError("Invalid manifest attribute table in AndroidManifest.xml.")
                for idx in range(attr_count):
                    attr_pos = attrs_base + idx * attr_size
                    ans, aname, araw, _vsize, _vres0, vtype, vdata = struct.unpack_from("<iiiHBBI", data, attr_pos)
                    attr_name = strings[aname] if 0 <= aname < len(strings) else ""
                    res_id = resource_ids[aname] if 0 <= aname < len(resource_ids) else 0
                    if ans == -1 and attr_name == "package":
                        str_idx = araw if araw >= 0 else (vdata if vtype == TYPE_STRING else -1)
                        if 0 <= str_idx < len(strings):
                            package_name = strings[str_idx]
                    elif res_id == ATTR_VERSION_CODE or attr_name == "versionCode":
                        if vtype in (TYPE_INT_DEC, TYPE_INT_HEX):
                            version_code = vdata
                    elif res_id == ATTR_VERSION_CODE_MAJOR or attr_name == "versionCodeMajor":
                        if vtype in (TYPE_INT_DEC, TYPE_INT_HEX):
                            version_code_major = vdata
                    elif res_id == ATTR_VERSION_NAME or attr_name == "versionName":
                        str_idx = araw if araw >= 0 else (vdata if vtype == TYPE_STRING else -1)
                        if 0 <= str_idx < len(strings):
                            version_name = strings[str_idx]
                if package_name is None or version_code is None or version_name is None:
                    raise ValueError("Manifest is missing package, versionCode, or versionName.")
                full_version_code = (version_code_major << 32) | version_code
                return package_name, full_version_code, version_name
        pos += csize
    raise ValueError("No <manifest> root element found in AndroidManifest.xml.")


def _read_len_prefixed_items(buf, offset, end):
    """Iterate uint32 length-prefixed byte slices inside buf[offset:end]."""
    pos = offset
    while pos < end:
        if pos + 4 > end:
            raise ValueError("Truncated length prefix in APK Signing Block.")
        length = struct.unpack_from("<I", buf, pos)[0]
        pos += 4
        if pos + length > end:
            raise ValueError("Length prefix exceeds APK Signing Block bounds.")
        yield buf[pos : pos + length]
        pos += length


def extract_apk_signing_certs(apk_path):
    """Extract lowercase SHA-256 hex digests of X.509 certificates from APK Signature Scheme v2/v3."""
    file_size = apk_path.stat().st_size
    if file_size < 22:
        raise ValueError("APK file is too small to contain a ZIP End of Central Directory.")
    tail_len = min(file_size, 22 + 65535)
    with apk_path.open("rb") as stream:
        stream.seek(file_size - tail_len)
        tail = stream.read(tail_len)
        eocd_pos = -1
        search_end = len(tail)
        while True:
            candidate = tail.rfind(b"PK\x05\x06", 0, search_end)
            if candidate < 0:
                break
            if candidate + 22 <= len(tail):
                comment_len = struct.unpack_from("<H", tail, candidate + 20)[0]
                if candidate + 22 + comment_len == len(tail):
                    eocd_pos = candidate
                    break
            search_end = candidate
        if eocd_pos < 0:
            raise ValueError("Could not locate ZIP End of Central Directory in APK.")
        cd_offset = struct.unpack_from("<I", tail, eocd_pos + 16)[0]
        if cd_offset < 24 or cd_offset > file_size:
            raise ValueError("APK does not contain an APK Signing Block before the Central Directory.")
        stream.seek(cd_offset - 24)
        footer = stream.read(24)
        size_footer = struct.unpack_from("<Q", footer, 0)[0]
        magic = footer[8:24]
        if magic != APK_SIG_BLOCK_MAGIC:
            raise ValueError("APK Signing Block magic not found (expected APK Signature Scheme v2/v3).")
        total_block_size = size_footer + 8
        if size_footer < 24 or total_block_size > cd_offset:
            raise ValueError("APK Signing Block size is invalid.")
        stream.seek(cd_offset - total_block_size)
        block = stream.read(total_block_size)

    size_header = struct.unpack_from("<Q", block, 0)[0]
    if size_header != size_footer:
        raise ValueError("APK Signing Block header and footer sizes do not match.")

    digests = set()
    pos = 8
    pairs_end = len(block) - 24
    while pos < pairs_end:
        if pos + 12 > pairs_end:
            raise ValueError("Truncated ID-value pair in APK Signing Block.")
        pair_len = struct.unpack_from("<Q", block, pos)[0]
        if pair_len < 4 or pos + 8 + pair_len > pairs_end:
            raise ValueError("Invalid ID-value pair length in APK Signing Block.")
        pair_id = struct.unpack_from("<I", block, pos + 8)[0]
        value = block[pos + 12 : pos + 8 + pair_len]
        if pair_id in (APK_SIG_SCHEME_V2_ID, APK_SIG_SCHEME_V3_ID):
            if len(value) < 4:
                raise ValueError("Truncated signer sequence in APK Signing Block.")
            signers_len = struct.unpack_from("<I", value, 0)[0]
            if 4 + signers_len > len(value):
                raise ValueError("Invalid signer sequence length in APK Signing Block.")
            for signer in _read_len_prefixed_items(value, 4, 4 + signers_len):
                if len(signer) < 4:
                    raise ValueError("Truncated signer entry in APK Signing Block.")
                signed_data_len = struct.unpack_from("<I", signer, 0)[0]
                if 4 + signed_data_len > len(signer):
                    raise ValueError("Invalid signed data length in APK Signing Block.")
                signed_data = signer[4 : 4 + signed_data_len]
                if len(signed_data) < 8:
                    raise ValueError("Truncated signed data in APK Signing Block.")
                digests_len = struct.unpack_from("<I", signed_data, 0)[0]
                certs_offset = 4 + digests_len
                if certs_offset + 4 > len(signed_data):
                    raise ValueError("Truncated certificate sequence in APK Signing Block.")
                certs_len = struct.unpack_from("<I", signed_data, certs_offset)[0]
                certs_end = certs_offset + 4 + certs_len
                if certs_end > len(signed_data):
                    raise ValueError("Invalid certificate sequence length in APK Signing Block.")
                for cert_der in _read_len_prefixed_items(signed_data, certs_offset + 4, certs_end):
                    digests.add(hashlib.sha256(cert_der).hexdigest())
        pos += 8 + pair_len
    if not digests:
        raise ValueError("No APK Signature Scheme v2/v3 certificates found.")
    return digests


def inspect_apk(apk_path, expected_cert_sha256=EXPECTED_CERT_SHA256):
    """Verify package identity and signing certificate, and return (versionCode, versionName)."""
    certs = extract_apk_signing_certs(apk_path)
    if expected_cert_sha256 and expected_cert_sha256 not in certs:
        raise ValueError(
            f"APK signing certificate mismatch; expected {expected_cert_sha256}, found {sorted(certs)}."
        )
    with zipfile.ZipFile(apk_path) as archive:
        try:
            manifest_bytes = archive.read("AndroidManifest.xml")
        except KeyError as error:
            raise ValueError("APK does not contain AndroidManifest.xml.") from error
    package_name, version_code, version_name = parse_manifest_axml(manifest_bytes)
    if package_name != EXPECTED_PACKAGE:
        raise ValueError(f"Expected package {EXPECTED_PACKAGE}, found {package_name!r}.")
    return version_code, version_name


def validate_version(version_code, version_name, existing=None, force=False):
    if type(version_code) is not int:
        raise ValueError(f"versionCode must be an integer, got {type(version_code).__name__}.")
    if version_code < MIN_VERSION_CODE:
        raise ValueError(
            f"versionCode {version_code} is below the minimum supported threshold {MIN_VERSION_CODE}."
        )
    if not isinstance(version_name, str) or not VERSION_NAME.fullmatch(version_name):
        raise ValueError(f"Invalid versionName: {version_name!r}.")
    if existing is not None and not force:
        existing_code = existing.get("versionCode")
        if type(existing_code) is int and version_code < existing_code:
            raise ValueError(
                f"Refusing to downgrade versionCode from {existing_code} to {version_code} without --force."
            )


def probe_rustore_api(version_code):
    """Verify that backapi.rustore.ru accepts ruStoreVerCode and serves .zip split downloads."""
    headers = {
        "Accept": "application/json",
        "User-Agent": USER_AGENT,
        "ruStoreVerCode": str(version_code),
    }
    status, _url, body = http_request(
        OVERALL_INFO_URL.format(PROBE_PACKAGE),
        headers=headers,
        timeout=30,
    )
    if status != 200:
        raise ValueError(f"RuStore overallInfo probe for {PROBE_PACKAGE} returned HTTP {status}.")
    try:
        overview = json.loads(body.decode("utf-8"))
    except ValueError as error:
        raise ValueError("RuStore overallInfo probe returned invalid JSON.") from error
    app = overview.get("body") if isinstance(overview, dict) else None
    app_id = app.get("appId") if isinstance(app, dict) else None
    if not isinstance(app, dict) or app.get("packageName") != PROBE_PACKAGE or type(app_id) is not int:
        raise ValueError("RuStore overallInfo probe returned unexpected package metadata.")

    payload = json.dumps({
        "appId": app_id,
        "firstInstall": True,
        "screenDensity": 420,
        "sdkVersion": 34,
        "withoutSplits": False,
        "supportedAbis": ["arm64-v8a", "armeabi-v7a"],
    }).encode("utf-8")
    dl_status, _dl_url, dl_body = http_request(
        DOWNLOAD_LINK_URL,
        headers={**headers, "Content-Type": "application/json; charset=utf-8"},
        data=payload,
        method="POST",
        timeout=30,
    )
    if dl_status != 200:
        raise ValueError(f"RuStore v2/download-link probe returned HTTP {dl_status}.")
    try:
        dl_data = json.loads(dl_body.decode("utf-8"))
    except ValueError as error:
        raise ValueError("RuStore v2/download-link probe returned invalid JSON.") from error
    raw_urls = (dl_data.get("body") or {}).get("downloadUrls") if isinstance(dl_data, dict) else None
    if not isinstance(raw_urls, list) or not raw_urls:
        raise ValueError("RuStore v2/download-link probe returned no downloadUrls.")
    urls = []
    for item in raw_urls:
        url = item.get("url") if isinstance(item, dict) else None
        if not isinstance(url, str) or not is_trusted_rustore_url(url):
            raise ValueError(f"RuStore v2/download-link probe returned an untrusted URL: {url!r}.")
        if not urlparse(url).path.lower().endswith(".zip"):
            raise ValueError(
                f"RuStore v2/download-link probe did not return .zip transport for versionCode {version_code}: {url}"
            )
        urls.append(url)

    probe_status, _probe_url, probe_bytes = http_request(
        urls[0],
        headers={"User-Agent": USER_AGENT, "Range": "bytes=0-3"},
        timeout=30,
        max_bytes=4,
    )
    if probe_status not in (200, 206):
        raise ValueError(f"First .zip download URL probe returned HTTP {probe_status}.")
    if probe_bytes != b"PK\x03\x04":
        raise ValueError("First .zip download URL did not return a ZIP archive header.")
    return True


def build_record(version_code, version_name, *, verified_zip_transport=True, checked_at=None, source="rustore_apk"):
    timestamp = checked_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return {
        "schemaVersion": 1,
        "versionCode": version_code,
        "versionName": version_name,
        "verifiedZipTransport": bool(verified_zip_transport),
        "checkedAt": timestamp,
        "source": source,
    }


def is_unchanged(existing, version_code, version_name):
    if not isinstance(existing, dict):
        return False
    return (
        type(existing.get("versionCode")) is int
        and existing.get("versionCode") == version_code
        and existing.get("versionName") == version_name
    )


def github(*args, input_text=None):
    result = subprocess.run(["gh", *args], input=input_text, check=True, capture_output=True, text=True)
    return result.stdout


def api(path, payload=None, method="POST"):
    if payload is None:
        return json.loads(github("api", path))
    return json.loads(github("api", path, "--method", method, "--input", "-", input_text=json.dumps(payload)))


def read_branch_state(repository):
    """Read (parent_sha, base_tree_sha, existing_record) from refs/heads/release-reports."""
    prefix = f"repos/{repository}/git"
    refs = api(prefix + f"/matching-refs/heads/{REPORT_BRANCH}")
    matching = [ref for ref in refs if ref["ref"] == f"refs/heads/{REPORT_BRANCH}"]
    if not matching:
        return None, None, None
    parent = matching[0]["object"]["sha"]
    base_tree = api(prefix + "/commits/" + parent)["tree"]["sha"]
    tree = api(prefix + "/trees/" + base_tree)
    existing = None
    for item in tree.get("tree", []):
        if item.get("path") == REPORT_PATH and item.get("type") == "blob":
            blob = api(prefix + "/blobs/" + item["sha"])
            content = base64.b64decode(blob["content"]).decode("utf-8")
            parsed = json.loads(content)
            if isinstance(parsed, dict):
                existing = parsed
            break
    return parent, base_tree, existing


def read_existing_raw(repository):
    """Best-effort read of the published file via raw.githubusercontent.com for --dry-run."""
    url = f"https://raw.githubusercontent.com/{repository}/{REPORT_BRANCH}/{REPORT_PATH}"
    try:
        status, _final_url, body = http_request(url, headers={"User-Agent": USER_AGENT}, timeout=15)
    except OSError:
        return None
    if status != 200:
        return None
    try:
        parsed = json.loads(body.decode("utf-8"))
    except ValueError:
        return None
    return parsed if isinstance(parsed, dict) else None


def publish_record(repository, record, *, force=False):
    """Commit rustore-version.json to release-reports without dropping existing files."""
    parent, base_tree, existing = read_branch_state(repository)
    validate_version(record["versionCode"], record["versionName"], existing=existing, force=force)
    if not force and is_unchanged(existing, record["versionCode"], record["versionName"]):
        print(
            f"RuStore version {record['versionName']} ({record['versionCode']}) is already published; skipping commit.",
            file=sys.stderr,
        )
        return False

    prefix = f"repos/{repository}/git"
    encoded = base64.b64encode((json.dumps(record, indent=2) + "\n").encode("utf-8")).decode("ascii")
    blob = api(prefix + "/blobs", {"content": encoded, "encoding": "base64"})
    tree_data = {
        "tree": [
            {"path": REPORT_PATH, "mode": "100644", "type": "blob", "sha": blob["sha"]},
        ]
    }
    if base_tree:
        tree_data["base_tree"] = base_tree
    tree = api(prefix + "/trees", tree_data)
    commit = api(
        prefix + "/commits",
        {
            "message": f"Update RuStore version metadata to {record['versionName']} ({record['versionCode']}).",
            "tree": tree["sha"],
            "parents": [parent] if parent else [],
            "author": COMMIT_IDENTITY,
            "committer": COMMIT_IDENTITY,
        },
    )
    if parent:
        api(prefix + f"/refs/heads/{REPORT_BRANCH}", {"sha": commit["sha"], "force": False}, method="PATCH")
    else:
        api(prefix + "/refs", {"ref": f"refs/heads/{REPORT_BRANCH}", "sha": commit["sha"]})
    print(
        f"Published RuStore version {record['versionName']} ({record['versionCode']}) to {REPORT_BRANCH}.",
        file=sys.stderr,
    )
    return True


def resolve_and_verify(*, existing=None, force=False, allow_fast_path=False):
    apk_url, advertised_version = discover_apk_url()
    # Skip the ~60 MB APK download on routine daily runs when the page still advertises
    # the exact version already on release-reports, while still probing backapi.rustore.ru.
    if (
        allow_fast_path
        and not force
        and advertised_version is not None
        and isinstance(existing, dict)
        and existing.get("versionName") == advertised_version
        and type(existing.get("versionCode")) is int
    ):
        version_code = existing["versionCode"]
        version_name = existing["versionName"]
        validate_version(version_code, version_name, existing=existing, force=False)
        probe_rustore_api(version_code)
        return build_record(version_code, version_name, verified_zip_transport=True), True

    with tempfile.TemporaryDirectory() as directory:
        apk_path = Path(directory) / "rustore.apk"
        download_apk(apk_url, apk_path)
        version_code, version_name = inspect_apk(apk_path)

    validate_version(version_code, version_name, existing=existing, force=force)
    probe_rustore_api(version_code)
    return build_record(version_code, version_name, verified_zip_transport=True), False


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--dry-run", action="store_true", help="Discover, verify, and print JSON without publishing.")
    mode.add_argument("--publish", action="store_true", help="Publish verified rustore-version.json to release-reports.")
    parser.add_argument("--force", action="store_true", help="Bypass unchanged-version skip and downgrade guard.")
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", "wyrtensi/wystore"))
    args = parser.parse_args(argv)
    if not REPOSITORY.fullmatch(args.repository):
        parser.error("Invalid GitHub repository name.")

    try:
        if args.dry_run:
            existing = read_existing_raw(args.repository)
            record, _fast = resolve_and_verify(existing=existing, force=args.force, allow_fast_path=False)
            print(json.dumps(record, indent=2))
            return 0

        _parent, _base_tree, existing = read_branch_state(args.repository)
        record, fast_path = resolve_and_verify(existing=existing, force=args.force, allow_fast_path=True)
        if fast_path and not args.force:
            print(
                f"RuStore version {record['versionName']} ({record['versionCode']}) is unchanged and passed live probe.",
                file=sys.stderr,
            )
            return 0
        publish_record(args.repository, record, force=args.force)
        return 0
    except subprocess.CalledProcessError as error:
        print(f"GitHub API operation failed with exit code {error.returncode}.", file=sys.stderr)
        if error.stderr:
            print(error.stderr.strip(), file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
