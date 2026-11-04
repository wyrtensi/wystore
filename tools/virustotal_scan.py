#!/usr/bin/env python3
"""Upload one release APK to VirusTotal and write release assets."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from email.utils import parsedate_to_datetime

API = 'https://www.virustotal.com/api/v3'
MAX_SIZE = 650 * 1024 * 1024
SMALL_SIZE = 32 * 1024 * 1024
INTERVAL = 16
TIMEOUT = 15 * 60


class ScanError(Exception):
    pass


class ScanTimeout(ScanError):
    pass


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds').replace('+00:00', 'Z')


def hash_file(path):
    digest = hashlib.sha256()
    size = 0
    with open(path, 'rb') as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def url_rejection_reason(url, upload=False):
    try:
        parsed = urllib.parse.urlsplit(url)
        hosts = {'www.virustotal.com'}
        # The large-file endpoint returns a one-use URL on this separate official host.
        if upload:
            hosts.add('bigfiles.virustotal.com')
        if parsed.scheme != 'https':
            return 'HTTPS is required'
        if parsed.hostname not in hosts:
            return 'host is not approved'
        if parsed.port not in (None, 443):
            return 'port is not approved'
        if parsed.username or parsed.password or parsed.fragment:
            return 'credentials or fragment are not allowed'
        return None
    except ValueError:
        return 'URL is malformed'


def trusted_url(url, upload=False):
    return url_rejection_reason(url, upload) is None


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def transport(method, url, headers, body, timeout):
    rejection = url_rejection_reason(url, upload=method == 'POST')
    if rejection:
        raise ScanError(f'VirusTotal returned an untrusted upload URL: {rejection}')
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=timeout) as response:
            return response.status, dict(response.headers), response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers), b''
    except (urllib.error.URLError, OSError, TimeoutError):
        raise ScanError('VirusTotal request failed') from None


class Client:
    def __init__(self, key, send=transport, clock=time.monotonic, sleep=time.sleep):
        self.key, self.send, self.clock, self.sleep = key, send, clock, sleep
        self.last = None
        self.deadline = clock() + TIMEOUT

    def wait(self, seconds):
        remaining = self.deadline - self.clock()
        if seconds >= remaining:
            raise ScanTimeout('VirusTotal analysis timed out')
        if seconds > 0:
            self.sleep(seconds)

    def request(self, method, url, body=None, headers=None):
        while True:
            if self.last is not None:
                self.wait(max(0, INTERVAL - (self.clock() - self.last)))
            if self.clock() >= self.deadline:
                raise ScanTimeout('VirusTotal analysis timed out')
            request_headers = {'x-apikey': self.key, **(headers or {})}
            self.last = self.clock()
            status, response_headers, raw = self.send(method, url, request_headers, body,
                                                      min(60, self.deadline - self.clock()))
            if status == 429 and method == 'GET':
                value = next((v for k, v in response_headers.items() if k.lower() == 'retry-after'), '16')
                try:
                    delay = max(0, float(value))
                except ValueError:
                    try:
                        delay = max(0, (parsedate_to_datetime(value) - dt.datetime.now(dt.timezone.utc)).total_seconds())
                    except (TypeError, ValueError, OverflowError):
                        delay = INTERVAL
                self.wait(max(INTERVAL, delay))
                continue
            if status == 429:
                raise ScanError('VirusTotal rate limited the upload; it was not retried')
            if status in (401, 403):
                raise ScanError('VirusTotal authentication or permission failed')
            if not 200 <= status < 300:
                raise ScanError(f'VirusTotal request failed with HTTP {status}')
            try:
                return json.loads(raw)
            except (ValueError, UnicodeDecodeError, TypeError):
                raise ScanError('VirusTotal returned invalid JSON') from None


def multipart(path):
    boundary = '----wystore' + uuid.uuid4().hex
    prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
              'filename="release.apk"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()
    suffix = f'\r\n--{boundary}--\r\n'.encode()
    def content():
        yield prefix
        with open(path, 'rb') as source:
            while chunk := source.read(1024 * 1024):
                yield chunk
        yield suffix
    headers = {'Content-Type': f'multipart/form-data; boundary={boundary}',
               'Content-Length': str(len(prefix) + path.stat().st_size + len(suffix))}
    return content(), headers


def obj(value):
    if not isinstance(value, dict):
        raise ScanError('VirusTotal returned an invalid response schema')
    return value


def scan(client, path, digest, size):
    url = API + '/files'
    if size > SMALL_SIZE:
        url = obj(client.request('GET', API + '/files/upload_url')).get('data')
        if not isinstance(url, str):
            raise ScanError('VirusTotal returned an invalid upload URL schema')
        rejection = url_rejection_reason(url, upload=True)
        if rejection:
            raise ScanError(f'VirusTotal returned an untrusted upload URL: {rejection}')
    body, headers = multipart(path)
    upload = obj(obj(client.request('POST', url, body, headers)).get('data'))
    analysis_id = upload.get('id')
    if not isinstance(analysis_id, str) or not re.fullmatch(r'[A-Za-z0-9_=-]{1,512}', analysis_id):
        raise ScanError('VirusTotal returned an invalid analysis ID')
    while True:
        response = obj(client.request('GET', API + '/analyses/' + analysis_id))
        metadata = obj(response.get('meta', {}))
        if 'file_info' in metadata:
            info = obj(metadata['file_info'])
            if 'sha256' in info and info['sha256'] != digest:
                raise ScanError('VirusTotal analysis hash does not match the APK')
        data = obj(response.get('data'))
        if data.get('id') != analysis_id:
            raise ScanError('VirusTotal analysis ID does not match the upload')
        attrs = obj(data.get('attributes'))
        status = attrs.get('status')
        if status in ('queued', 'in-progress'):
            continue
        if status != 'completed':
            raise ScanError('VirusTotal returned an invalid analysis status')
        stats = obj(attrs.get('stats'))
        counts = []
        for name in ('malicious', 'suspicious', 'harmless', 'undetected'):
            value = stats.get(name)
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ScanError('VirusTotal returned invalid analysis statistics')
            counts.append(value)
        if not sum(counts):
            raise ScanError('VirusTotal returned no completed engine judgments')
        date = attrs.get('date')
        if isinstance(date, bool) or not isinstance(date, int) or date < 0:
            raise ScanError('VirusTotal returned an invalid analysis date')
        try:
            date_string = dt.datetime.fromtimestamp(date, dt.timezone.utc).isoformat(timespec='seconds').replace('+00:00', 'Z')
        except (ValueError, OverflowError, OSError):
            raise ScanError('VirusTotal returned an invalid analysis date') from None
        file_data = obj(obj(client.request('GET', API + '/files/' + digest)).get('data'))
        file_attrs = obj(file_data.get('attributes'))
        if file_data.get('id') != digest or file_attrs.get('sha256') != digest or type(file_attrs.get('size')) is not int or file_attrs['size'] != size:
            raise ScanError('VirusTotal file identity does not match the APK')
        return {'analysisId': analysis_id, 'analysisDate': date_string, 'status': 'completed',
                'malicious': counts[0], 'suspicious': counts[1], 'analysed': sum(counts)}


def write_atomic(path, contents):
    fd, name = tempfile.mkstemp(dir=path.parent, prefix='.virustotal-', suffix='.tmp')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as output:
            output.write(contents)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def write_assets(directory, report):
    directory.mkdir(parents=True, exist_ok=True)
    status = report['status']
    if status == 'completed':
        color = 'red' if report['malicious'] else 'orange' if report['suspicious'] else '40835b'
        detections = report['malicious'] + report['suspicious']
        message = f"{report['version']}: {detections}/{report['analysed']}"
    else:
        color = 'yellow' if status == 'pending' else 'lightgrey'
        message = f"{report['version']}: {status}"
    badge = {'schemaVersion': 1, 'label': 'VirusTotal', 'message': message, 'color': color}
    # Release notes contain a permanent snapshot. Shields blocks github.com release-asset URLs.
    shields = ('https://img.shields.io/static/v1?' + urllib.parse.urlencode({
        'label': 'VirusTotal', 'message': message, 'color': color, 'style': 'flat',
        'logo': 'virustotal', 'logoColor': 'white', 'labelColor': '202938'}))
    summary = (f"{report['malicious']} malicious, {report['suspicious']} suspicious / {report['analysed']} completed engine results"
               if status == 'completed' else status)
    notes = (f'<!-- wystore-virustotal:start -->\n[![VirusTotal]({shields})]({report["report_url"]})\n\n'
             f'VirusTotal: {summary}  \n'
             f'APK SHA-256: [{report["sha256"]}]({report["report_url"]})  \nReport updated: {report["checkedAt"]}\n'
             'This report is not a Google Play Protect approval.\n'
             '<!-- wystore-virustotal:end -->\n')
    for name, content in [('virustotal-report.json', json.dumps(report, indent=2) + '\n'),
                          ('virustotal-badge.json', json.dumps(badge, indent=2) + '\n'),
                          ('virustotal-notes.md', notes)]:
        write_atomic(directory / name, content)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--alias-apk', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args(argv)
    try:
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', args.version):
            raise ScanError('Invalid release version')
        digest, size = hash_file(args.apk)
        alias_digest, alias_size = hash_file(args.alias_apk)
        if (digest, size) != (alias_digest, alias_size):
            raise ScanError('APK aliases have different contents')
        report = {'version': args.version, 'sha256': digest, 'size': size, 'checkedAt': now(),
                  'analysisDate': None, 'analysisId': None, 'status': 'pending',
                  'malicious': None, 'suspicious': None, 'analysed': None,
                  'report_url': 'https://www.virustotal.com/gui/file/' + digest}
        try:
            if size > MAX_SIZE:
                raise ScanError('APK exceeds the VirusTotal file size limit')
            if not args.prepare_only:
                key = os.environ.get('VIRUSTOTAL_API_KEY')
                if not key:
                    raise ScanError('VIRUSTOTAL_API_KEY is missing')
                report.update(scan(Client(key), args.apk, digest, size))
                report['checkedAt'] = now()
        except ScanError as exc:
            report['status'] = 'timeout' if isinstance(exc, ScanTimeout) else 'error'
            report['checkedAt'] = now()
            write_assets(args.output_dir, report)
            print(str(exc), file=sys.stderr)
            return 1
        write_assets(args.output_dir, report)
        return 0
    except (ScanError, OSError) as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
