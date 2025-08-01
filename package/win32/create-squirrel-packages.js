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

// WORKAROUND: Copy R directory to app root if electron-winstaller excludes it
const rDir = path.join(electronAppDir, 'resources', 'app', 'R');
const rDestDir = path.join(electronAppDir, 'R');

console.log('DEBUG: Checking R directory workaround...');
console.log('DEBUG: R source dir:', rDir);
console.log('DEBUG: R dest dir:', rDestDir);
console.log('DEBUG: R source exists:', fs.existsSync(rDir));
console.log('DEBUG: R dest exists:', fs.existsSync(rDestDir));

if (fs.existsSync(rDir)) {
  if (!fs.existsSync(rDestDir)) {
    console.log('Copying R directory to app root as workaround...');
    fs.cpSync(rDir, rDestDir, { recursive: true });
    console.log('✓ R directory copied to app root');
  } else {
    console.log('R directory already exists in app root, skipping copy');
  }
} else {
  console.log('ERROR: R directory not found in source Electron app at:', rDir);
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