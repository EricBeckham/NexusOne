import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { generatedTokens } from './sync-theme-tokens.mjs';

const login = new URL('../themes/nexusone/login/', import.meta.url);
const read = (path) => readFileSync(new URL(path, login), 'utf8').replaceAll('\r\n', '\n');
test('inherit auth forms and scripts; only visual resources are customized', () => {
  assert.match(read('theme.properties'), /^parent=keycloak$/m);
  assert.match(read('theme.properties'), /styles=css\/login.css css\/nexus-tokens.css css\/nexus-login.css/);
  assert.ok(!readdirSync(login).some((name) => name.endsWith('.ftl')));
  assert.ok(!read('theme.properties').includes('scripts='));
});
test('theme palette, spacing, font and radius match canonical LOGIN/P2 tokens', () => {
  assert.equal(read('resources/css/nexus-tokens.css'), generatedTokens.replaceAll('\r\n', '\n'));
  const css = read('resources/css/nexus-login.css');
  assert.ok(!/#(?:[0-9a-f]{3,8})\b|\b(?:rgb|hsl)a?\(/i.test(css));
  assert.ok(!/!important|url\(/i.test(css));
  assert.match(css, /background: var\(--nx-product\)/);
  assert.match(css, /\[aria-invalid="true"\]/);
  assert.match(css, /:disabled/);
  assert.match(css, /focus-visible/);
  assert.match(css, /var\(--nx-auth-panel-width\)/);
});
test('accessible text branding is bilingual without changing server errors or credential labels', () => {
  for (const locale of ['en', 'zh_CN']) {
    const messages = read(`messages/messages_${locale}.properties`);
    assert.match(messages, /loginTitleHtml=星枢 NexusOne/);
    assert.deepEqual(messages.trim().split('\n').map((line) => line.split('=')[0]), ['loginTitleHtml', 'loginAccountTitle']);
  }
});
