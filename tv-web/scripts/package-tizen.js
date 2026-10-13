/*
 * Signs dist/tizen into dist/TSIPTV.wgt (run scripts/build.js first).
 *
 * The Tizen CLI names the package after <name> in config.xml ("TS IPTV.wgt"); the space breaks
 * `tizen install -n` on Windows, so the result is moved to a name without one.
 *
 * Usage: node scripts/package-tizen.js
 *   TIZEN_PROFILE  security profile to sign with (default TSIPTV)
 *   TIZEN_STUDIO   Tizen Studio folder, when `tizen` is not on PATH
 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const input = path.join(root, 'dist', 'tizen');
const output = path.join(root, 'dist', 'TSIPTV.wgt');
const profile = process.env.TIZEN_PROFILE || 'TSIPTV';
const windows = process.platform === 'win32';
if (!/^[\w.-]+$/.test(profile)) throw new Error(`TIZEN_PROFILE must be a plain name, got ${profile}`);

function tizenCli() {
  const studio = process.env.TIZEN_STUDIO;
  if (!studio) return windows ? 'tizen.bat' : 'tizen';
  return path.join(studio, 'tools', 'ide', 'bin', windows ? 'tizen.bat' : 'tizen');
}

for (const name of fs.readdirSync(input)) if (name.endsWith('.wgt')) fs.rmSync(path.join(input, name));
fs.rmSync(output, { force: true });

const args = ['package', '-t', 'wgt', '-s', profile, '--', input];
// A .bat only runs through cmd.exe, which takes one command line.
const result = windows
  ? spawnSync([tizenCli(), ...args].map((a) => `"${a}"`).join(' '), { stdio: 'inherit', shell: true })
  : spawnSync(tizenCli(), args, { stdio: 'inherit' });
if (result.status !== 0) process.exit(result.status || 1);

const wgt = fs.readdirSync(input).find((name) => name.endsWith('.wgt'));
if (!wgt) throw new Error('tizen package produced no .wgt (wrong profile name or password?)');
fs.renameSync(path.join(input, wgt), output);
console.log(`tizen: ${path.relative(process.cwd(), output)} (profile ${profile})`);
