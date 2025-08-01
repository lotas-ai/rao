const electronWinstaller = require('electron-winstaller');
const path = require('path');

// Get paths from command line arguments or use defaults
const buildDir = process.argv[2] || 'build';
const electronAppDir = path.join(buildDir, 'out', 'Rao-win32-x64');

console.log('Creating Squirrel.Windows packages...');
console.log('Build directory:', buildDir);
console.log('Electron app directory:', electronAppDir);

electronWinstaller.createWindowsInstaller({
  appDirectory: electronAppDir,
  outputDirectory: path.join(buildDir, 'squirrel'),
  authors: 'Lotas',
  exe: 'rao.exe',
  iconUrl: 'https://lotas-downloads.s3.us-east-2.amazonaws.com/icon.ico',
  setupIcon: path.join(electronAppDir, 'resources', 'app', 'resources', 'icons', 'Rao.ico'),
  noMsi: true
}).then(function() {
  console.log('Squirrel packages created successfully');
  console.log('Output directory:', path.join(buildDir, 'squirrel'));
}).catch(function(e) {
  console.error('Squirrel package creation failed:', e);
  process.exit(1);
});