const electronWinstaller = require('electron-winstaller');
const path = require('path');

// Get arguments from command line
const appDirectory = process.argv[2];
const outputDirectory = process.argv[3];

if (!appDirectory || !outputDirectory) {
  console.error('Usage: node create-squirrel-packages.js <appDirectory> <outputDirectory>');
  process.exit(1);
}

console.log('Creating Squirrel.Windows packages...');
console.log('App Directory:', appDirectory);
console.log('Output Directory:', outputDirectory);

electronWinstaller.createWindowsInstaller({
  appDirectory: appDirectory,
  outputDirectory: outputDirectory,
  authors: 'Lotas',
  exe: 'rao.exe',
  iconUrl: 'https://lotas-downloads.s3.us-east-2.amazonaws.com/icon.ico',
  noMsi: true
}).then(() => {
  console.log('Squirrel packages created successfully');
  process.exit(0);
}).catch((e) => {
  console.error('Squirrel package creation failed:', e);
  process.exit(1);
}); 