import test from 'node:test';
import assert from 'node:assert/strict';
import { reportUrl } from '../../docs/assets/virustotal-redirect.mjs';

const sha256 = 'a'.repeat(64);
const url = `https://www.virustotal.com/gui/file/${sha256}`;
test('accepts a report URL matching its SHA-256', () => {
  assert.equal(reportUrl({ sha256, report_url: url }), url);
});
test('rejects mismatches and unsafe URLs', () => {
  for (const value of [
    `https://www.virustotal.com/gui/file/${'b'.repeat(64)}`,
    `http://www.virustotal.com/gui/file/${sha256}`,
    `https://www.virustotal.com.evil.example/gui/file/${sha256}`,
    `https://user@www.virustotal.com/gui/file/${sha256}`,
    `${url}?next=evil`, `${url}#fragment`, `${url}/`, 'not a URL',
  ]) assert.throws(() => reportUrl({ sha256, report_url: value }));
  for (const hash of ['', 'A'.repeat(64), 'a'.repeat(63), null]) {
    assert.throws(() => reportUrl({ sha256: hash, report_url: url }));
  }
});
