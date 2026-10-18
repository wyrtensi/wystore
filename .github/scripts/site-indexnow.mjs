// Tells IndexNow search engines (Yandex, Bing and others share submissions)
// that the pages in the sitemap changed. The key is public by design: the
// engines confirm ownership by fetching docs/<key>.txt from the site.
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const INDEXNOW_KEY = '971c30650f2f20806510a616738dc07a';
const host = 'wystore.ru';

const sitemap = await readFile(resolve(import.meta.dirname, '../../docs/sitemap.xml'), 'utf8');
const urlList = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]);
const response = await fetch('https://api.indexnow.org/indexnow', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json; charset=utf-8' },
  body: JSON.stringify({ host, key: INDEXNOW_KEY, keyLocation: `https://${host}/${INDEXNOW_KEY}.txt`, urlList })
});
// 200 and 202 both mean accepted; anything else is a real failure.
if (response.status !== 200 && response.status !== 202) {
  console.error(`IndexNow rejected the submission: HTTP ${response.status} ${await response.text()}`);
  process.exit(1);
}
console.log(`IndexNow accepted ${urlList.length} URLs (HTTP ${response.status}).`);
