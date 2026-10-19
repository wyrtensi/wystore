// Assembles the public site from docs/ into dist/ and checks it before the
// deploy job may publish it. docs/ also holds repository material (plans,
// store listing, README screenshots) that must not end up on wystore.ru, so
// only the paths in PUBLIC are copied.
//
// The app's version in each page's JSON-LD is written here from
// app/build.gradle.kts. Kept by hand, it was missed on every release and told
// search engines 0.2.1 until 0.4.1.
import { cp, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const docs = join(root, 'docs');
const dist = join(root, 'dist');
const LANGS = ['en', 'uk', 'be', 'kk', 'uz', 'lv', 'zh'];
const PUBLIC = ['index.html', '404.html', ...LANGS, 'assets', '.well-known', 'favicon.ico', 'robots.txt', 'sitemap.xml', 'security.txt'];

await rm(dist, { recursive: true, force: true });
await mkdir(dist);
for (const entry of PUBLIC) {
  if (existsSync(join(docs, entry))) await cp(join(docs, entry), join(dist, entry), { recursive: true });
}
// Verification and IndexNow key files, and the images other sites already link to.
for (const name of await readdir(docs)) {
  if (/^(google[0-9a-f]+\.html|yandex_[0-9a-f]+\.html|[0-9a-f]{32}\.txt|logo[\w-]*\.png|app_icon\.png|banner[\w-]*\.png)$/.test(name)) {
    await cp(join(docs, name), join(dist, name));
  }
}

// The server keeps assets for a day and revalidates pages (deploy/server/Caddyfile), so after a
// deploy a browser paired the new page with yesterday's style.css: the fonts the page preloaded
// went unused, with a console warning, and the look was a day behind. Each asset a page names
// gets the hash of its contents in the query, so a changed file is a new URL and an unchanged
// one stays cached. Fonts keep their plain URL: style.css names them without a query, and a
// preload must ask for exactly that URL or the browser fetches the font again and the preload
// is wasted.
const ASSET_REF = /((?:src|href|data-src)=")((?:\.{1,2})?\/[^"#?]*assets\/[^"#?]+)"/g;

async function stampAssets(html, pageDir) {
  const hashes = new Map();
  for (const [, , ref] of html.matchAll(ASSET_REF)) {
    if (/\.woff2?$/.test(ref) || hashes.has(ref)) continue;
    const target = ref.startsWith('/') ? join(dist, ref) : resolve(pageDir, ref);
    // A missing file is the link check's to report.
    if (!existsSync(target)) continue;
    hashes.set(ref, createHash('sha256').update(await readFile(target)).digest('hex').slice(0, 10));
  }
  return html.replace(ASSET_REF, (match, attr, ref) => (hashes.has(ref) ? `${attr}${ref}?v=${hashes.get(ref)}"` : match));
}

const problems = [];
const gradle = await readFile(join(root, 'app', 'build.gradle.kts'), 'utf8');
const version = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
if (!version) problems.push('app/build.gradle.kts: versionName not found');

const sitemap = await readFile(join(dist, 'sitemap.xml'), 'utf8');
const pages = [...sitemap.matchAll(/<loc>https:\/\/wystore\.ru(\/[^<]*)<\/loc>/g)].map((match) => match[1]);
if (pages.length !== LANGS.length + 1) problems.push(`sitemap lists ${pages.length} pages, expected ${LANGS.length + 1}`);

for (const page of pages) {
  const file = join(dist, page, 'index.html');
  if (!existsSync(file)) {
    problems.push(`${page}: listed in the sitemap but dist has no index.html`);
    continue;
  }
  let html = await readFile(file, 'utf8');
  const softwareVersion = /("softwareVersion":\s*)"[^"]*"/;
  if (!softwareVersion.test(html)) {
    problems.push(`${page}: no "softwareVersion" in its JSON-LD`);
  } else if (version) {
    html = html.replace(softwareVersion, `$1"${version}"`);
  }
  if (!html.includes(`<link rel="canonical" href="https://wystore.ru${page}">`)) problems.push(`${page}: canonical does not point at itself`);
  for (const other of pages) {
    if (!html.includes(`href="https://wystore.ru${other}"`)) problems.push(`${page}: no hreflang link to ${other}`);
  }
  // Every relative src/href must resolve to a copied file.
  for (const [, ref] of html.matchAll(/(?:src|href)="(\.{1,2}\/[^"#?]*)/g)) {
    const target = resolve(join(dist, page), ref);
    const isDir = existsSync(target) && (await stat(target)).isDirectory();
    if (!existsSync(isDir ? join(target, 'index.html') : target)) problems.push(`${page}: broken link ${ref}`);
  }
  await writeFile(file, await stampAssets(html, join(dist, page)));
}
if (!existsSync(join(dist, '404.html'))) {
  problems.push('404.html is missing');
} else {
  const notFound = join(dist, '404.html');
  await writeFile(notFound, await stampAssets(await readFile(notFound, 'utf8'), dist));
}

if (problems.length > 0) {
  console.error('Site check failed:');
  for (const problem of problems) console.error(`- ${problem}`);
  process.exit(1);
}
console.log(`Site OK: ${pages.length} pages in dist/, app version ${version}.`);
