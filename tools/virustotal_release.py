"""Attach VirusTotal reports to an existing release without rebuilding its APKs."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


START = "<!-- wystore-virustotal:start -->"
END = "<!-- wystore-virustotal:end -->"
TAG = re.compile(r"v[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")
REPOSITORY = re.compile(r"[0-9A-Za-z_.-]+/[0-9A-Za-z_.-]+")


def github(*args, input_text=None):
    result = subprocess.run(["gh", *args], input=input_text, check=True, capture_output=True, text=True)
    return result.stdout


def api(path, payload=None, method="POST"):
    if payload is None:
        return json.loads(github("api", path))
    return json.loads(github("api", path, "--method", method, "--input", "-", input_text=json.dumps(payload)))


def release_metadata(repository, tag=None):
    args = ["release", "view"]
    if tag is not None:
        args.append(tag)
    return json.loads(github(*args, "--repo", repository, "--json", "tagName,isDraft,body,assets"))


def checked_tag(value):
    if not isinstance(value, str) or not TAG.fullmatch(value):
        raise ValueError("The release tag must be a version such as v0.6.2.")
    return value


def replace_report(body, notes):
    """Keep release notes and hand edits outside the one managed report block."""
    notes = notes.strip()
    if notes.count(START) != 1 or notes.count(END) != 1 or not notes.startswith(START) or not notes.endswith(END):
        raise ValueError("The generated VirusTotal notes have invalid report markers.")
    if body.count(START) != body.count(END) or body.count(START) > 1:
        raise ValueError("The existing release body has ambiguous VirusTotal report markers.")
    if START not in body:
        return body.rstrip() + "\n\n" + notes + "\n"
    start = body.index(START)
    end = body.index(END)
    if end < start:
        raise ValueError("The existing release report markers are reversed.")
    return body[:start] + notes + body[end + len(END):]


def prepare(repository, requested_tag, output_dir, artifact_dir=None):
    if requested_tag != "latest":
        checked_tag(requested_tag)
    metadata = release_metadata(repository, None if requested_tag == "latest" else requested_tag)
    tag = checked_tag(metadata["tagName"])
    if metadata["isDraft"] or (requested_tag != "latest" and tag != requested_tag):
        raise ValueError("VirusTotal scanning requires the requested published release.")
    output_dir.mkdir(parents=True, exist_ok=True)
    versioned_name = f"wystore-{tag[1:]}.apk"
    names = (versioned_name, "wystore.apk")
    if artifact_dir is None:
        github("release", "download", tag, "--repo", repository,
               "--pattern", versioned_name, "--pattern", "wystore.apk", "--dir", str(output_dir))
    else:
        for name in names:
            matches = [asset for asset in metadata.get('assets', []) if asset.get('name') == name]
            if len(matches) != 1:
                raise ValueError(f'The published release must contain exactly one {name}.')
            asset = matches[0]
            source = artifact_dir / name
            digest = hashlib.sha256()
            with source.open('rb') as stream:
                while chunk := stream.read(1024 * 1024):
                    digest.update(chunk)
            if (asset.get('state') != 'uploaded' or type(asset.get('size')) is not int
                    or asset['size'] != source.stat().st_size
                    or asset.get('digest') != 'sha256:' + digest.hexdigest()):
                raise ValueError(f'The build artifact does not match the published {name}.')
        # Validate both aliases before preparing any file for analysis.
        for name in names:
            shutil.copyfile(artifact_dir / name, output_dir / name)
    for name in (versioned_name, "wystore.apk"):
        if not (output_dir / name).is_file():
            raise ValueError(f"The published release does not contain {name}.")
    context = {"repository": repository, "tag": tag, "apk": versioned_name, "alias": "wystore.apk"}
    (output_dir / "release-context.json").write_text(json.dumps(context) + "\n", encoding="utf-8")
    if os.environ.get("GITHUB_ENV"):
        with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as stream:
            stream.write(f"VT_RELEASE_TAG={tag}\nVT_APK={output_dir / versioned_name}\nVT_ALIAS_APK={output_dir / 'wystore.apk'}\n")
    print(f"Prepared published release {tag} for VirusTotal analysis.")


def publish_latest(repository, tag, badge, status):
    # A manual rescan of an older release must never replace the latest release's badge.
    if release_metadata(repository)["tagName"] != tag:
        print(f"Preserved the latest badge because {tag} is not the latest release.")
        return
    prefix = f"repos/{repository}/git"
    refs = api(prefix + "/matching-refs/heads/release-reports")
    matching = [ref for ref in refs if ref["ref"] == "refs/heads/release-reports"]
    parent = matching[0]["object"]["sha"] if matching else None
    blob = api(prefix + "/blobs", {"content": base64.b64encode(badge.read_bytes()).decode("ascii"), "encoding": "base64"})
    tree_data = {"tree": [{"path": "virustotal-latest.json", "mode": "100644", "type": "blob", "sha": blob["sha"]}]}
    if parent:
        tree_data["base_tree"] = api(prefix + "/commits/" + parent)["tree"]["sha"]
    tree = api(prefix + "/trees", tree_data)
    identity = {"name": "wyrtensi", "email": "wyrtensi@gmail.com"}
    commit = api(prefix + "/commits", {
        "message": f"Publish the VirusTotal {status} report for {tag}.",
        "tree": tree["sha"], "parents": [parent] if parent else [],
        "author": identity, "committer": identity,
    })
    # Check again immediately before making a newly created report visible.
    if release_metadata(repository)["tagName"] != tag:
        print(f"Preserved the latest badge because a newer release appeared during analysis of {tag}.")
        return
    if parent:
        api(prefix + "/refs/heads/release-reports", {"sha": commit["sha"], "force": False}, method="PATCH")
    else:
        api(prefix + "/refs", {"ref": "refs/heads/release-reports", "sha": commit["sha"]})


def publish(repository, output_dir):
    context = json.loads((output_dir / "release-context.json").read_text(encoding="utf-8"))
    tag = checked_tag(context["tag"])
    if context["repository"] != repository:
        raise ValueError("The report repository differs from the downloaded release.")
    report = output_dir / "virustotal-report.json"
    badge = output_dir / "virustotal-badge.json"
    notes = (output_dir / "virustotal-notes.md").read_text(encoding="utf-8")
    report_data = json.loads(report.read_text(encoding="utf-8"))
    if report_data["version"] != tag:
        raise ValueError("The report belongs to another release.")
    metadata = release_metadata(repository, tag)
    if metadata["isDraft"] or metadata["tagName"] != tag:
        raise ValueError("The release is no longer the expected published release.")
    # Fetch the body immediately before editing; the analysis may have taken several minutes.
    updated = replace_report(metadata["body"], notes)
    body_file = output_dir / "release-body.md"
    body_file.write_text(updated, encoding="utf-8")
    # These are the only replaceable assets. Published APKs are never uploaded or changed here.
    github("release", "upload", tag, str(report), str(badge), "--repo", repository, "--clobber")
    github("release", "edit", tag, "--repo", repository, "--notes-file", str(body_file))
    publish_latest(repository, tag, badge, report_data["status"])
    print(f"Attached VirusTotal report to {tag}; published APKs were preserved.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("prepare", "publish"))
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", "wyrtensi/wystore"))
    parser.add_argument("--release-tag", default="latest")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--artifact-dir", type=Path)
    args = parser.parse_args(argv)
    if not REPOSITORY.fullmatch(args.repository):
        parser.error("Invalid GitHub repository name.")
    try:
        if args.operation == "prepare":
            prepare(args.repository, args.release_tag, args.output_dir.resolve(),
                    args.artifact_dir.resolve() if args.artifact_dir else None)
        else:
            publish(args.repository, args.output_dir.resolve())
    except subprocess.CalledProcessError as error:
        print(f"GitHub release operation failed with exit code {error.returncode}.", file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
