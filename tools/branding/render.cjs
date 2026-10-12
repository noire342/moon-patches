// SPDX-License-Identifier: GPL-3.0-only
// Run from the repository root after installing sharp.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const assets = path.join(root, 'docs/branding');
const resources = path.join(root, 'patches/src/main/resources');
async function png(input, destination, width, height) {
  fs.mkdirSync(path.dirname(destination), {recursive:true});
  await sharp(input).resize(width,height).png().toFile(destination);
}
async function main() {
  const icon = fs.readFileSync(path.join(assets,'Moon-icon.svg'),'utf8');
  const sphere = icon.replace('width="1024" height="1024" viewBox="0 0 1024 1024"',
    'width="256" height="256" viewBox="64 64 896 896"');
  const foreground = await sharp(Buffer.from(sphere)).resize(256,256)
    .extend({top:88,bottom:88,left:88,right:88,background:{r:0,g:0,b:0,alpha:0}}).png().toBuffer();
  fs.writeFileSync(path.join(resources,'custom-branding/drawable-nodpi/moon_launcher_foreground.png'),foreground);
  await png(Buffer.from(icon),path.join(root,'patches-bundle.png'),512,512);
  await png(Buffer.from(icon),path.join(resources,'settings/drawable-nodpi/moon_about_icon.png'),192,192);
  for (const [density,size] of Object.entries({mdpi:108,hdpi:162,xhdpi:216,xxhdpi:324,xxxhdpi:432})) {
    const directory=path.join(resources,`custom-branding/mipmap-${density}`);
    await png(foreground,path.join(directory,'morphe_adaptive_foreground_custom.png'),size,size);
    await sharp({create:{width:size,height:size,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).png()
      .toFile(path.join(directory,'morphe_adaptive_background_custom.png'));
  }
  for (const theme of ['light','dark']) {
    const master=fs.readFileSync(path.join(assets,theme==='light'?'Moon-wordmark.svg':'Moon-wordmark-dark.svg'));
    for (const [density,dimensions] of Object.entries({hdpi:[194,72],xhdpi:[258,96],xxhdpi:[387,144],xxxhdpi:[512,192]})) {
      const directory=path.join(resources,`change-header/drawable-${density}`);
      await png(master,path.join(directory,`moon_wordmark_${theme}.png`),...dimensions);
      await png(master,path.join(directory,`morphe_header_custom_${theme}.png`),...dimensions);
    }
  }
  console.log('Moon density exports, custom placeholders and bundle icon generated.');
}
main().catch(error=>{console.error(error);process.exitCode=1});
