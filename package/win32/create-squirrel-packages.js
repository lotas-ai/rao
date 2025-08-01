const electronWinstaller = require('electron-winstaller');
const path = require('path');
const fs = require('fs');

// Get paths from command line arguments or use defaults
const buildDir = process.argv[2] || 'build';

// When run from desktop directory, the Electron app is in ./out/Rao-win32-x64
const electronAppDir = path.join(__dirname, 'out', 'Rao-win32-x64');

// Ensure buildDir is absolute
const absoluteBuildDir = path.isAbsolute(buildDir) ? buildDir : path.resolve(buildDir);
const outputDir = path.join(absoluteBuildDir, 'squirrel');

console.log('Creating Squirrel.Windows packages...');
console.log('Build directory:', absoluteBuildDir);
console.log('Output directory:', outputDir);
console.log('Electron app directory:', electronAppDir);

// Check if the app directory exists
if (!fs.existsSync(electronAppDir)) {
  console.error('ERROR: Electron app directory not found at:', electronAppDir);
  process.exit(1);
}

// Debug: Check if bin directory exists in the app
const binDir = path.join(electronAppDir, 'resources', 'app', 'bin');
console.log('Checking for bin directory at:', binDir);
if (fs.existsSync(binDir)) {
  console.log('✓ bin directory exists');
  const binFiles = fs.readdirSync(binDir);
  console.log('Files in bin directory:', binFiles);
} else {
  console.error('✗ bin directory NOT found!');
}

electronWinstaller.createWindowsInstaller({
  appDirectory: electronAppDir,
  outputDirectory: outputDir,
  authors: 'Lotas',
  exe: 'rao.exe',
  noMsi: true
}).then(function() {
  console.log('Squirrel packages created successfully');
  console.log('Output directory:', outputDir);
}).catch(function(e) {
  console.error('Squirrel package creation failed:', e);
  process.exit(1);
});