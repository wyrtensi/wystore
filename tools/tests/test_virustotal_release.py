import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "virustotal_release.py"
spec = importlib.util.spec_from_file_location("virustotal_release", SCRIPT)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseReportTests(unittest.TestCase):
    def test_replacing_report_preserves_changelog_and_later_manual_notes(self):
        old = release.START + "\nold report\n" + release.END
        new = release.START + "\nnew report\n" + release.END
        body = "Release notes\n\n" + old + "\n\nManual addition\n"
        self.assertEqual(release.replace_report(body, new),
                         "Release notes\n\n" + new + "\n\nManual addition\n")

    def test_report_is_appended_once_when_scanning_an_older_release(self):
        notes = release.START + "\nreport\n" + release.END
        updated = release.replace_report("Original notes\n", notes)
        self.assertEqual(release.replace_report(updated, notes), updated)
        self.assertTrue(updated.startswith("Original notes\n"))

    def test_ambiguous_markers_are_rejected_instead_of_overwriting_notes(self):
        valid = release.START + "\nreport\n" + release.END
        for body in (release.START, release.END, valid + valid, release.END + release.START):
            with self.subTest(body=body), self.assertRaises(ValueError):
                release.replace_report(body, valid)

    def test_latest_resolves_once_and_downloads_both_exact_apk_names(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            calls = []

            def github(*args):
                calls.append(args)
                if args[:2] == ("release", "view"):
                    return json.dumps({"tagName": "v0.6.2", "isDraft": False, "body": "Notes"})
                (output / "wystore-0.6.2.apk").write_bytes(b"same APK")
                (output / "wystore.apk").write_bytes(b"same APK")
                return ""

            with patch.object(release, "github", side_effect=github), patch.dict(os.environ, {}, clear=True):
                release.prepare("wyrtensi/wystore", "latest", output)
            self.assertNotIn("latest", calls[0])
            self.assertIn("v0.6.2", calls[1])
            self.assertIn("wystore-0.6.2.apk", calls[1])
            self.assertIn("wystore.apk", calls[1])
            self.assertEqual(json.loads((output / "release-context.json").read_text())["tag"], "v0.6.2")

    def test_invalid_tags_fail_before_github_requests(self):
        for tag in ("main", "../main", "v0.6.2\ninjected=value", "--latest"):
            with self.subTest(tag=tag), patch.object(release, "github") as github, self.assertRaises(ValueError):
                release.prepare("wyrtensi/wystore", tag, Path("unused"))
            github.assert_not_called()

    def test_publish_only_replaces_report_assets_and_keeps_release_notes(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            (output / "release-context.json").write_text(json.dumps({"repository": "wyrtensi/wystore", "tag": "v0.6.2"}))
            (output / "virustotal-report.json").write_text(json.dumps({"version": "v0.6.2", "status": "completed"}))
            (output / "virustotal-badge.json").write_text("{}")
            (output / "virustotal-notes.md").write_text(release.START + "\nreport\n" + release.END)
            with patch.object(release, "github") as github, patch.object(release, "publish_latest") as latest:
                github.side_effect = [json.dumps({"tagName": "v0.6.2", "isDraft": False, "body": "Fresh manual notes"}), "", ""]
                release.publish("wyrtensi/wystore", output)
                upload = github.call_args_list[1].args
                self.assertIn(str(output / "virustotal-report.json"), upload)
                self.assertFalse(any(str(arg).endswith(".apk") for arg in upload))
                self.assertIn("--clobber", upload)
                self.assertTrue((output / "release-body.md").read_text().startswith("Fresh manual notes"))
                latest.assert_called_once_with("wyrtensi/wystore", "v0.6.2", output / "virustotal-badge.json", "completed")

    def test_rescanning_an_old_release_does_not_change_latest_badge(self):
        with patch.object(release, "release_metadata", return_value={"tagName": "v0.6.3"}), patch.object(release, "api") as api:
            release.publish_latest("wyrtensi/wystore", "v0.6.2", Path("unused"), "completed")
        api.assert_not_called()

    def test_first_report_creates_a_metadata_only_branch_with_owner_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            badge = Path(directory) / "badge.json"
            badge.write_text('{"message":"v0.6.2: pending"}')
            with patch.object(release, "release_metadata", return_value={"tagName": "v0.6.2"}), patch.object(release, "api") as api:
                api.side_effect = [[], {"sha": "blob"}, {"sha": "tree"}, {"sha": "commit"}, {"ref": "refs/heads/release-reports"}]
                release.publish_latest("wyrtensi/wystore", "v0.6.2", badge, "pending")
                tree = api.call_args_list[2].args[1]
                commit = api.call_args_list[3].args[1]
                self.assertEqual([item["path"] for item in tree["tree"]], ["virustotal-latest.json"])
                self.assertEqual(commit["parents"], [])
                self.assertEqual(commit["author"], {"name": "wyrtensi", "email": "wyrtensi@gmail.com"})
                self.assertEqual(commit["committer"], commit["author"])

    def test_newer_release_appearing_mid_publication_preserves_latest_ref(self):
        with tempfile.TemporaryDirectory() as directory:
            badge = Path(directory) / "badge.json"
            badge.write_text("{}")
            with patch.object(release, "release_metadata", side_effect=[{"tagName": "v0.6.2"}, {"tagName": "v0.6.3"}]), patch.object(release, "api") as api:
                api.side_effect = [[], {"sha": "blob"}, {"sha": "tree"}, {"sha": "commit"}]
                release.publish_latest("wyrtensi/wystore", "v0.6.2", badge, "completed")
                self.assertEqual(api.call_count, 4)

    def test_existing_reports_are_updated_without_force_or_dropping_other_files(self):
        with tempfile.TemporaryDirectory() as directory:
            badge = Path(directory) / "badge.json"
            badge.write_text("{}")
            with patch.object(release, "release_metadata", return_value={"tagName": "v0.6.2"}), patch.object(release, "api") as api:
                api.side_effect = [[{"ref": "refs/heads/release-reports", "object": {"sha": "parent"}}], {"sha": "blob"},
                                   {"tree": {"sha": "old-tree"}}, {"sha": "tree"}, {"sha": "commit"}, {}]
                release.publish_latest("wyrtensi/wystore", "v0.6.2", badge, "error")
                self.assertEqual(api.call_args_list[3].args[1]["base_tree"], "old-tree")
                self.assertEqual(api.call_args_list[-1].kwargs["method"], "PATCH")
                self.assertFalse(api.call_args_list[-1].args[1]["force"])

    def test_report_for_another_tag_is_rejected_before_publishing(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            (output / "release-context.json").write_text(json.dumps({"repository": "wyrtensi/wystore", "tag": "v0.6.2"}))
            (output / "virustotal-report.json").write_text(json.dumps({"version": "v0.6.1"}))
            (output / "virustotal-notes.md").write_text(release.START + "\nreport\n" + release.END)
            with patch.object(release, "github") as github, self.assertRaises(ValueError):
                release.publish("wyrtensi/wystore", output)
            github.assert_not_called()


if __name__ == "__main__":
    unittest.main()
