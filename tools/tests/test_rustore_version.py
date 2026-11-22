import base64
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "rustore_version.py"
spec = importlib.util.spec_from_file_location("rustore_version", SCRIPT)
rv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rv)


def _pad4(data):
    rem = len(data) % 4
    return data if rem == 0 else data + b"\x00" * (4 - rem)


def _build_string_pool(strings, *, utf8=True):
    encoded = bytearray()
    offsets = []
    for text in strings:
        offsets.append(len(encoded))
        if utf8:
            raw = text.encode("utf-8")
            encoded.append(len(text))
            encoded.append(len(raw))
            encoded.extend(raw)
            encoded.append(0)
        else:
            raw = text.encode("utf-16-le")
            encoded.extend(struct.pack("<H", len(text)))
            encoded.extend(raw)
            encoded.extend(b"\x00\x00")
    padded_strings = _pad4(bytes(encoded))
    header_size = 28
    offsets_blob = b"".join(struct.pack("<I", off) for off in offsets)
    strings_start = header_size + len(offsets_blob)
    total_size = strings_start + len(padded_strings)
    flags = rv.STRING_POOL_UTF8_FLAG if utf8 else 0
    header = struct.pack(
        "<HHIIIIII",
        rv.RES_STRING_POOL_TYPE,
        header_size,
        total_size,
        len(strings),
        0,
        flags,
        strings_start,
        0,
    )
    return header + offsets_blob + padded_strings


def _build_manifest_axml(
    package_name="ru.vk.store",
    version_code=1114003,
    version_name="1.114.0.3",
    *,
    version_code_major=None,
    utf8=True,
    obfuscate_attr_names=False,
):
    android_ns = "http://schemas.android.com/apk/res/android"
    strings = [
        "" if obfuscate_attr_names else "versionCode",
        "" if obfuscate_attr_names else "versionName",
        "" if obfuscate_attr_names else "versionCodeMajor",
        "package",
        "manifest",
        android_ns,
        package_name,
        version_name,
    ]
    res_ids = [
        rv.ATTR_VERSION_CODE,
        rv.ATTR_VERSION_NAME,
        rv.ATTR_VERSION_CODE_MAJOR,
    ]

    pool_chunk = _build_string_pool(strings, utf8=utf8)
    res_map_body = struct.pack(f"<{len(res_ids)}I", *res_ids)
    res_map_chunk = struct.pack("<HHI", rv.RES_XML_RESOURCE_MAP_TYPE, 8, 8 + len(res_map_body)) + res_map_body

    attrs = [
        (-1, 3, 6, 8, 0, rv.TYPE_STRING, 6),  # package
        (5, 0, -1, 8, 0, rv.TYPE_INT_DEC, version_code),  # android:versionCode
        (5, 1, 7, 8, 0, rv.TYPE_STRING, 7),  # android:versionName
    ]
    if version_code_major is not None:
        attrs.append((5, 2, -1, 8, 0, rv.TYPE_INT_DEC, version_code_major))

    attr_bytes = b"".join(struct.pack("<iiiHBBI", *attr) for attr in attrs)
    ext = struct.pack("<iiHHHHHH", -1, 4, 20, 20, len(attrs), 0, 0, 0)
    node_size = 16 + len(ext) + len(attr_bytes)
    start_elem = struct.pack("<HHIII", rv.RES_XML_START_ELEMENT_TYPE, 16, node_size, 1, 0xFFFFFFFF) + ext + attr_bytes

    body = pool_chunk + res_map_chunk + start_elem
    return struct.pack("<HHI", rv.RES_XML_TYPE, 8, 8 + len(body)) + body


def _len_prefix(data):
    return struct.pack("<I", len(data)) + data


def _build_apk_sig_block(cert_der, *, scheme_id=rv.APK_SIG_SCHEME_V2_ID):
    signed_data = _len_prefix(b"") + _len_prefix(_len_prefix(cert_der)) + _len_prefix(b"")
    signer = _len_prefix(signed_data) + _len_prefix(b"") + _len_prefix(b"")
    scheme_value = _len_prefix(_len_prefix(signer))
    pair = struct.pack("<QI", 4 + len(scheme_value), scheme_id) + scheme_value
    size_without_header = len(pair) + 8 + 16
    return (
        struct.pack("<Q", size_without_header)
        + pair
        + struct.pack("<Q", size_without_header)
        + rv.APK_SIG_BLOCK_MAGIC
    )


def _write_synthetic_apk(path, manifest_bytes, *, cert_der=b"official-rustore-cert-der", include_sig_block=True):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("AndroidManifest.xml", manifest_bytes)
    raw = buf.getvalue()
    if not include_sig_block:
        path.write_bytes(raw)
        return
    eocd_pos = raw.rfind(b"PK\x05\x06")
    cd_offset = struct.unpack_from("<I", raw, eocd_pos + 16)[0]
    sig_block = _build_apk_sig_block(cert_der)
    new_cd_offset = cd_offset + len(sig_block)
    updated_eocd = raw[eocd_pos : eocd_pos + 16] + struct.pack("<I", new_cd_offset) + raw[eocd_pos + 20 :]
    path.write_bytes(raw[:cd_offset] + sig_block + raw[cd_offset:eocd_pos] + updated_eocd)


class DiscoveryTests(unittest.TestCase):
    def test_extracts_mobile_apk_and_skips_tv_apk(self):
        html = (
            '{"tv":"https://static.rustore.ru/release/RuStoreTV.apk",'
            '"versionedApk":{"link":"https://static.rustore.ru/rustore-releases/stable/RuStore-mobile-1.114.0.3.apk"}}'
        )
        url, advertised = rv.extract_apk_url(html)
        self.assertEqual(url, "https://static.rustore.ru/rustore-releases/stable/RuStore-mobile-1.114.0.3.apk")
        self.assertEqual(advertised, "1.114.0.3")

    def test_extracts_from_escaped_rsc_payload(self):
        html = r'{\"versionedApk\":{\"link\":\"https://static.rustore.ru/rustore-releases/stable/RuStore-mobile-1.115.2.0.apk\"}}'
        url, advertised = rv.extract_apk_url(html)
        self.assertEqual(url, "https://static.rustore.ru/rustore-releases/stable/RuStore-mobile-1.115.2.0.apk")
        self.assertEqual(advertised, "1.115.2.0")

    def test_rejects_untrusted_or_http_urls(self):
        for bad in (
            "http://static.rustore.ru/rustore-releases/stable/RuStore-mobile-1.114.0.3.apk",
            "https://evil-rustore.ru/RuStore-mobile-1.114.0.3.apk",
            "https://rustore.ru.evil.com/RuStore-mobile-1.114.0.3.apk",
        ):
            with self.subTest(url=bad):
                self.assertFalse(rv.is_trusted_rustore_url(bad))
                with self.assertRaises(ValueError):
                    rv.extract_apk_url(f'<a href="{bad}">Download</a>')


class AxmlAndApkInspectionTests(unittest.TestCase):
    def test_parses_utf8_and_utf16_axml_manifests(self):
        for utf8 in (True, False):
            with self.subTest(utf8=utf8):
                axml = _build_manifest_axml("ru.vk.store", 1114003, "1.114.0.3", utf8=utf8)
                self.assertEqual(rv.parse_manifest_axml(axml), ("ru.vk.store", 1114003, "1.114.0.3"))

    def test_parses_version_attributes_via_resource_ids_when_names_are_obfuscated(self):
        axml = _build_manifest_axml(
            "ru.vk.store",
            1114003,
            "1.114.0.3",
            obfuscate_attr_names=True,
        )
        self.assertEqual(rv.parse_manifest_axml(axml), ("ru.vk.store", 1114003, "1.114.0.3"))

    def test_combines_version_code_major(self):
        axml = _build_manifest_axml("ru.vk.store", 1114003, "1.114.0.3", version_code_major=2)
        _, code, _ = rv.parse_manifest_axml(axml)
        self.assertEqual(code, (2 << 32) | 1114003)

    def test_inspect_apk_verifies_cert_and_package(self):
        cert = b"synthetic-rustore-x509-cert"
        cert_sha = hashlib.sha256(cert).hexdigest()
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "rustore.apk"
            _write_synthetic_apk(apk, _build_manifest_axml("ru.vk.store", 1114003, "1.114.0.3"), cert_der=cert)
            self.assertEqual(rv.inspect_apk(apk, expected_cert_sha256=cert_sha), (1114003, "1.114.0.3"))

            with self.assertRaises(ValueError):
                rv.inspect_apk(apk, expected_cert_sha256="0" * 64)

    def test_inspect_apk_rejects_wrong_package_or_missing_signature(self):
        cert = b"synthetic-rustore-x509-cert"
        cert_sha = hashlib.sha256(cert).hexdigest()
        with tempfile.TemporaryDirectory() as directory:
            wrong_pkg = Path(directory) / "wrong-pkg.apk"
            _write_synthetic_apk(wrong_pkg, _build_manifest_axml("com.other.app", 1114003, "1.114.0.3"), cert_der=cert)
            with self.assertRaises(ValueError):
                rv.inspect_apk(wrong_pkg, expected_cert_sha256=cert_sha)

            unsigned = Path(directory) / "unsigned.apk"
            _write_synthetic_apk(unsigned, _build_manifest_axml("ru.vk.store", 1114003, "1.114.0.3"), include_sig_block=False)
            with self.assertRaises(ValueError):
                rv.inspect_apk(unsigned, expected_cert_sha256=cert_sha)


class ValidationAndProbeTests(unittest.TestCase):
    def test_sanity_and_downgrade_validation(self):
        rv.validate_version(1114003, "1.114.0.3", existing={"versionCode": 1112004, "versionName": "1.112.0.4"})

        # Below minimum threshold (including the legacy 6-digit dot-stripped form).
        for bad_code in (111204, 1059000, True, "1114003"):
            with self.subTest(bad_code=bad_code), self.assertRaises(ValueError):
                rv.validate_version(bad_code, "1.114.0.3")

        for bad_name in ("", "1", "1.114.0-beta", None):
            with self.subTest(bad_name=bad_name), self.assertRaises(ValueError):
                rv.validate_version(1114003, bad_name)

        # Downgrade rejected by default, allowed with --force.
        with self.assertRaises(ValueError):
            rv.validate_version(1112004, "1.112.0.4", existing={"versionCode": 1114003, "versionName": "1.114.0.3"})
        rv.validate_version(
            1112004,
            "1.112.0.4",
            existing={"versionCode": 1114003, "versionName": "1.114.0.3"},
            force=True,
        )

    def test_probe_accepts_zip_transport_and_valid_zip_header(self):
        overview = json.dumps({"body": {"appId": 317631, "packageName": "com.vkontakte.android"}}).encode("utf-8")
        downloads = json.dumps({
            "body": {
                "downloadUrls": [
                    {"url": "https://static-m.rustore.ru/2026/11/11/89/base.zip", "size": 100},
                    {"url": "https://static-m.rustore.ru/2026/11/11/89/arm64.zip", "size": 50},
                ]
            }
        }).encode("utf-8")
        responses = [
            (200, rv.OVERALL_INFO_URL.format(rv.PROBE_PACKAGE), overview),
            (200, rv.DOWNLOAD_LINK_URL, downloads),
            (206, "https://static-m.rustore.ru/2026/11/11/89/base.zip", b"PK\x03\x04"),
        ]
        with patch.object(rv, "http_request", side_effect=responses) as mock_http:
            self.assertTrue(rv.probe_rustore_api(1114003))
        self.assertEqual(mock_http.call_args_list[0].kwargs["headers"]["ruStoreVerCode"], "1114003")
        sent_payload = json.loads(mock_http.call_args_list[1].kwargs["data"].decode("utf-8"))
        self.assertFalse(sent_payload["withoutSplits"])

    def test_probe_rejects_legacy_apk_transport_or_404_download(self):
        overview = json.dumps({"body": {"appId": 317631, "packageName": "com.vkontakte.android"}}).encode("utf-8")
        legacy_apk = json.dumps({
            "body": {"downloadUrls": [{"url": "https://static-m.rustore.ru/2026/11/11/89/base.apk"}]}
        }).encode("utf-8")
        with patch.object(rv, "http_request", side_effect=[
            (200, rv.OVERALL_INFO_URL.format(rv.PROBE_PACKAGE), overview),
            (200, rv.DOWNLOAD_LINK_URL, legacy_apk),
        ]):
            with self.assertRaises(ValueError):
                rv.probe_rustore_api(111204)

        zip_dl = json.dumps({
            "body": {"downloadUrls": [{"url": "https://static-m.rustore.ru/2026/11/11/89/base.zip"}]}
        }).encode("utf-8")
        with patch.object(rv, "http_request", side_effect=[
            (200, rv.OVERALL_INFO_URL.format(rv.PROBE_PACKAGE), overview),
            (200, rv.DOWNLOAD_LINK_URL, zip_dl),
            (404, "https://static-m.rustore.ru/2026/11/11/89/base.zip", b""),
        ]):
            with self.assertRaises(ValueError):
                rv.probe_rustore_api(1114003)


class PublishAndFastPathTests(unittest.TestCase):
    def _encoded_record(self, version_code, version_name):
        payload = json.dumps(rv.build_record(version_code, version_name, checked_at="2026-11-22T15:00:00Z"))
        return base64.b64encode(payload.encode("utf-8")).decode("ascii")

    def test_unchanged_version_skips_commit_unless_forced(self):
        existing_blob = {"content": self._encoded_record(1114003, "1.114.0.3")}
        record = rv.build_record(1114003, "1.114.0.3", checked_at="2026-11-22T16:00:00Z")
        branch_reads = [
            [{"ref": "refs/heads/release-reports", "object": {"sha": "parent-sha"}}],
            {"tree": {"sha": "base-tree-sha"}},
            {"tree": [
                {"path": "virustotal-latest.json", "type": "blob", "sha": "vt-sha"},
                {"path": "rustore-version.json", "type": "blob", "sha": "rv-sha"},
            ]},
            existing_blob,
        ]
        with patch.object(rv, "api", side_effect=branch_reads) as mock_api:
            published = rv.publish_record("wyrtensi/wystore", record, force=False)
        self.assertFalse(published)
        self.assertEqual(mock_api.call_count, 4)

        forced_responses = [
            *branch_reads,
            {"sha": "new-blob-sha"},
            {"sha": "new-tree-sha"},
            {"sha": "new-commit-sha"},
            {"ref": "refs/heads/release-reports"},
        ]
        with patch.object(rv, "api", side_effect=forced_responses) as mock_api:
            published = rv.publish_record("wyrtensi/wystore", record, force=True)
        self.assertTrue(published)
        self.assertEqual(mock_api.call_count, 8)
        self.assertEqual(mock_api.call_args_list[-1].kwargs["method"], "PATCH")
        self.assertFalse(mock_api.call_args_list[-1].args[1]["force"])

    def test_force_bypasses_fast_path_and_reinspects_apk(self):
        existing = {"versionCode": 1114003, "versionName": "1.114.0.3"}
        with patch.object(
            rv,
            "discover_apk_url",
            return_value=("https://static.rustore.ru/rustore-releases/stable/RuStore-mobile-1.114.0.3.apk", "1.114.0.3"),
        ), patch.object(rv, "download_apk") as download, patch.object(
            rv, "inspect_apk", return_value=(1114003, "1.114.0.3")
        ) as inspect, patch.object(rv, "probe_rustore_api", return_value=True) as probe:
            record, used_fast_path = rv.resolve_and_verify(existing=existing, force=True, allow_fast_path=True)
        self.assertFalse(used_fast_path)
        download.assert_called_once()
        inspect.assert_called_once()
        probe.assert_called_once_with(1114003)
        self.assertEqual(record["versionCode"], 1114003)

    def test_publish_preserves_existing_files_with_base_tree_and_no_force(self):
        existing_blob = {"content": self._encoded_record(1112004, "1.112.0.4")}
        record = rv.build_record(1114003, "1.114.0.3", checked_at="2026-11-22T15:00:00Z")
        api_responses = [
            [{"ref": "refs/heads/release-reports", "object": {"sha": "parent-sha"}}],
            {"tree": {"sha": "base-tree-sha"}},
            {"tree": [
                {"path": "virustotal-latest.json", "type": "blob", "sha": "vt-sha"},
                {"path": "virustotal-latest-report.json", "type": "blob", "sha": "vt-report-sha"},
                {"path": "rustore-version.json", "type": "blob", "sha": "old-rv-sha"},
            ]},
            existing_blob,
            {"sha": "new-blob-sha"},
            {"sha": "new-tree-sha"},
            {"sha": "new-commit-sha"},
            {"ref": "refs/heads/release-reports"},
        ]
        with patch.object(rv, "api", side_effect=api_responses) as mock_api:
            published = rv.publish_record("wyrtensi/wystore", record, force=False)
        self.assertTrue(published)

        blob_payload = mock_api.call_args_list[4].args[1]
        stored = json.loads(base64.b64decode(blob_payload["content"]).decode("utf-8"))
        self.assertEqual(stored, record)

        tree_payload = mock_api.call_args_list[5].args[1]
        self.assertEqual(tree_payload["base_tree"], "base-tree-sha")
        self.assertEqual([item["path"] for item in tree_payload["tree"]], ["rustore-version.json"])

        commit_payload = mock_api.call_args_list[6].args[1]
        self.assertEqual(commit_payload["parents"], ["parent-sha"])
        self.assertEqual(commit_payload["author"], {"name": "wyrtensi", "email": "wyrtensi@gmail.com"})
        self.assertEqual(commit_payload["committer"], commit_payload["author"])

        patch_call = mock_api.call_args_list[7]
        self.assertEqual(patch_call.args[0], "repos/wyrtensi/wystore/git/refs/heads/release-reports")
        self.assertEqual(patch_call.kwargs["method"], "PATCH")
        self.assertFalse(patch_call.args[1]["force"])

    def test_first_publish_creates_release_reports_branch(self):
        record = rv.build_record(1114003, "1.114.0.3", checked_at="2026-11-22T15:00:00Z")
        api_responses = [
            [],
            {"sha": "blob-sha"},
            {"sha": "tree-sha"},
            {"sha": "commit-sha"},
            {"ref": "refs/heads/release-reports"},
        ]
        with patch.object(rv, "api", side_effect=api_responses) as mock_api:
            published = rv.publish_record("wyrtensi/wystore", record, force=False)
        self.assertTrue(published)
        tree_payload = mock_api.call_args_list[2].args[1]
        self.assertNotIn("base_tree", tree_payload)
        commit_payload = mock_api.call_args_list[3].args[1]
        self.assertEqual(commit_payload["parents"], [])
        self.assertEqual(mock_api.call_args_list[4].args[0], "repos/wyrtensi/wystore/git/refs")

    def test_downgrade_refused_before_creating_blob(self):
        existing_blob = {"content": self._encoded_record(1114003, "1.114.0.3")}
        older = rv.build_record(1112004, "1.112.0.4", checked_at="2026-11-22T15:00:00Z")
        branch_reads = [
            [{"ref": "refs/heads/release-reports", "object": {"sha": "parent-sha"}}],
            {"tree": {"sha": "base-tree-sha"}},
            {"tree": [{"path": "rustore-version.json", "type": "blob", "sha": "rv-sha"}]},
            existing_blob,
        ]
        with patch.object(rv, "api", side_effect=branch_reads) as mock_api:
            with self.assertRaises(ValueError):
                rv.publish_record("wyrtensi/wystore", older, force=False)
        self.assertEqual(mock_api.call_count, 4)

    def test_fast_path_skips_apk_download_but_runs_live_probe(self):
        existing = {"versionCode": 1114003, "versionName": "1.114.0.3"}
        with patch.object(
            rv,
            "discover_apk_url",
            return_value=("https://static.rustore.ru/rustore-releases/stable/RuStore-mobile-1.114.0.3.apk", "1.114.0.3"),
        ), patch.object(rv, "download_apk") as download, patch.object(rv, "probe_rustore_api", return_value=True) as probe:
            record, used_fast_path = rv.resolve_and_verify(existing=existing, force=False, allow_fast_path=True)
        self.assertTrue(used_fast_path)
        download.assert_not_called()
        probe.assert_called_once_with(1114003)
        self.assertEqual(
            set(record.keys()),
            {"schemaVersion", "versionCode", "versionName", "verifiedZipTransport", "checkedAt", "source"},
        )
        self.assertEqual(record["schemaVersion"], 1)
        self.assertEqual(record["versionCode"], 1114003)
        self.assertEqual(record["versionName"], "1.114.0.3")
        self.assertTrue(record["verifiedZipTransport"])
        self.assertEqual(record["source"], "rustore_apk")


if __name__ == "__main__":
    unittest.main()
