/** @vitest-environment happy-dom */
import { beforeEach, describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createMemoryHistory } from 'vue-router';
const api = vi.hoisted(() => ({ installations: vi.fn(), app: vi.fn() }));
vi.mock('../../api/client', () => ({ p4: api }));
import View from './AvailableApplicationsView.vue';
async function page() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/p3/applications', component: View }, { path: '/p3/applications/:id/start', component: { template: '<div />' } }] });
  await router.push('/p3/applications');
  const w = mount(View, { global: { plugins: [router] } }); await flushPromises(); return w;
}
beforeEach(() => { vi.resetAllMocks(); api.installations.mockResolvedValue([]); });
describe('member runtime applications', () => {
  it('links installed products to P3, never market administration', async () => {
    api.installations.mockResolvedValue([{ id: 'i', app_id: 'a', status: 'INSTALLED' }, { id: 'old', status: 'UNINSTALLED' }]);
    api.app.mockResolvedValue({ name: '费用' }); const w = await page();
    expect(w.text()).toContain('费用'); expect(w.get('a').attributes('href')).toBe('/p3/applications/i/start');
    expect(api.app).toHaveBeenCalledTimes(1); expect(w.html()).not.toContain('/p4/');
  });
  it('does not fabricate products on an empty tenant', async () => { expect((await page()).text()).toContain('暂无可用应用'); });
  it('keeps failure distinct from empty', async () => {
    api.installations.mockRejectedValue(new Error('tenant context is revoked'));
    const w = await page(); expect(w.text()).toContain('tenant context is revoked'); expect(w.text()).not.toContain('暂无可用应用');
  });
});
