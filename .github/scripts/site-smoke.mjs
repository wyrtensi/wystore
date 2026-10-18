// Post-deploy check of the live site: every sitemap page answers 200 with
// HTML, unknown paths answer 404, www and http redirect to https://wystore.ru.
// Retried, because a fresh host name may wait a minute for its certificate.
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const base = 'https://wystore.ru';
const attempts = Number(process.env.SMOKE_ATTEMPTS ?? 10);
const delayMs = Number(process.env.SMOKE_DELAY_MS ?? 30_000);

const sitemap = await readFile(resolve(import.meta.dirname, '../../docs/sitemap.xml'), 'utf8');
const pages = [...sitemap.matchAll(/<loc>https:\/\/wystore\.ru([^<]*)<\/loc>/g)].map((match) => match[1]);

async function check() {
  const failures = [];
  try {
    for (const path of pages) {
      const response = await fetch(`${base}${path}`, { redirect: 'manual' });
      if (response.status !== 200) failures.push(`${path}: HTTP ${response.status}`);
      else if (!response.headers.get('content-type')?.startsWith('text/html')) failures.push(`${path}: not HTML`);
    }
    const missing = await fetch(`${base}/this-page-does-not-exist/`, { redirect: 'manual' });
    if (missing.status !== 404) failures.push(`unknown path: HTTP ${missing.status}, expected 404`);
    for (const from of ['https://www.wystore.ru/en/', 'http://wystore.ru/en/']) {
      const response = await fetch(from, { redirect: 'manual' });
      const location = response.headers.get('location');
      if (response.status < 300 || response.status > 308 || location !== `${base}/en/`) {
        failures.push(`${from}: HTTP ${response.status} to ${location}, expected a redirect to ${base}/en/`);
      }
    }
  } catch (error) {
    failures.push(`${error.cause?.code ?? error.name}: ${error.cause?.message ?? error.message}`);
  }
  return failures;
}

for (let attempt = 1; ; attempt += 1) {
  const failures = await check();
  if (failures.length === 0) {
    console.log(`Production OK: ${pages.length} pages, 404 page, www and http redirects.`);
    break;
  }
  if (attempt >= attempts) {
    console.error(`Production check failed after ${attempt} attempts:`);
    for (const failure of failures) console.error(`- ${failure}`);
    process.exit(1);
  }
  console.log(`Attempt ${attempt}: ${failures.length} problem(s), retrying in ${delayMs / 1000}s.`);
  await new Promise((done) => setTimeout(done, delayMs));
}
