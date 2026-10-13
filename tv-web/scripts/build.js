/*
 * Assembles the TV packages' folders (no dependencies):
 *   dist/webos  -> ares-package dist/webos -o dist          (LG, .ipk)
 *   dist/tizen  -> tizen package -t wgt -s <profile> -- dist/tizen   (Samsung, .wgt)
 * The version comes from package.json and is written into appinfo.json and config.xml.
 *
 * Usage: node scripts/build.js
 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const src = path.join(root, 'src');
const dist = path.join(root, 'dist');
const { version } = require(path.join(root, 'package.json'));

const PLATFORM_SCRIPTS = {
  webos: '',
  // Samsung's product APIs (avplay, appcommon) come from the TV firmware, not from the package.
  tizen: '<script src="$WEBAPIS/webapis/webapis.js"></script>',
};

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, entry.name);
    const b = path.join(to, entry.name);
    if (entry.isDirectory()) copyDir(a, b);
    else fs.copyFileSync(a, b);
  }
}

function build(platform) {
  const out = path.join(dist, platform);
  fs.rmSync(out, { recursive: true, force: true });
  copyDir(src, out);
  copyDir(path.join(root, 'platforms', platform), out);

  const indexPath = path.join(out, 'index.html');
  const html = fs.readFileSync(indexPath, 'utf8');
  if (!html.includes('<!-- platform-scripts -->')) throw new Error('index.html lost its platform-scripts marker');
  fs.writeFileSync(indexPath, html.replace('<!-- platform-scripts -->', PLATFORM_SCRIPTS[platform]));

  if (platform === 'webos') {
    const infoPath = path.join(out, 'appinfo.json');
    const info = JSON.parse(fs.readFileSync(infoPath, 'utf8'));
    info.version = version;
    fs.writeFileSync(infoPath, JSON.stringify(info, null, 2) + '\n');
  } else {
    const configPath = path.join(out, 'config.xml');
    const xml = fs.readFileSync(configPath, 'utf8').replace(/(<widget[^>]*\sversion=")[^"]*"/, `$1${version}"`);
    fs.writeFileSync(configPath, xml);
  }
  console.log(`${platform}: ${path.relative(process.cwd(), out)} (version ${version})`);
}

if (!/^\d+\.\d+\.\d+$/.test(version)) throw new Error(`package.json version must be major.minor.patch, got ${version}`);
build('webos');
build('tizen');
