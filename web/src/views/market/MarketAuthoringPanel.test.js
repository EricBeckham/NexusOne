/** @vitest-environment happy-dom */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
const api = vi.hoisted(() => ({ createApp: vi.fn(), createVersion: vi.fn(), transition: vi.fn(), createSku: vi.fn() }));
vi.mock('../../api/client', () => ({ p4: api }));
import Panel from './MarketAuthoringPanel.vue';
const mountPanel = (extra = {}) => mount(Panel, { props: { section: 'packages', apps: [], versions: [], ...extra }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } });
beforeEach(() => { vi.resetAllMocks(); });
describe('platform authoring', () => {
  it('creates the entered app and emits refresh only after success', async () => {
    const w = mountPanel(); const inputs = w.findAll('input');
    await inputs[0].setValue('expense'); await inputs[1].setValue('费用');
    await w.get('form').trigger('submit'); await flushPromises();
    expect(api.createApp).toHaveBeenCalledWith({ code: 'expense', name: '费用', summary: '' });
    expect(w.emitted('saved')).toHaveLength(1);
  });
  it('keeps input and error without reporting saved on rejection', async () => {
    api.createApp.mockRejectedValue(new Error('权限不足')); const w = mountPanel();
    await w.findAll('input')[0].setValue('expense'); await w.get('form').trigger('submit'); await flushPromises();
    expect(w.get('[role=alert]').text()).toBe('权限不足'); expect(w.emitted('saved')).toBeUndefined();
    expect(w.findAll('input')[0].element.value).toBe('expense');
  });
  it.each([['DRAFT', 'review'], ['IN_REVIEW', 'approve'], ['APPROVED', 'publish']])('uses the %s transition', async (status, action) => {
    const w = mountPanel({ selectedApp: { id: 'app', name: '费用' }, versions: [{ id: 'v', version: '1', status }] });
    await w.get('button[type=button]').trigger('click'); await flushPromises();
    expect(api.transition).toHaveBeenCalledWith('v', action);
  });
  it('creates SKU with explicit entitlement and does not claim runtime readiness', async () => {
    const w = mountPanel({ section: 'skus', apps: [{ id: 'app', name: '费用' }] });
    await w.get('select').setValue('app'); const inputs = w.findAll('input');
    await inputs[0].setValue('expense-basic'); await inputs[1].setValue('基础版');
    await w.get('form').trigger('submit'); await flushPromises();
    expect(api.createSku).toHaveBeenCalledWith({ app_id: 'app', code: 'expense-basic', name: '基础版', entitlements: { apps: { enabled: true, quota: 1, attributes: {} } } });
    expect(w.text()).toContain('还需绑定');
  });
});
