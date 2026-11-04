# VirusTotal release reports

Large APK uploads use the one-time URL from `/files/upload_url`. Only HTTPS on
`www.virustotal.com` or the official `bigfiles.virustotal.com` upload host is accepted;
the latter is allowed only for uploads. Redirects, other hosts, credentials and
nonstandard ports are rejected. See the [VirusTotal large-file API](https://docs.virustotal.com/reference/files-upload-url)
and [official host announcement](https://blog.virustotal.com/2024/11/important-update-ip-address-change-for.html).

The automatic analysis starts at least 60 seconds after the release job finishes.
That job hands its built APKs to the analysis job through a one-day workflow artifact;
each alias must match the SHA-256, size and uploaded state of the corresponding published
release asset before scanning. Manual analyses download both published aliases instead.
The analysis updates the existing release description and report assets without replacing
either APK or rebuilding the application.

The Release workflow publishes the signed APK first. A separate VirusTotal job then downloads
the two published APK names, verifies that their SHA-256 hashes match, uploads the file once,
and waits up to 15 minutes for the completed analysis. Detections do not block publication.
An API error or timeout fails the analysis job and produces an explicit error or timeout report.
It is never represented as zero detections.

## Setup and execution

Store the API key in the repository Actions secret `VIRUSTOTAL_API_KEY`. The key is only passed
to the scan step; it is not written to reports, URLs, the Android app, or the website.

The automatic job runs after each successful release, starting with the next published version.
To repeat a scan, run the **VirusTotal** workflow manually and enter an existing release tag,
such as `v0.6.2`, or `latest`. This does not rebuild, replace, or re-sign either APK.

The Public API currently permits four requests per minute and 500 per day, subject to its
non-commercial usage restrictions. All calls are spaced by at least 16 seconds. Files over
32 MiB use the large-file upload URL. Authentication and malformed responses fail explicitly;
uploads are not automatically repeated after an uncertain or rate-limited response.

## Published data

Each release contains `virustotal-report.json` and `virustotal-badge.json`. The report includes
the release version, APK SHA-256, size, analysis ID, timestamps, and detection counts. The release
notes contain a fixed badge snapshot and a direct link to that APK's VirusTotal report.
The badge denominator excludes unsupported engines, failed analyses, and timeouts.

The README and website use a Shields endpoint backed by
`https://raw.githubusercontent.com/wyrtensi/wystore/release-reports/virustotal-latest.json`.
Shields blocks direct GitHub release-download endpoints. The report workflow creates and updates
the separate `release-reports` branch using an orphan commit containing only public badge data.
Report commits use `wyrtensi <wyrtensi@gmail.com>` as both author and committer. Future commits
preserve other files on that branch and never force-update its ref.

Only a scan of GitHub's current latest release updates this feed. A manual scan of an older tag
updates that release's own report but does not replace the latest badge. The version appears in
the badge because Shields and GitHub can cache responses. Before the first report-bearing release,
the latest badge source does not exist; publish the website changes with that release.

VirusTotal reports are not Google Play Protect approvals and do not guarantee that an app is safe.
Uploaded release APKs enter VirusTotal's shared dataset; only public release files are submitted.

## Local checks

```sh
python -m unittest discover -s tools/tests -v
actionlint
python tools/verify.py --release
node .github/scripts/site-build.mjs
```

The Python tests use deterministic synthetic responses and make no live API calls. Live API
access and report publication must also be checked in the first release run.

References: [API limits](https://docs.virustotal.com/reference/public-vs-premium-api),
[uploading files](https://docs.virustotal.com/reference/files-scan),
[analysis states](https://docs.virustotal.com/reference/analyses-object),
[Shields endpoint badges](https://shields.io/badges/endpoint-badge).
