const electronWinstaller = require('electron-winstaller');
const path = require('path');
const fs = require('fs');

// Get paths from command line arguments or use defaults
const buildDir = process.argv[2] || 'build';

// When run from desktop directory, the Electron app is in ./out/Rao-win32-x64
const electronAppDir = path.join(__dirname, 'out', 'Rao-win32-x64');

console.log('Creating Squirrel.Windows packages...');
console.log('Build directory:', buildDir);
console.log('Electron app directory:', electronAppDir);

// Check if the app directory exists
if (!fs.existsSync(electronAppDir)) {
  console.error('ERROR: Electron app directory not found at:', electronAppDir);
  process.exit(1);
}

electronWinstaller.createWindowsInstaller({
  appDirectory: electronAppDir,
  outputDirectory: path.join(buildDir, 'squirrel'),
  authors: 'Lotas',
  exe: 'rao.exe',
  noMsi: true
}).then(function() {
  console.log('Squirrel packages created successfully');
  console.log('Output directory:', path.join(buildDir, 'squirrel'));
}).catch(function(e) {
  console.error('Squirrel package creation failed:', e);
  process.exit(1);
});