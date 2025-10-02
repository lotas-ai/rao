#!/usr/bin/env node

const fs = require('fs');
const os = require('os');
const path = require('path');
const { exec: pkgExec } = require('pkg');

const root = path.resolve(__dirname, '..');
const entry = path.join(root, 'out', 'bundle.js');
const distDir = path.join(root, 'dist');

const platform = os.platform();

const targetsByPlatform = {
  darwin: [
    { target: 'node18-macos-arm64', suffix: 'macos-arm64' },
    { target: 'node18-macos-x64', suffix: 'macos-x64' },
  ],
  linux: [
    { target: 'node18-linux-x64', suffix: 'linux-x64' },
  ],
  win32: [
    { target: 'node18-win-x64', suffix: 'win-x64.exe' },
  ],
};

const targets = targetsByPlatform[platform];
if (!targets) {
  process.stderr.write(`Unsupported platform for packaging: ${platform}\n`);
  process.exit(1);
}

async function run() {
  try {
    fs.rmSync(distDir, { recursive: true, force: true });
    fs.mkdirSync(distDir, { recursive: true });
    for (const { target, suffix } of targets) {
      const outputFile = path.join(distDir, `rao-local-backend-${suffix}`);
      const cliArgs = [
        entry,
        '--targets',
        target,
        '--output',
        outputFile,
      ];
      await pkgExec(cliArgs);
    }
  } catch (err) {
    process.stderr.write(`${err?.stack || err}\n`);
    process.exit(1);
  }
}

run();
