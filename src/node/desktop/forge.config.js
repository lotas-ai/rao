const process = require("process");
const path = require("path");

const config = {
  plugins: [
    {
      name: '@electron-forge/plugin-webpack',
      config: {
        mainConfig: './webpack.main.config.js',
        renderer: {
          config: './webpack.renderer.config.js',
          entryPoints: [
            {
              js: './src/renderer/renderer.ts',
              name: 'main_window',
              html: './src/renderer/renderer.html',
              preload: {
                name: 'preload',
                js: './src/renderer/preload.ts',
              },
            },
            {
              html: './src/ui/loading/loading.html',
              js: './src/ui/loading/loading.ts',
              name: 'loading_window',
            },
            {
              html: './src/ui/error/error.html',
              js: './src/ui/error/error.ts',
              name: 'error_window',
            },
            {
              html: './src/ui/connect/connect.html',
              js: './src/ui/connect/connect.ts',
              name: 'connect_window',
            },
            {
              html: './src/ui/widgets/choose-r/ui.html',
              js: './src/ui/widgets/choose-r/load.ts',
              preload: {
                js: './src/ui/widgets/choose-r/preload.ts',
              },
              name: 'choose_r',
            },
            {
              html: './src/ui/splash/splash.html',
              js: './src/ui/splash/splash.ts',
              name: 'splash',
            }
          ],
        },
      }
          // uncoment and change these ports to launch multiple debug instances
          // port: 3000,
          // loggerPort: 9000,
    },
  ],

  // https://electron.github.io/electron-packager/main/interfaces/electronpackager.options.html 
  packagerConfig: {
    icon: './resources/icons/Rao',
    appBundleId: 'ai.lotas.rao',
    // appCopyright: `Copyright (C) ${new Date().getFullYear()} by Posit Software, PBC`,
    name: 'Rao',
    executableName: process.platform === 'darwin' ? 'Rao' : 'rao',
    win32metadata: {
      CompanyName: "Lotas",
      FileDescription: "Rao",
      InternalName: "Rao",
      ProductName: "Rao",
    },
    extendInfo: './Info.plist.in',
    extraResource: process.platform === 'win32' ? (() => {
      const fs = require('fs');
      const pkgTempDir = process.env.PKG_TEMP_DIR;
      
      if (!pkgTempDir) {
        console.warn('PKG_TEMP_DIR environment variable not set, skipping CPack integration');
        return [];
      }
      
      const cpackDir = path.join(pkgTempDir, '_CPack_Packages/win64/NSIS');
      
      // Check if CPack directory exists (it might not exist during early build phases)
      if (!fs.existsSync(cpackDir)) {
        console.warn(`CPack directory ${cpackDir} does not exist yet, skipping CPack integration`);
        return [];
      }
      
      try {
        const packages = fs.readdirSync(cpackDir).filter(name => name.startsWith('Rao-'));
        
        if (packages.length === 0) {
          console.warn(`No Rao packages found in ${cpackDir}`);
          return [];
        }
        
        console.log(`Found CPack package: ${packages[0]}`);
        return [path.join(cpackDir, packages[0], 'resources/app')];
      } catch (error) {
        console.warn(`Failed to read CPack directory ${cpackDir}: ${error.message}`);
        return [];
      }
    })() : [],
  },

  hooks: {
    postMake: async (forgeConfig, makeResults) => {
      // Add delay to prevent file locking issues
      await new Promise(resolve => setTimeout(resolve, 2000));
      return makeResults;
    }
  },

  makers: [
    {
      name: '@electron-forge/maker-squirrel',
      config: {
        name: 'rao',
        authors: 'Lotas',
        description: 'Rao IDE',
        setupIcon: './resources/icons/Rao.ico',
        noMsi: true
      }
    },
    {
      name: '@electron-forge/maker-zip',
      platforms: ['darwin', 'linux']  // Exclude win32 to prevent conflicts with Squirrel
    },
    {
      name: '@electron-forge/maker-deb',
      config: {}
    },
    {
      name: '@electron-forge/maker-rpm',
      config: {}
    }
  ]
};

module.exports = config;
