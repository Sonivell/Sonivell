// Gera os PNGs do ícone a partir dos SVGs (usa o Chromium do Playwright).
// Uso: node tools/gen-icons.cjs
const { chromium } = require('playwright');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');

const dir = join(__dirname, '..', 'icons');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [src, out, size] of [
    ['icon.svg', 'icon-192.png', 192], ['icon.svg', 'icon-512.png', 512], ['maskable.svg', 'maskable-512.png', 512],
  ]) {
    const svg = readFileSync(join(dir, src), 'utf8');
    await page.setViewportSize({ width: size, height: size });
    await page.setContent(`<style>html,body{margin:0;background:transparent}</style><img src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}" width="${size}" height="${size}">`);
    await page.locator('img').screenshot({ path: join(dir, out), omitBackground: true });
  }
  await browser.close();
})();
