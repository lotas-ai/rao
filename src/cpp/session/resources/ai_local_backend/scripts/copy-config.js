#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const sourceDir = path.join(root, 'src', 'config');
const targetDir = path.join(root, 'out', 'config');

if (!fs.existsSync(sourceDir)) {
  process.stderr.write(`Source config directory not found: ${sourceDir}\n`);
  process.exit(1);
}

fs.rmSync(targetDir, { recursive: true, force: true });
fs.mkdirSync(targetDir, { recursive: true });

const entries = fs.readdirSync(sourceDir, { withFileTypes: true });
for (const entry of entries) {
  const from = path.join(sourceDir, entry.name);
  const to = path.join(targetDir, entry.name);
  if (entry.isDirectory()) {
    fs.cpSync(from, to, { recursive: true });
  } else if (entry.isFile()) {
    fs.copyFileSync(from, to);
  }
}
