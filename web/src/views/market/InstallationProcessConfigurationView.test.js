/** @vitest-environment happy-dom */
import { vi, describe, it, beforeEach, expect } from 'vitest';
import { mount,flushPromises } from '@vue/test-utils';
import { createRouter,createMemoryHistory } from 'vue-router';
const api=vi.hoisted(()=>({processConfiguration:vi.fn(),configureProcess:vi.fn(),resetProcess:vi.fn(),listProcessDefinitions:vi.fn()}));
vi.mock('../../api/client',()=>({p4:api})); vi.mock('../../api/p3',()=>({p3:api}));
import View from './InstallationProcessConfigurationView.vue';
async function page(){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/p4/installations/:id/process-configuration',component:View},{path:'/p4/installations',component:{template:'<div />'}},{path:'/p3/workflow',component:{template:'<div />'}}]}); await router.push('/p4/installations/install/process-configuration');const w=mount(View,{global:{plugins:[router]}});await flushPromises();return w;}
beforeEach(()=>{vi.resetAllMocks(); api.processConfiguration.mockResolvedValue({default_key:'platform-default',default_version:2});api.listProcessDefinitions.mockResolvedValue([{key:'custom',name:'个性化费用',source:'tenant-deployment',version:3},{key:'classpath',source:'classpath'}]);});
describe('tenant process customization',()=>{
 it('shows platform default but only offers tenant published processes',async()=>{const w=await page();expect(w.text()).toContain('platform-default');expect(w.findAll('option')).toHaveLength(2);await w.get('select').setValue('custom');api.configureProcess.mockResolvedValue({default_key:'platform-default',default_version:2,override_key:'custom',override_version:3});await w.get('form').trigger('submit');await flushPromises();expect(api.configureProcess).toHaveBeenCalledWith('install',{key:'custom',version:3});expect(w.text()).toContain('仅影响之后发起');});
 it('does not expose an editable form after authorization/loading failure',async()=>{api.processConfiguration.mockRejectedValue(new Error('denied'));const w=await page();expect(w.get('[role=alert]').text()).toBe('denied');expect(w.find('form').exists()).toBe(false);});
 it('restores default without modifying a process',async()=>{api.processConfiguration.mockResolvedValue({default_key:'platform-default',override_key:'custom'});api.resetProcess.mockResolvedValue({default_key:'platform-default'});const w=await page();await w.get('button[type=button]').trigger('click');await flushPromises();expect(api.resetProcess).toHaveBeenCalledWith('install');expect(w.text()).toContain('平台默认流程');});
});
