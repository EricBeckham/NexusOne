import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = new URL('../../../web/src/styles/tokens.css', import.meta.url);
const target = new URL('../themes/nexusone/login/resources/css/nexus-tokens.css', import.meta.url);
const css = readFileSync(source, 'utf8');
const root = css.match(/:root\s*\{[^}]+\}/)?.[0];
const product = css.match(/\[data-product="p2"\]\s*\{([^}]+)\}/)?.[1];
if (!root || !product) throw new Error('Canonical root/P2 tokens missing; do not invent a palette');
export const generatedTokens = `/* Generated from web/src/styles/tokens.css; run deploy/keycloak/scripts/sync-theme-tokens.mjs. */\n${root}\n:root {${product}}\n`;
if (process.argv.includes('--check')) {
  if (readFileSync(target, 'utf8').replaceAll('\r\n', '\n') !== generatedTokens.replaceAll('\r\n', '\n')) {
    throw new Error('Theme tokens drifted; regenerate from canonical WEB tokens');
  }
} else if (fileURLToPath(import.meta.url) === process.argv[1]) {
  writeFileSync(target, generatedTokens);
}
