const process = require("process");

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
  },
};

module.exports = config;
