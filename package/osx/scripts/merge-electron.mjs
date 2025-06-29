
import 'process';
import { execSync } from 'child_process';
import { existsSync, rmSync } from 'fs';
import { makeUniversalApp } from '@electron/universal';

const [node, script, x64AppPath, arm64AppPath, outPath] = process.argv;

// for x64, we don't build a universal application; instead,
// we just copy the x64 Electron bits directly into the package
let tmpPath = "";
let hasArm64Build = existsSync(arm64AppPath);
if (hasArm64Build) {

  // build universal application in temporary directory,
  // then merge it all together when we're done
  tmpPath = `${outPath}.tmp`;

  // merge the two builds together
  console.log("# Building universal Desktop application.")
  console.log(`- [i] x64AppPath: ${x64AppPath}`)
  console.log(`- [i] arm64AppPath: ${arm64AppPath}`)
  await makeUniversalApp({
    x64AppPath: x64AppPath,
    arm64AppPath: arm64AppPath,
    outAppPath: tmpPath,
    force: true,
  });

} else {

  console.log("# Building x86_64 Electron application");
  console.log(`- [i] x64AppPath: ${x64AppPath}`)
  tmpPath = x64AppPath;

}

// use rsync to move them into the final install path
console.log("- [i] Merging desktop and session packages ...")
execSync(`rsync -a "${tmpPath}/" "${outPath}/"`, { stdio: 'inherit' });
console.log("- [i] Done!")

// clean up
if (hasArm64Build) {
  rmSync(tmpPath, { recursive: true });
}

