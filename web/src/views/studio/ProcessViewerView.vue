<template>
  <div>
    <NxPageHeader
      eyebrow="星构"
      title="流程设计器"
      description="编辑租户内流程草稿；已发布目录仅供查看，改动不会部署或发布。"
    >
      <template #actions>
        <button type="button" class="ghost" data-testid="process-new" @click="newDraft">新建草稿</button>
        <button v-if="!isEditing" type="button" class="ghost" data-testid="process-edit-copy" :disabled="!definition" :title="!definition ? '请先在左侧选择一个流程' : undefined" @click="editAsDraft">创建可编辑草稿</button>
        <button
          v-if="isPersistedDraft"
          type="button"
          class="ghost"
          data-testid="process-validate"
          :disabled="dirty || saving || validating"
          :title="validateDisabledReason || undefined"
          @click="validateDraft"
        >{{ validating ? "校验中…" : "校验草稿" }}</button>
        <button
          v-if="canPublish"
          type="button"
          class="primary"
          data-testid="process-publish"
          :disabled="publishing"
          title="校验已通过，可以发布"
          @click="publishConfirm = true"
        >发布流程</button>
        <button v-if="isEditing" type="button" class="primary" data-testid="process-save" :disabled="saving" @click="saveDraft">
          {{ saving ? "保存中…" : "保存草稿" }}
        </button>
        <router-link class="ghost" to="/p1/form-templates" data-testid="process-open-templates">表单模板</router-link>
        <router-link class="ghost" to="/p4/product-definitions" data-testid="process-open-definitions">产品定义</router-link>
      </template>
    </NxPageHeader>
    <p class="nx-muted later-note">审批节点按从上到下的顺序依次流转，可增删、拖拽排序；在节点属性中可添加条件分支。发布前须保存并通过校验。</p>
    <NxErrorState
      v-if="error && signInRequired"
      title="需要登录租户账号"
      description="流程与草稿按租户读取：当前没有登录的租户会话。请先以租户账号登录（演示：林晓），再回到流程设计器。"
      :trace-id="error.traceId || ''"
      data-testid="process-signin-required"
    >
      <template #action>
        <router-link class="primary" to="/login" data-testid="process-signin-link">去登录</router-link>
      </template>
    </NxErrorState>
    <NxErrorState
      v-else-if="error"
      title="流程定义没有加载"
      :description="error.message || '请稍后重试'"
      :trace-id="error.traceId || ''"
      retry-label="重试"
      @retry="reload"
    />
    <div v-else-if="loading" class="process-loading" data-testid="process-loading">
      <NxSkeleton v-for="index in 3" :key="index" variant="list-row" label="正在加载流程与步骤" compact />
    </div>
    <NxEmpty
      v-else-if="!loading && !definition && !definitions.length"
      title="暂无流程"
      description="这个租户还没有流程或草稿。新建一个草稿，从一个顺序审批节点开始配置。"
      data-testid="process-empty"
    >
      <template #action>
        <button type="button" class="primary" data-testid="process-empty-new" @click="newDraft">新建草稿</button>
      </template>
    </NxEmpty>
    <div v-else class="shell">
      <aside class="nx-card list" data-testid="process-list">
        <h2>流程与草稿 <small>{{ definitions.length }} 个</small></h2>
        <button
          v-for="item in definitions"
          :key="item.key"
          type="button"
          class="item"
          :class="{ current: item.key === definition?.key }"
          @click="openDefinition(item.key)"
        >
          <strong>{{ showText(item.name) }}</strong>
          <span class="item-meta"><span class="item-key">{{ showText(item.key) }}</span><span class="item-state" :data-source="item.source">{{ item.source === "draft" ? "草稿" : "已发布只读" }}</span></span>
        </button>
      </aside>
      <section class="nx-card canvas" data-testid="process-canvas">
        <header class="canvas-head">
          <div>
            <h2>{{ showText(definition?.name) }}</h2>
            <p class="nx-muted">{{ isEditing ? "可编辑草稿" : "已发布只读" }}<template v-if="devNotes && definition?.resource"> · {{ definition.resource }}</template></p>
          </div>
          <span v-if="definition && sequence.sequential" class="mode-chip" data-testid="flow-mode">顺序审批 · {{ sequence.steps.length }} 个审批节点</span>
        </header>
        <p v-if="draftState" class="draft-state" :data-tone="draftState.tone" data-testid="draft-state">{{ draftState.text }}</p>
        <NxEmpty
          v-if="definition && !graph.nodes.length"
          compact
          title="暂无节点"
          :description="isEditing ? '这份草稿没有可解析的节点。新建草稿会从「开始 → 审批 → 结束」起步。' : '这个流程没有可展示的节点。可以新建草稿重新配置。'"
          data-testid="process-nodes-empty"
        >
          <template #action>
            <button type="button" class="ghost" data-testid="process-nodes-empty-new" @click="newDraft">新建草稿</button>
          </template>
        </NxEmpty>
        <ol
          v-else-if="definition && sequence.sequential"
          class="flow"
          :class="{ editing: isEditing }"
          data-testid="flow-steps"
          aria-label="顺序审批步骤"
          @dragover.prevent
          @dragleave="onFlowDragLeave"
          @drop.prevent="onStepDrop"
        >
          <li
            v-if="sequence.start"
            class="terminal start"
            :class="{ current: selectedId === sequence.start.id }"
            data-testid="process-node"
            role="button"
            tabindex="0"
            @click="selectedId = sequence.start.id"
            @keydown.enter.prevent="selectedId = sequence.start.id"
          >
            <span class="terminal-icon"><NxIcon name="play" :size="14" /></span>
            <span><strong>{{ showText(sequence.start.name || "开始") }}</strong><small>发起人提交申请</small></span>
          </li>
          <template v-for="(step, index) in sequence.steps" :key="step.id">
            <li class="connector" aria-hidden="false">
              <span class="line" />
              <button v-if="isEditing" type="button" class="insert" data-testid="insert-step" :aria-label="`在第 ${index + 1} 个节点前插入审批节点`" title="插入审批节点" @click="insertStep(index)">
                <NxIcon name="plus" :size="14" />
              </button>
            </li>
            <li v-if="dropIndex === index" class="drop-line" data-testid="step-drop-indicator" aria-hidden="true" />
            <li
              class="step"
              :class="{ current: selectedId === step.id, dragging: draggingId === step.id, invalid: nodeErrors(step.id).length }"
              :draggable="isEditing"
              data-testid="process-node"
              role="button"
              tabindex="0"
              :aria-label="`第 ${index + 1} 个审批节点 ${step.name || ''}`"
              @click="selectedId = step.id"
              @keydown.enter.self.prevent="selectedId = step.id"
              @dragstart="onStepDragStart($event, step)"
              @dragend="endStepDrag"
              @dragover.prevent.stop="onStepDragOver($event, index)"
              @drop.prevent.stop="onStepDrop"
            >
              <span v-if="isEditing" class="grip" aria-hidden="true"><NxIcon name="grip" :size="18" /></span>
              <span class="num">{{ index + 1 }}</span>
              <span class="step-body">
                <small class="step-kind">第 {{ index + 1 }} 步 · 审批</small>
                <strong>{{ step.name || "未命名审批节点" }}</strong>
                <span class="assignee" :class="{ missing: !step.assignee }">
                  <NxIcon name="user" :size="14" />{{ step.assignee ? assigneeLabel(step.assignee) : "未设置审批人" }}
                </span>
                <span v-for="(message, messageIndex) in nodeErrors(step.id)" :key="messageIndex" class="step-error" data-testid="step-error">{{ message }}</span>
              </span>
              <span v-if="isEditing" class="step-actions">
                <button type="button" class="icon-btn" data-testid="step-up" aria-label="上移" :title="index === 0 ? '已经是第一个节点' : '上移'" :disabled="index === 0" @click.stop="moveStep(index, index - 1)"><NxIcon name="arrowUp" :size="16" /></button>
                <button type="button" class="icon-btn" data-testid="step-down" aria-label="下移" :title="index === sequence.steps.length - 1 ? '已经是最后一个节点' : '下移'" :disabled="index === sequence.steps.length - 1" @click.stop="moveStep(index, index + 2)"><NxIcon name="arrowDown" :size="16" /></button>
                <button type="button" class="icon-btn danger-hover" data-testid="step-remove" aria-label="删除审批节点" :title="sequence.steps.length === 1 ? '至少保留一个审批节点' : '删除'" :disabled="sequence.steps.length === 1" @click.stop="removeStep(index)"><NxIcon name="trash" :size="16" /></button>
              </span>
            </li>
          </template>
          <li v-if="!sequence.steps.length" class="steps-empty" data-testid="flow-steps-empty">
            <strong>还没有审批节点</strong>
            <small>{{ isEditing ? "点下方「添加审批节点」，申请会按顺序交给每个节点的审批人。" : "此流程发起后会直接结束，不经过审批。" }}</small>
          </li>
          <li v-if="dropIndex === sequence.steps.length" class="drop-line" data-testid="step-drop-indicator" aria-hidden="true" />
          <li class="connector">
            <span class="line" />
            <button v-if="isEditing" type="button" class="insert" data-testid="insert-step" aria-label="在末尾添加审批节点" title="添加审批节点" @click="insertStep(sequence.steps.length)">
              <NxIcon name="plus" :size="14" />
            </button>
          </li>
          <li
            v-if="sequence.end"
            class="terminal end"
            :class="{ current: selectedId === sequence.end.id }"
            data-testid="process-node"
            role="button"
            tabindex="0"
            @click="selectedId = sequence.end.id"
            @keydown.enter.prevent="selectedId = sequence.end.id"
          >
            <span class="terminal-icon"><NxIcon name="check" :size="14" /></span>
            <span><strong>{{ showText(sequence.end.name || "结束") }}</strong><small>全部通过后结束，结果回写申请</small></span>
          </li>
          <li v-else class="terminal end missing-end"><span class="terminal-icon"><NxIcon name="check" :size="14" /></span><span><strong>结束</strong><small>编辑时自动补齐</small></span></li>
        </ol>
        <button v-if="definition && sequence.sequential && isEditing" type="button" class="ghost add-step" data-testid="add-step" @click="insertStep(sequence.steps.length)">
          <NxIcon name="plus" :size="16" />添加审批节点
        </button>
        <p v-if="definition && graph.nodes.length && !sequence.sequential" class="nonsequential" data-testid="process-nonsequential">
          <template v-if="isEditing">此草稿{{ sequence.reason }}，以流程图编辑：点选节点修改名称或添加、删除分支，点选连线设置条件。</template>
          <template v-else>此流程{{ sequence.reason }}，顺序编辑器不支持，仅可查看。</template>
        </p>
        <svg
          v-if="definition && graph.nodes.length && !sequence.sequential"
          class="graph"
          :viewBox="`0 0 ${graph.width} ${graph.height}`"
          :width="graph.width"
          :height="graph.height"
          role="group"
          aria-label="流程图"
        >
          <defs>
            <marker id="flow-arrow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="8" markerHeight="8" orient="auto">
              <path d="M0 0 L8 4 L0 8 z" class="arrow" />
            </marker>
          </defs>
          <!-- ZA13: read-only parallel scope — a shaded box behind each fork → join block. -->
          <g v-for="scope in scopeBoxes" :key="scope.fork" class="parallel-scope" data-testid="parallel-scope-box" :data-fork="scope.fork">
            <rect :x="scope.x" :y="scope.y" :width="scope.width" :height="scope.height" rx="10" />
            <text :x="scope.x + 8" :y="scope.y + 13">并行</text>
          </g>
          <!-- Z7: flows are selectable (wide invisible hit line); a condition shows as a label at the midpoint. -->
          <g
            v-for="flow in graph.flows"
            :key="flow.id || `${flow.source_ref}-${flow.target_ref}`"
            class="flow"
            :class="{ current: flow.id && flow.id === selectedFlowId, conditional: Boolean(flow.condition), invalid: flowErrors(flow.id).length > 0 || Boolean(checkConditionText(flow.condition)) }"
            data-testid="process-flow"
            :data-flow-id="flow.id"
            @click="selectFlow(flow.id)"
          >
            <path v-if="flow.path" class="hit" :d="flow.path" />
            <path v-if="flow.path" class="edge" :d="flow.path" marker-end="url(#flow-arrow)" />
            <text
              v-if="flow.condition && flow.path"
              class="flow-condition"
              :x="flow.labelX"
              :y="flow.labelY - 6"
              text-anchor="middle"
            ><title>{{ flow.condition }}</title>{{ shortCondition(describeCondition(flow.condition)) }}</text>
          </g>
          <g
            v-for="node in graph.nodes" :data-node-id="node.id"
            :key="node.id"
            class="node"
            :class="{ current: node.id === selectedId, invalid: nodeErrors(node.id).length > 0 }"
            data-testid="process-node"
            @click="selectNode(node.id)"
          >
            <rect :x="node.x" :y="node.y" :width="graph.nodeWidth" :height="graph.nodeHeight" rx="8" />
            <text :x="node.x + 12" :y="node.y + 24">{{ showText(node.name) }}</text>
            <text :x="node.x + 12" :y="node.y + 42" class="type">{{ nodeKind(node.type, node.id) }}</text>
          </g>
        </svg>
      </section>
      <aside class="nx-card props" data-testid="process-properties">
        <h2>{{ selectedFlow ? "连线属性" : "节点属性" }}</h2>
        <label v-if="isEditing" class="draft-name">草稿名称<input v-model="definition.name" data-testid="draft-name" aria-label="草稿名称" /></label>
        <NxPropertyPanel
          v-if="selected && !selectedFlow"
          v-model="propTab"
          :tabs="PROP_TABS"
          compact
          aria-label="节点属性分区"
          data-testid="process-property-sections"
        >
          <template #basic>
            <dl>
              <dt>标识</dt>
              <dd>{{ showText(selected.id) }}</dd>
              <dt>名称</dt>
              <dd v-if="isEditing && editableType(selected.type)"><input v-model="selected.name" data-testid="node-name" aria-label="节点名称" /></dd>
              <dd v-else>{{ showText(selected.name) }}</dd>
              <dt>类型</dt>
              <dd data-testid="node-type">{{ nodeTypeLabel(selected.type, selected.id) }}</dd>
            </dl>
          </template>
          <template #assignee>
            <template v-if="selected.type === 'userTask'">
              <label class="nx-field"><span>审批人引用</span>
                <input v-if="isEditing" v-model="selected.assignee" data-testid="node-assignee" aria-label="审批人引用" placeholder="例如发起时选择的审批人" />
                <span v-else>{{ selected.assignee ? assigneeLabel(selected.assignee) : "暂无" }}</span>
              </label>
              <span v-if="isEditing" class="presets">
                <button v-for="preset in ASSIGNEE_PRESETS" :key="preset.value" type="button" class="preset"
                  :class="{ active: selected.assignee === preset.value }" data-testid="assignee-preset"
                  @click="selected.assignee = preset.value">{{ preset.label }}</button>
              </span>
              <small v-if="isEditing" class="preset-hint">目前发起时只选一位审批人；多个节点都用它时会由同一人依次审批。</small>
            </template>
            <p v-else class="nx-muted" data-testid="assignee-not-applicable">此节点无需指定审批人。</p>
          </template>
          <template #condition>
            <section v-if="selected?.type === 'exclusiveGateway'" class="branch-summary" data-testid="branch-summary">
              <ol>
                <li v-for="(flow, index) in gatewayBranches(selected.id)" :key="flow.id">
                  <button type="button" class="branch-line" data-testid="branch-summary-item" :title="flow.condition || undefined" @click="selectFlow(flow.id)">
                    <span class="branch-index">分支 {{ index + 1 }}</span>
                    <span class="branch-target">→ {{ nodeName(flow.target_ref) }}</span>
                    <span class="branch-condition" :class="{ none: !flow.condition, bad: Boolean(checkConditionText(flow.condition)) }">{{ flow.condition ? describeCondition(flow.condition) : "无条件" }}</span>
                  </button>
                </li>
              </ol>
            </section>
            <p v-else class="nx-muted" data-testid="condition-summary-empty">点选条件分支节点或分支连线，可在此查看条件摘要。</p>
          </template>
        </NxPropertyPanel>
        <!-- Z6: exclusive-gateway add/remove on editable drafts. Conditions are edited on branch flows (Z7). -->
        <div v-if="isEditing && selected && canBranchAfter(selected)" class="branch-actions">
          <button type="button" class="nx-btn nx-btn--default nx-btn--sm" data-testid="add-gateway" @click="addGatewayAfter(selected.id)">
            <NxIcon name="plus" :size="14" />在此后添加条件分支
          </button>
          <small class="nx-muted">原有路径作为分支一，另加一条分支审批，两条分支在后续节点汇合。</small>
          <button type="button" class="nx-btn nx-btn--default nx-btn--sm" data-testid="add-parallel" @click="addParallelAfter(selected.id)">
            <NxIcon name="plus" :size="14" />在此后添加并行分支
          </button>
          <small class="nx-muted">插入「并行开始 → 两条并行审批 → 并行汇合」，两条审批同时进行，都完成后再继续。</small>
        </div>
        <div v-if="isEditing && selected?.type === 'parallelGateway'" class="branch-actions">
          <button type="button" class="nx-btn nx-btn--text danger-text" data-testid="remove-parallel" @click="removeParallel(selected.id)">删除并行分支</button>
          <small class="nx-muted">连同配对的并行开始 / 汇合和其间的并行审批一起删除。</small>
        </div>

        <p v-if="selected?.type === 'exclusiveGateway' && gatewayWithoutConditions(selected.id)" class="gateway-warning" data-testid="gateway-warning">
          所有分支都没有设置条件，运行时会始终走第一条分支。点选分支连线设置条件。
        </p>
        <div v-if="isEditing && selected?.type === 'exclusiveGateway'" class="branch-actions">
          <button type="button" class="nx-btn nx-btn--text danger-text" data-testid="remove-gateway" @click="removeGateway(selected.id)">删除条件分支</button>
          <small class="nx-muted">保留第一条分支，其他分支上独有的审批节点一并删除。</small>
        </div>
        <p v-if="branchError" class="step-error" role="alert" data-testid="branch-error">{{ branchError }}</p>
        <section v-if="selectedFlow" class="flow-props" data-testid="flow-properties">
          <dl>
            <dt>从</dt><dd>{{ nodeName(selectedFlow.source_ref) }}</dd>
            <dt>到</dt><dd>{{ nodeName(selectedFlow.target_ref) }}</dd>
            <dt class="condition-label">条件</dt>
            <dd v-if="isEditing && conditionEditable(selectedFlow)" class="condition-editor wide" data-testid="condition-editor">
              <!-- ZA7: thin structured editor (variable · operator · value) ↔ the condition string; 原文 keeps any expression. -->
              <div class="mode-switch" role="group" aria-label="条件编辑方式">
                <button type="button" :class="{ active: conditionMode === 'builder' }" data-testid="condition-mode-builder" @click="switchConditionMode('builder')">条件</button>
                <button type="button" :class="{ active: conditionMode === 'raw' }" data-testid="condition-mode-raw" @click="switchConditionMode('raw')">原文</button>
              </div>
              <template v-if="conditionMode === 'builder'">
                <select v-model="conditionDraft.variable" data-testid="condition-variable" aria-label="变量" :aria-invalid="draftProblems.variable ? 'true' : undefined" @change="onConditionVariableChange">
                  <option value="">选择变量</option>
                  <option v-for="variable in CONDITION_VARIABLES" :key="variable.name" :value="variable.name">{{ variable.label }}</option>
                  <option value="__custom">其他变量…</option>
                </select>
                <template v-if="conditionDraft.variable === '__custom'">
                  <input v-model="conditionDraft.custom" class="nx-code" data-testid="condition-custom" aria-label="变量名" placeholder="变量名，如 amount" :aria-invalid="draftProblems.variable ? 'true' : undefined" @input="applyConditionDraft" />
                  <small class="preset-hint">需确保运行时存在该变量；申请表单的字段目前不会传入流程。</small>
                </template>
                <small v-if="draftProblems.variable" class="nx-field-error" data-testid="condition-error-variable">{{ draftProblems.variable }}</small>
                <select v-model="conditionDraft.operator" data-testid="condition-operator" aria-label="运算符" @change="applyConditionDraft">
                  <option v-for="op in operatorsFor(draftVariableName)" :key="op.value" :value="op.value">{{ op.label }}</option>
                </select>
                <small v-if="draftProblems.operator" class="nx-field-error" data-testid="condition-error-operator">{{ draftProblems.operator }}</small>
                <select v-if="draftEnumOptions" v-model="conditionDraft.value" data-testid="condition-value" aria-label="值" :aria-invalid="draftProblems.value ? 'true' : undefined" @change="applyConditionDraft">
                  <option value="">选择值</option>
                  <option v-for="option in draftEnumOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
                <input v-else v-model="conditionDraft.value" data-testid="condition-value" aria-label="值" placeholder="值，如 1000 或 文本" :aria-invalid="draftProblems.value ? 'true' : undefined" @input="applyConditionDraft" />
                <small v-if="draftProblems.value" class="nx-field-error" data-testid="condition-error-value">{{ draftProblems.value }}</small>
                <p v-if="draftIncomplete" class="condition-incomplete" data-testid="condition-incomplete">
                  条件还没填完整，暂不生效；保存时仍使用：{{ selectedFlow.condition || "无条件" }}
                </p>
                <div class="condition-foot">
                  <small class="preset-hint">生成：<code data-testid="condition-generated">{{ selectedFlow.condition || "无条件" }}</code></small>
                  <button type="button" class="nx-btn nx-btn--text" data-testid="condition-clear" @click="clearCondition">清空条件</button>
                </div>
              </template>
              <template v-else>
                <input v-model="selectedFlow.condition" class="nx-code" data-testid="flow-condition" aria-label="分支条件原文" placeholder="例如 ${outcome == 'APPROVE'}" :aria-invalid="rawProblem ? 'true' : undefined" />
                <small v-if="rawProblem" class="nx-field-error" data-testid="condition-error-raw">{{ rawProblem }}</small>
                <small class="preset-hint">原文按表达式原样保存；留空表示无条件。</small>
                <small v-if="conditionModeNote" class="preset-hint" data-testid="condition-mode-note">{{ conditionModeNote }}</small>
              </template>
            </dd>
            <dd v-else data-testid="flow-condition-text">
              <code v-if="selectedFlow.condition">{{ selectedFlow.condition }}</code><span v-else>无条件</span>
              <small v-if="isEditing" class="preset-hint">{{ parallelFlow(selectedFlow) ? "并行分支的连线不设条件，各分支同时进行。" : "只有从条件分支出发的连线可以设置条件。" }}</small>
            </dd>
          </dl>
          <p v-if="flowErrors(selectedFlow.id).length" class="step-error" data-testid="flow-error">{{ flowErrors(selectedFlow.id).join("；") }}</p>
        </section>
        <section v-if="selectedBlock" class="parallel-scope-panel" data-testid="parallel-scope">
          <h3>并行范围</h3>
          <ol>
            <li><span class="scope-role">开始</span>{{ nodeName(selectedBlock.fork) }}</li>
            <li v-for="(branch, index) in selectedBlock.branches" :key="index" data-testid="parallel-scope-branch">
              <span class="scope-role">分支 {{ index + 1 }}</span>{{ branch.length ? branch.map(nodeName).join(" → ") : "直接汇合" }}
            </li>
            <li><span class="scope-role">汇合</span>{{ nodeName(selectedBlock.join) }}</li>
          </ol>
          <p class="nx-muted">{{ selectedBlock.branches.length }} 条分支同时进行，全部完成后在「{{ nodeName(selectedBlock.join) }}」继续。</p>
        </section>
        <p v-if="!selected && !selectedFlow" class="nx-muted" data-testid="process-properties-empty">点选画布中的节点，查看或修改它的属性。</p>
        <p v-if="isEditing && selected && nodeErrors(selected.id).length" class="step-error" data-testid="node-error">{{ nodeErrors(selected.id).join("；") }}</p>
        <section v-if="conditionProblems.length" class="validation-result" data-testid="condition-problems" data-valid="false">
          <strong>条件需要修改 · {{ conditionProblems.length }} 处</strong>
          <p class="nx-muted">这些条件的格式不对，修改后才能保存。点选可定位到对应连线。</p>
          <ul>
            <li v-for="item in conditionProblems" :key="item.flow">
              <button type="button" class="validation-item" data-testid="condition-problem" @click="selectFlow(item.flow)">
                <span class="validation-where">{{ item.target }}</span>{{ item.message }}
              </button>
            </li>
          </ul>
        </section>
        <section v-if="validationResult" class="validation-result" data-testid="process-validation" :data-valid="validationResult.valid">
          <strong>{{ validationResult.valid ? "校验通过" : `校验未通过 · ${validationItems.length} 处需要修改` }}</strong>
          <p v-if="validationResult.valid" class="nx-muted">当前草稿满足基础结构要求。</p>
          <template v-else>
            <p class="nx-muted">点选下面的问题可定位到对应节点或连线，修改并保存后重新校验。</p>
            <ul>
              <li v-for="(item, index) in validationItems" :key="`${item.code}-${index}`">
                <button v-if="item.target" type="button" class="validation-item" data-testid="validation-item" @click="item.flow ? selectFlow(item.flow) : selectNode(item.node)">
                  <span class="validation-where">{{ item.target }}</span>{{ item.message }}
                </button>
                <span v-else data-testid="validation-item">{{ item.message }}</span>
                <code v-if="devNotes" class="validation-code">{{ item.code }}</code>
              </li>
            </ul>
          </template>
        </section>
        <p v-if="validationError" class="validation-error" role="alert" data-testid="process-validation-error">
          校验请求失败：{{ validationError.message || "请稍后重试" }}
        </p>
        <section v-if="publication" class="publication-result" data-testid="process-publication">
          <strong>已发布</strong>
          <span v-if="publication.version">最近发布版本 v{{ publication.version }}</span>
          <span v-else>服务端已确认发布</span>
          <router-link to="/p4/product-definitions" data-testid="process-next-bind">下一步：到产品定义绑定</router-link>
        </section>
        <p v-if="publishError" class="publish-error" role="alert" data-testid="process-publish-error">
          发布失败：{{ publishError.message || "服务端未能发布此流程" }}
        </p>
        <template v-if="!sequence.sequential">
        <h3>相关连线</h3>
        <ul v-if="selectedFlows.length">
          <li v-for="flow in selectedFlows" :key="flow.id || `${flow.source_ref}-${flow.target_ref}`">
            <button type="button" class="related-flow" data-testid="related-flow" @click="selectFlow(flow.id)">
              {{ nodeName(flow.source_ref) }} → {{ nodeName(flow.target_ref) }}
              <span :title="flow.condition || undefined">{{ flow.condition ? describeCondition(flow.condition) : "无条件" }}</span>
            </button>
          </li>
        </ul>
        <p v-else class="nx-muted">暂无</p>
        </template>
      </aside>
    </div>
    <div v-if="publishConfirm" class="confirm-backdrop" data-testid="process-publish-confirm-backdrop">
      <section class="confirm-dialog nx-card" role="dialog" aria-modal="true" aria-labelledby="process-publish-confirm-title" data-testid="process-publish-confirm">
        <h2 id="process-publish-confirm-title">确认发布流程？</h2>
        <p>将当前已保存且校验通过的草稿发布为租户可用版本。正在运行的流程实例不会迁移。</p>
        <ul class="confirm-facts" data-testid="process-publish-facts">
          <li data-testid="process-publish-version-hint">当前已发布版本：{{ publication?.version != null ? `v${publication.version}` : "尚无（本次将发布为首个版本）" }}</li>
          <li data-testid="process-publish-ref-hint">产品引用数暂不可用：还没有「哪些产品绑定了此流程」的只读接口，发布前请自行在产品定义中核对。</li>
        </ul>
        <div class="confirm-actions">
          <button type="button" class="ghost" data-testid="process-publish-cancel" :disabled="publishing" @click="publishConfirm = false">取消</button>
          <button type="button" class="primary" data-testid="process-publish-confirm-submit" :disabled="publishing" @click="publishDraft">
            {{ publishing ? "发布中…" : "确认发布" }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NxEmpty, NxErrorState, NxPageHeader, NxPropertyPanel, NxSkeleton } from "../../components";
import NxIcon from "../../icons/NxIcon.vue";
import { p3 } from "../../api/p3";
import { insertAt, moveToGap } from "../../utils/listMove";
import {
  addDraftNode, createBlankDraft, parallelRole, parseDraftXml, removeExclusiveGateway, removeParallelBlock, serializeDraftXml,
} from "./processDraft";
import { layoutDefinition, showText } from "./processLayout";
import { blockOf, parallelBlocks } from "./parallelScope";
import { nxMessage } from "../../feedback/nxMessage";
import {
  CONDITION_OPERATORS, CONDITION_VARIABLES, catalogVariable, checkConditionDraft, checkConditionText, describeCondition, formatCondition, operatorsFor, parseCondition,
} from "./conditionDsl";
import { fromSequence, newStep, toSequence } from "./sequentialFlow";
import { devNotesEnabled } from "../../utils/devNotes";

// Only `assignee` is set as a process variable at start (SequentialApprovalService); offer nothing else.
const ASSIGNEE_PRESETS = [
  { value: "${assignee}", label: "发起时选择的审批人" },
];

// The classpath resource path is a developer note (O9).
const devNotes = devNotesEnabled();
const route = useRoute();
const router = useRouter();
const definitions = ref([]);
const definition = ref(null);
const isDraft = ref(false);
const isEditing = ref(false);
const isPersistedDraft = ref(false);
const dirty = ref(false);
const saving = ref(false);
const validating = ref(false);
const publishing = ref(false);
const publishConfirm = ref(false);
const publication = ref(null);
const publishError = ref(null);
const validationResult = ref(null);
const validationError = ref(null);
const selectedId = ref("");
// Z7: a selected sequence flow (edge) — mutually exclusive with a selected node.
const selectedFlowId = ref("");
const PROP_TABS = [
  { id: "basic", label: "节点基本" },
  { id: "assignee", label: "审批人" },
  { id: "condition", label: "条件摘要" },
];
const propTab = ref("basic");
const error = ref(null);
const loading = ref(false);
// No tenant session: process APIs answer 401 P2.IAM.UNAUTHORIZED "missing bearer token" (M2); retrying cannot help.
const signInRequired = computed(() => Number(error.value?.status) === 401 || error.value?.code === "P2.IAM.UNAUTHORIZED");
const canPublish = computed(() => isPersistedDraft.value && !dirty.value && validationResult.value?.valid === true && !publishing.value);

const graph = computed(() => layoutDefinition(definition.value || {}));
// ZA13: parallel blocks (same supported shape as the ZA11 validator) for read-only display.
const blocks = computed(() => parallelBlocks(definition.value));
const scopeBoxes = computed(() => blocks.value.map((block) => {
  const members = graph.value.nodes.filter((node) => block.members.has(node.id));
  const left = Math.min(...members.map((node) => node.x));
  const top = Math.min(...members.map((node) => node.y));
  const right = Math.max(...members.map((node) => node.x + graph.value.nodeWidth));
  const bottom = Math.max(...members.map((node) => node.y + graph.value.nodeHeight));
  return { fork: block.fork, x: left - 10, y: top - 18, width: right - left + 20, height: bottom - top + 28 };
}));
const sequence = computed(() => toSequence(definition.value || {}));
const draggingId = ref("");
const dropIndex = ref(null);
const selected = computed(() => definition.value?.nodes?.find((node) => node.id === selectedId.value) || null);
const selectedBlock = computed(() => (selected.value ? blockOf(blocks.value, selected.value.id) : null));
const selectedFlow = computed(() => (selectedFlowId.value
  ? definition.value?.flows?.find((flow) => flow.id === selectedFlowId.value) || null
  : null));


function selectNode(id) {
  selectedFlowId.value = "";
  selectedId.value = id;
  queueMicrotask(() => {
    const safe = CSS.escape(String(id));
    const el = document.querySelector(`[data-node-id="${safe}"]`)
      || document.querySelector(`.step[data-step-id="${safe}"]`);
    el?.scrollIntoView?.({ block: "nearest", behavior: "smooth" });
  });
}

function selectFlow(id) {
  if (!id) return;
  selectedId.value = "";
  selectedFlowId.value = id;
}

function nodeName(id) {
  const node = definition.value?.nodes?.find((item) => item.id === id);
  return node?.name || id || "暂无";
}

// Conditions only mean something on flows leaving a gateway; empty input stays "no condition".
function conditionEditable(flow) {
  const source = definition.value?.nodes?.find((node) => node.id === flow?.source_ref);
  return source?.type === "exclusiveGateway";
}

// ZA7: local builder state for the selected flow; it writes the canonical string only when complete.
const conditionMode = ref("builder");
const conditionModeNote = ref("");
const conditionDraft = reactive({ variable: "", custom: "", operator: "==", value: "" });
const draftVariableName = computed(() => (conditionDraft.variable === "__custom" ? conditionDraft.custom.trim() : conditionDraft.variable));
const draftEnumOptions = computed(() => catalogVariable(draftVariableName.value)?.options || null);

function loadConditionDraft() {
  conditionModeNote.value = "";
  const parsed = parseCondition(selectedFlow.value?.condition);
  Object.assign(conditionDraft, { variable: "", custom: "", operator: "==", value: "" });
  if (parsed.kind === "advanced") {
    conditionMode.value = "raw";
    return;
  }
  conditionMode.value = "builder";
  if (parsed.kind === "simple") {
    const catalog = catalogVariable(parsed.variable);
    Object.assign(conditionDraft, {
      variable: catalog ? parsed.variable : "__custom",
      custom: catalog ? "" : parsed.variable,
      operator: parsed.operator,
      value: parsed.value,
    });
  }
}

function draftValueType() {
  const value = String(conditionDraft.value).trim();
  if (draftEnumOptions.value) return "string";
  if (CONDITION_OPERATORS.find((op) => op.value === conditionDraft.operator)?.numeric || /^-?\d+(\.\d+)?$/.test(value)) return "number";
  if (value === "true" || value === "false") return "boolean";
  return "string";
}

const draftProblems = computed(() => {
  if (conditionDraft.variable === "__custom" && !conditionDraft.custom.trim() && String(conditionDraft.value).trim()) {
    return { ...checkConditionDraft({ variable: "x", operator: conditionDraft.operator, value: conditionDraft.value }), variable: "请填写变量名" };
  }
  return checkConditionDraft({ variable: draftVariableName.value, operator: conditionDraft.operator, value: conditionDraft.value });
});
const draftIncomplete = computed(() => Object.keys(draftProblems.value).length > 0);
const rawProblem = computed(() => checkConditionText(selectedFlow.value?.condition));
const conditionProblems = computed(() => (definition.value?.flows || [])
  .map((flow) => ({ flow: flow.id, message: checkConditionText(flow.condition), target: `连线 ${nodeName(flow.source_ref)} → ${nodeName(flow.target_ref)}：` }))
  .filter((item) => item.flow && item.message));

function applyConditionDraft() {
  if (!selectedFlow.value) return;
  const variable = draftVariableName.value;
  const value = String(conditionDraft.value ?? "").trim();
  if (!variable && !value) {
    selectedFlow.value.condition = "";
    return;
  }
  // ZA8: an incomplete or mistyped draft never replaces the last complete condition.
  if (Object.keys(draftProblems.value).length) return;
  try {
    selectedFlow.value.condition = formatCondition({ variable, operator: conditionDraft.operator, value, valueType: draftValueType() });
  } catch {
    // Incomplete: keep the last complete condition (ZA8 explains what is missing).
  }
}

function onConditionVariableChange() {
  if (!operatorsFor(draftVariableName.value).some((op) => op.value === conditionDraft.operator)) conditionDraft.operator = "==";
  if (draftEnumOptions.value && !draftEnumOptions.value.some((option) => option.value === conditionDraft.value)) conditionDraft.value = "";
  applyConditionDraft();
}

function clearCondition() {
  Object.assign(conditionDraft, { variable: "", custom: "", operator: "==", value: "" });
  if (selectedFlow.value) selectedFlow.value.condition = "";
}

function switchConditionMode(mode) {
  conditionModeNote.value = "";
  if (mode === "raw") {
    conditionMode.value = "raw";
    return;
  }
  if (parseCondition(selectedFlow.value?.condition).kind === "advanced") {
    conditionModeNote.value = "这是条件编辑器表示不了的高级表达式，请在原文里修改，或清空后改用条件编辑。";
    return;
  }
  loadConditionDraft();
}

watch(selectedFlowId, loadConditionDraft);

function gatewayBranches(id) {
  return (definition.value?.flows || []).filter((flow) => flow.source_ref === id);
}

function shortCondition(value) {
  const text = String(value || "");
  return text.length > 18 ? `${text.slice(0, 17)}…` : text;
}
const selectedFlows = computed(() => {
  const id = selectedId.value;
  return (definition.value?.flows || []).filter((flow) => flow.source_ref === id || flow.target_ref === id);
});

async function reload() {
  selectedFlowId.value = "";
  loading.value = true;
  error.value = null;
  try {
    const [listed, savedDrafts] = await Promise.all([p3.listProcessDefinitions(), p3.listProcessDrafts()]);
    const catalog = Array.isArray(listed) ? listed : [];
    const draftList = Array.isArray(savedDrafts) ? savedDrafts : [];
    definitions.value = [
      ...draftList.map((draft) => ({ key: draft.key, name: draft.name, source: "draft" })),
      ...catalog.filter((item) => !draftList.some((draft) => draft.key === item.key)),
    ];
    const key = route.params.key ? String(route.params.key) : "";
    if (!key) {
      definition.value = null;
      isPersistedDraft.value = false;
      dirty.value = false;
      validationResult.value = null;
      publication.value = null;
      publishError.value = null;
      publishConfirm.value = false;
      selectedId.value = "";
      if (definitions.value[0]?.key) {
        await router.replace(`/p3/workflow/${encodeURIComponent(definitions.value[0].key)}`);
      }
      return;
    }
    const saved = draftList.find((draft) => draft.key === key);
    if (saved) {
      const draft = await p3.getProcessDraft(key);
      definition.value = { ...parseDraftXml(draft.bpmn_xml || draft.bpmnXml), key, name: draft.name, source: "draft" };
      try {
        publication.value = await p3.getProcessPublication(key);
      } catch (publicationLookupError) {
        if (publicationLookupError.status !== 404) throw publicationLookupError;
        publication.value = null;
      }
      isDraft.value = true;
      isEditing.value = true;
      isPersistedDraft.value = true;
    } else {
      definition.value = await p3.getProcessDefinition(key);
      isDraft.value = false;
      isEditing.value = false;
      isPersistedDraft.value = false;
    }
    dirty.value = false;
    validationResult.value = null;
    validationError.value = null;
    publishError.value = null;
    publishConfirm.value = false;
    const nodes = definition.value?.nodes || [];
    selectedId.value = nodes.some((node) => node.id === selectedId.value) ? selectedId.value : (nodes[0]?.id || "");
  } catch (err) {
    error.value = err;
    definition.value = null;
    isPersistedDraft.value = false;
    dirty.value = false;
    publication.value = null;
    publishError.value = null;
    publishConfirm.value = false;
  } finally {
    loading.value = false;
  }
}

function openDefinition(key) {
  if (!key || key === route.params.key) {
    return;
  }
  router.push(`/p3/workflow/${encodeURIComponent(key)}`);
}

function newDraft() {
  selectedFlowId.value = "";
  const key = `draft-${Date.now()}`;
  definition.value = createBlankDraft(key);
  isDraft.value = true;
  isEditing.value = true;
  isPersistedDraft.value = false;
  dirty.value = true;
  validationResult.value = null;
  publication.value = null;
  publishError.value = null;
  selectedId.value = "approveTask";
}

function editAsDraft() {
  if (!definition.value) return;
  isDraft.value = true;
  isEditing.value = true;
  isPersistedDraft.value = false;
  if (sequence.value.sequential) definition.value = fromSequence(definition.value, sequence.value.steps);
  dirty.value = true;
  validationResult.value = null;
}

function setSteps(steps) {
  definition.value = fromSequence(definition.value, steps);
}

function insertStep(index) {
  if (!definition.value || !sequence.value.sequential) return;
  const step = newStep(definition.value);
  setSteps(insertAt(sequence.value.steps, index, step));
  selectedId.value = step.id;
}

function removeStep(index) {
  const steps = sequence.value.steps;
  if (steps.length <= 1) return;
  const removed = steps[index];
  setSteps(steps.filter((_, i) => i !== index));
  if (removed?.id === selectedId.value) selectedId.value = sequence.value.steps[Math.min(index, sequence.value.steps.length - 1)]?.id || "";
}

function moveStep(from, gap) {
  const steps = sequence.value.steps;
  const moved = moveToGap(steps, from, gap);
  if (moved !== steps) setSteps(moved);
}

function onStepDragStart(event, step) {
  if (!isEditing.value) return;
  draggingId.value = step.id;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
    try {
      event.dataTransfer.setData("text/plain", step.id);
    } catch {
      // In-memory drag state is authoritative.
    }
  }
}

function onStepDragOver(event, index) {
  if (!draggingId.value) return;
  const rect = event.currentTarget.getBoundingClientRect();
  const before = rect.height > 0 && event.clientY < rect.top + rect.height / 2;
  dropIndex.value = before ? index : index + 1;
  if (event.dataTransfer) event.dataTransfer.dropEffect = "move";
}

function onFlowDragLeave(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) dropIndex.value = null;
}

function onStepDrop() {
  const id = draggingId.value;
  const gap = dropIndex.value;
  endStepDrag();
  if (!id || gap == null) return;
  moveStep(sequence.value.steps.findIndex((step) => step.id === id), gap);
}

function endStepDrag() {
  draggingId.value = "";
  dropIndex.value = null;
}

function flowErrors(id) {
  const errors = validationResult.value?.errors;
  if (!id || !Array.isArray(errors)) return [];
  return errors.filter((item) => item?.flow_id === id || item?.flowId === id).map((item) => item.message).filter(Boolean);
}

// Z10: validation errors read "where: what" and can be clicked to select the node or flow.
const validationItems = computed(() => (validationResult.value?.errors || []).map((item) => {
  const node = item?.node_id ?? item?.nodeId ?? "";
  const flow = item?.flow_id ?? item?.flowId ?? "";
  const flowRef = flow ? definition.value?.flows?.find((candidate) => candidate.id === flow) : null;
  // ZA14: a finding with both locators (e.g. PARALLEL_BRANCH_NOT_JOINED: fork + exit flow) names the flow, the more specific place.
  const target = flowRef
    ? `连线 ${nodeName(flowRef.source_ref)} → ${nodeName(flowRef.target_ref)}：`
    : node ? `${nodeName(node)}：` : "";
  return { code: item?.code || "", message: item?.message || "校验未通过", node, flow: flowRef ? flow : "", target };
}));

function gatewayWithoutConditions(id) {
  const exits = (definition.value?.flows || []).filter((flow) => flow.source_ref === id);
  return exits.length > 1 && exits.every((flow) => !String(flow.condition || "").trim());
}

function describeFailure(err) {
  return [err?.code, err?.message].filter(Boolean).join(" ") || "请稍后重试";
}

function nodeErrors(id) {
  const errors = validationResult.value?.errors;
  if (!Array.isArray(errors)) return [];
  return errors.filter((item) => item?.node_id === id || item?.nodeId === id).map((item) => item.message).filter(Boolean);
}

const NODE_TYPES = { startEvent: "开始", userTask: "审批节点", endEvent: "结束", exclusiveGateway: "条件分支" };
const branchError = ref("");

function canBranchAfter(node) {
  if (!["startEvent", "userTask"].includes(node?.type)) return false;
  return (definition.value?.flows || []).filter((flow) => flow.source_ref === node.id).length === 1;
}

function addGatewayAfter(id) {
  branchError.value = "";
  try {
    const next = addDraftNode(definition.value, "exclusiveGateway", { after: id });
    const added = next.nodes.find((node) => node.type === "exclusiveGateway" && !definition.value.nodes.some((item) => item.id === node.id));
    definition.value = next;
    if (added) selectedId.value = added.id;
  } catch (err) {
    branchError.value = err.message;
  }
}

function removeGateway(id) {
  branchError.value = "";
  try {
    const predecessor = (definition.value.flows || []).find((flow) => flow.target_ref === id)?.source_ref || "";
    definition.value = removeExclusiveGateway(definition.value, id);
    selectedId.value = predecessor;
  } catch (err) {
    branchError.value = err.message;
  }
}

// Z9: a parallel gateway reads as 并行开始 (fork) or 并行汇合 (join), decided by its flows.
function nodeKind(type, id) {
  if (type === "parallelGateway") {
    const role = parallelRole(definition.value, id);
    return role === "fork" ? "并行开始" : role === "join" ? "并行汇合" : "并行网关";
  }
  return NODE_TYPES[type] || showText(type);
}

function nodeTypeLabel(type, id) {
  return NODE_TYPES[type] || type === "parallelGateway" ? `${nodeKind(type, id)}（${type}）` : showText(type);
}

function parallelFlow(flow) {
  const types = [flow?.source_ref, flow?.target_ref].map((ref) => definition.value?.nodes?.find((node) => node.id === ref)?.type);
  return types.includes("parallelGateway");
}

function addParallelAfter(id) {
  branchError.value = "";
  try {
    const next = addDraftNode(definition.value, "parallelGateway", { after: id });
    const fork = next.nodes.find((node) => node.type === "parallelGateway" && !definition.value.nodes.some((item) => item.id === node.id));
    definition.value = next;
    if (fork) selectedId.value = fork.id;
  } catch (err) {
    branchError.value = err.message;
  }
}

function removeParallel(id) {
  branchError.value = "";
  try {
    const next = removeParallelBlock(definition.value, id);
    const before = new Set(next.nodes.map((node) => node.id));
    const anchor = (definition.value.flows || []).find((flow) => before.has(flow.source_ref) && !before.has(flow.target_ref))?.source_ref || "";
    definition.value = next;
    selectedId.value = anchor;
  } catch (err) {
    branchError.value = err.message;
  }
}

// N4 / CF-P4: say where the draft is in 保存 → 校验 → 发布, so a disabled button is never unexplained.
const draftState = computed(() => {
  if (!definition.value) return null;
  if (!isEditing.value) return { tone: "neutral", text: "当前为已发布只读。若要修改，请点「创建可编辑草稿」在副本上编辑，原流程不受影响。" };
  if (!isPersistedDraft.value) return { tone: "warning", text: "草稿尚未保存：先点「保存草稿」，保存后才能校验和发布。" };
  if (dirty.value) return { tone: "warning", text: "有未保存的修改：请先保存，再点「校验草稿」。" };
  if (publication.value && validationResult.value?.valid !== true) return { tone: "success", text: "已保存，并有已发布版本；再次发布前需重新校验。" };
  if (validationResult.value?.valid === true) return { tone: "success", text: "已保存并校验通过：可以发布。" };
  if (validationResult.value) return { tone: "danger", text: "校验未通过：按节点上的提示修改，保存后再校验。" };
  return { tone: "info", text: "已保存：下一步「校验草稿」。" };
});

const validateDisabledReason = computed(() => {
  if (saving.value) return "正在保存，请稍候";
  if (validating.value) return "正在校验，请稍候";
  if (dirty.value) return "有未保存的修改，请先保存再校验";
  return "";
});

function assigneeLabel(value) {
  return ASSIGNEE_PRESETS.find((preset) => preset.value === value)?.label || value;
}

function editableType(type) {
  return ["startEvent", "userTask", "endEvent", "exclusiveGateway", "parallelGateway"].includes(type);
}

async function saveDraft() {
  if (!definition.value || saving.value) return;
  // ZA8: never save a malformed condition; the problems are marked on the graph and listed in the panel.
  if (conditionProblems.value.length) {
    nxMessage.error(`有 ${conditionProblems.value.length} 条连线的条件格式不对，已在图上标出，修改后再保存`);
    selectFlow(conditionProblems.value[0].flow);
    return;
  }
  saving.value = true;
  error.value = null;
  try {
    const saved = await p3.saveProcessDraft(definition.value.key, {
      name: definition.value.name,
      bpmn_xml: serializeDraftXml(definition.value),
    });
    definition.value = { ...definition.value, name: saved.name || definition.value.name, source: "draft" };
    isDraft.value = true;
    isEditing.value = true;
    isPersistedDraft.value = true;
    dirty.value = false;
    validationResult.value = null;
    validationError.value = null;
    publishError.value = null;
    const existing = definitions.value.find((item) => item.key === definition.value.key);
    if (existing) existing.name = definition.value.name;
    else definitions.value.unshift({ key: definition.value.key, name: definition.value.name, source: "draft" });
    nxMessage.success("草稿已保存，下一步校验草稿");
  } catch (err) {
    // Z10: a failed save keeps the editor (and unsaved work) — it used to replace the page with a load error.
    nxMessage.error(`保存没有完成：${describeFailure(err)}`, { traceId: err?.traceId || "", durationMs: 8000 });
  } finally {
    saving.value = false;
  }
}

async function validateDraft() {
  if (!definition.value || !isPersistedDraft.value || dirty.value || validating.value) return;
  validating.value = true;
  validationError.value = null;
  validationResult.value = null;
  try {
    validationResult.value = await p3.validateProcessDraft(definition.value.key);
    if (validationResult.value?.valid) nxMessage.success("校验通过，可以发布");
    else {
      // CF-P2: auto-focus first locatable finding; list items remain clickable.
      const first = validationItems.value?.find((item) => item.node || item.flow);
      if (first?.flow) selectFlow(first.flow);
      else if (first?.node) selectNode(first.node);
    }
  } catch (err) {
    validationError.value = err;
    nxMessage.error(`校验请求没有完成：${describeFailure(err)}`, { traceId: err?.traceId || "", durationMs: 8000 });
  } finally {
    validating.value = false;
  }
}

async function publishDraft() {
  if (!canPublish.value || !definition.value || publishing.value) return;
  publishing.value = true;
  publishError.value = null;
  try {
    publication.value = await p3.publishProcessDraft(definition.value.key);
    publishConfirm.value = false;
    nxMessage.success(publication.value?.version ? `流程已发布为 v${publication.value.version}` : "流程已发布");
  } catch (err) {
    publishError.value = err;
  } finally {
    publishing.value = false;
  }
}

watch(definition, () => {
  validationResult.value = null;
  validationError.value = null;
  publishError.value = null;
  if (dirty.value) publishConfirm.value = false;
  if (isEditing.value) dirty.value = true;
}, { deep: true, flush: "sync" });

watch(() => route.params.key, reload, { immediate: true });
</script>

<style scoped>
.process-loading { display: grid; gap: var(--nx-space-2); max-width: 760px; }
.later-note { margin-top: 0; }
.parallel-scope rect { fill: var(--nx-product-light, var(--nx-primary-light)); stroke: var(--nx-product, var(--nx-primary)); stroke-dasharray: 4 3; opacity: 0.6; }
.parallel-scope text { fill: var(--nx-product, var(--nx-primary)); font-size: 11px; font-weight: 600; }
.parallel-scope-panel { margin-top: var(--nx-space-3); }
.parallel-scope-panel h3 { margin: 0 0 var(--nx-space-1); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); font-weight: 500; }
.parallel-scope-panel ol { display: grid; gap: 4px; margin: 0 0 var(--nx-space-1); padding: 0; list-style: none; font-size: var(--nx-font-size-sm); }
.scope-role { display: inline-block; min-width: 44px; margin-right: 6px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.parallel-scope-panel p { margin: 0; font-size: var(--nx-font-size-xs); }
.node.invalid rect { stroke: var(--nx-danger); stroke-width: 2; }
.flow.invalid .edge { stroke: var(--nx-danger); }
.validation-item { display: block; width: 100%; padding: 2px 0; border: 0; background: none; color: inherit; text-align: left; cursor: pointer; }
.validation-item:hover { color: var(--nx-primary); }
.validation-where { color: var(--nx-text-primary); font-weight: 600; }
.validation-code { margin-left: 4px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.gateway-warning { margin: var(--nx-space-2) 0 0; padding: var(--nx-space-2); border-radius: var(--nx-radius-sm); background: var(--nx-warning-light); color: var(--nx-warning); font-size: var(--nx-font-size-sm); }
.branch-actions { display: grid; gap: 4px; justify-items: start; margin-top: var(--nx-space-2); }
.danger-text { color: var(--nx-danger); padding-left: 0; }
.draft-state { margin: 0 0 var(--nx-space-3); padding: var(--nx-space-2) var(--nx-space-3); border-radius: var(--nx-radius-sm); font-size: var(--nx-font-size-sm); background: var(--nx-info-light); color: var(--nx-info); }
.draft-state[data-tone="warning"] { background: var(--nx-warning-light); color: var(--nx-warning); }
.draft-state[data-tone="success"] { background: var(--nx-success-light); color: var(--nx-success); }
.draft-state[data-tone="danger"] { background: var(--nx-danger-light); color: var(--nx-danger); }
.draft-state[data-tone="neutral"] { background: var(--nx-divider); color: var(--nx-text-regular); }
.step-kind { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.steps-empty { display: grid; gap: 4px; justify-items: center; padding: var(--nx-space-4); border: 1px dashed var(--nx-border); border-radius: var(--nx-radius-sm); text-align: center; }
.steps-empty small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.shell {
  display: grid;
  grid-template-columns: minmax(min(100%, 200px), 240px) minmax(0, 1fr) minmax(min(100%, 220px), 280px);
  gap: var(--nx-space-4);
  align-items: start;
}
.list, .props { display: grid; gap: var(--nx-space-2); }
.draft-name { display: grid; gap: var(--nx-space-1); }
.node-actions { display: flex; flex-wrap: wrap; gap: var(--nx-space-2); }
.node-actions h3 { width: 100%; }
.validation-result { display: grid; gap: var(--nx-space-2); padding: var(--nx-space-2); border: 1px solid var(--nx-border); border-radius: var(--nx-radius-sm); }
.validation-result[data-valid="true"] { border-color: var(--nx-success); }
.validation-result ul { margin: 0; padding-left: 1.1em; display: grid; gap: var(--nx-space-1); }
.validation-result li span, .validation-error, .publish-error { color: var(--nx-text-secondary); }
.publication-result { display: grid; gap: var(--nx-space-1); padding: var(--nx-space-2); border: 1px solid var(--nx-success); border-radius: var(--nx-radius-sm); }
.confirm-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: var(--nx-space-4); background: rgb(0 0 0 / 48%); }
.confirm-facts { margin: var(--nx-space-3) 0; padding-left: 1.2em; color: var(--nx-text-regular); }
.confirm-facts li { margin: var(--nx-space-1) 0; }
.confirm-dialog { width: min(100%, 440px); display: grid; gap: var(--nx-space-3); }
.confirm-facts { margin: var(--nx-space-3) 0; padding-left: 1.2em; color: var(--nx-text-regular); }
.confirm-facts li { margin: var(--nx-space-1) 0; }
.confirm-dialog h2, .confirm-facts { margin: var(--nx-space-3) 0; padding-left: 1.2em; color: var(--nx-text-regular); }
.confirm-facts li { margin: var(--nx-space-1) 0; }
.confirm-dialog p { margin: 0; }
.confirm-actions { display: flex; justify-content: flex-end; gap: var(--nx-space-2); }
.list h2, .canvas h2, .props h2, .props h3 { margin: 0; font-size: var(--nx-font-size-md); }
.list h2 small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); font-weight: 400; }
.item {
  display: grid;
  gap: 2px;
  padding: var(--nx-space-2);
  text-align: left;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-sm);
  background: transparent;
  color: inherit;
}
.item.current { border-color: var(--nx-product); }
.item span, .props li span, .type { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.item-meta { display: flex; align-items: center; justify-content: space-between; gap: var(--nx-space-2); min-width: 0; }
.item-key { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-state { flex: none; padding: 2px 6px; border-radius: var(--nx-radius-pill); color: var(--nx-success) !important; background: var(--nx-success-light); }
.item-state[data-source="draft"] { color: var(--nx-warning) !important; background: var(--nx-warning-light); }
.canvas { overflow: auto; }
svg.graph { display: block; min-width: 100%; }
.flow { cursor: pointer; }
.flow .edge { fill: none; stroke: var(--nx-border-strong, var(--nx-border)); stroke-width: 1.5; }
.arrow { fill: var(--nx-border-strong, var(--nx-border)); }
.flow .hit { fill: none; stroke: transparent; stroke-width: 12; }
.flow.conditional .edge { stroke-dasharray: 5 3; }
.flow.current .edge { stroke: var(--nx-product); stroke-width: 2.5; }
.flow-condition { fill: var(--nx-text-secondary); font-size: 11px; }
.flow-props dl { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: var(--nx-space-1) var(--nx-space-2); margin: 0; }
.flow-props dt { color: var(--nx-text-secondary); }
.flow-props dd { display: grid; gap: 4px; margin: 0; overflow-wrap: anywhere; }
.flow-props input, .flow-props select { box-sizing: border-box; width: 100%; min-height: 32px; }
.condition-editor { display: grid; gap: 6px; }
/* ZA9: the condition row uses the full panel width (label above), instead of the 64px/rest grid. */
.flow-props .condition-label { grid-column: 1 / -1; margin-top: var(--nx-space-1); }
.flow-props dd.wide { grid-column: 1 / -1; }
.branch-summary { margin-top: var(--nx-space-3); }
.branch-summary h3 { margin: 0 0 var(--nx-space-1); color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); font-weight: 500; }
.branch-summary ol { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.branch-line { display: grid; grid-template-columns: auto 1fr; gap: 0 var(--nx-space-2); width: 100%; padding: 6px; border: 0; border-radius: var(--nx-radius-sm); background: none; color: inherit; text-align: left; cursor: pointer; }
.branch-line:hover { background: var(--nx-bg-page); }
.branch-index { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.branch-target { color: var(--nx-text-primary); font-size: var(--nx-font-size-sm); }
.branch-condition { grid-column: 2; color: var(--nx-product); font-size: var(--nx-font-size-sm); }
.branch-condition.none { color: var(--nx-text-secondary); }
.branch-condition.bad { color: var(--nx-danger); }
.mode-switch { display: inline-flex; width: max-content; padding: 2px; border-radius: var(--nx-radius-sm); background: var(--nx-bg-page); }
.mode-switch button { padding: 2px 10px; border: 0; border-radius: 4px; background: none; color: var(--nx-text-secondary); cursor: pointer; font-size: var(--nx-font-size-xs); }
.mode-switch button.active { background: var(--nx-surface); color: var(--nx-text-primary); font-weight: 600; box-shadow: 0 1px 2px rgb(0 0 0 / 8%); }
.condition-incomplete { margin: 0; padding: 6px 8px; border-radius: var(--nx-radius-sm); background: var(--nx-warning-light); color: var(--nx-warning); font-size: var(--nx-font-size-xs); }
.condition-foot { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 4px; }
.related-flow { display: grid; width: 100%; padding: 4px 6px; border: 0; border-radius: var(--nx-radius-sm); background: none; color: inherit; text-align: left; cursor: pointer; }
.related-flow:hover { background: var(--nx-bg-page); }
.node { cursor: pointer; }
.node rect { fill: var(--nx-surface); stroke: var(--nx-border); }
.node.current rect { stroke: var(--nx-product); stroke-width: 2; }
text { fill: var(--nx-text-primary); font-size: 13px; }
.props dl { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: var(--nx-space-1) var(--nx-space-2); margin: 0; }
.props dt { color: var(--nx-text-secondary); }
.props dd { margin: 0; overflow-wrap: anywhere; }
.props ul { margin: 0; padding-left: 1.1em; display: grid; gap: var(--nx-space-2); }
.props li { display: grid; }
.canvas-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--nx-space-3); margin-bottom: var(--nx-space-4); }
.canvas-head p { margin: 4px 0 0; font-size: var(--nx-font-size-xs); }
.mode-chip {
  flex: none;
  padding: 2px 10px;
  border-radius: var(--nx-radius-pill);
  font-size: var(--nx-font-size-xs);
  font-weight: 600;
  color: var(--nx-product);
  background: var(--nx-product-light);
}
.flow {
  list-style: none;
  margin: 0 auto;
  padding: var(--nx-space-4);
  max-width: 560px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border-radius: var(--nx-radius);
  background: var(--nx-bg-page);
}
.terminal {
  display: flex;
  align-items: center;
  gap: var(--nx-space-3);
  align-self: center;
  min-width: 240px;
  padding: 8px 16px 8px 8px;
  border: 1px solid var(--nx-border);
  border-radius: var(--nx-radius-pill);
  background: var(--nx-surface);
  cursor: pointer;
}
.terminal strong, .step-body strong { display: block; color: var(--nx-text-primary); font-size: var(--nx-font-size-md); }
.terminal small { color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.terminal-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--nx-surface);
  background: var(--nx-success);
}
.terminal.end .terminal-icon { background: var(--nx-text-secondary); }
.terminal.current, .step.current { border-color: var(--nx-product); box-shadow: 0 0 0 3px var(--nx-product-light); }
.missing-end { opacity: 0.6; cursor: default; }
.connector { position: relative; display: grid; place-items: center; height: 36px; }
.connector .line { position: absolute; top: 0; bottom: 0; left: 50%; width: 2px; background: var(--nx-border-strong); }
.connector .line::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: -4px;
  border: 5px solid transparent;
  border-top-color: var(--nx-border-strong);
  border-bottom: 0;
}
.insert {
  position: relative;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid var(--nx-border-strong);
  border-radius: 50%;
  background: var(--nx-surface);
  color: var(--nx-text-secondary);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s, border-color 0.15s;
}
.flow:hover .insert, .insert:focus-visible { opacity: 1; }
.insert:hover { color: var(--nx-product); border-color: var(--nx-product); }
.step {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--nx-space-3);
  padding: 12px;
  border: 1px solid var(--nx-border);
  border-left: 3px solid var(--nx-product);
  border-radius: var(--nx-radius-sm);
  background: var(--nx-surface);
  box-shadow: var(--nx-shadow-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s;
}
.flow:not(.editing) .step { grid-template-columns: auto minmax(0, 1fr); }
.step:focus-visible, .terminal:focus-visible { outline: 2px solid var(--nx-product); outline-offset: 2px; }
.step.invalid { border-color: var(--nx-danger); border-left-color: var(--nx-danger); }
.step.dragging { opacity: 0.4; }
.grip { color: var(--nx-text-disabled); cursor: grab; }
.num {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  font-size: var(--nx-font-size-xs);
  font-weight: 700;
  color: var(--nx-product);
  background: var(--nx-product-light);
}
.step-body { display: grid; gap: 4px; min-width: 0; }
.assignee { display: inline-flex; align-items: center; gap: 4px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-sm); }
.assignee.missing { color: var(--nx-warning); }
.step-error { color: var(--nx-danger); font-size: var(--nx-font-size-sm); margin: 0; }
.step-actions { display: flex; gap: 2px; opacity: 0.55; transition: opacity 0.15s; }
.step:hover .step-actions, .step.current .step-actions, .step:focus-within .step-actions { opacity: 1; }
.icon-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--nx-text-secondary);
  cursor: pointer;
}
.icon-btn:hover:not(:disabled) { color: var(--nx-product); background: var(--nx-product-light); }
.icon-btn.danger-hover:hover:not(:disabled) { color: var(--nx-danger); background: var(--nx-danger-light); }
.icon-btn:disabled { color: var(--nx-text-disabled); cursor: not-allowed; }
.drop-line { height: 3px; margin: 2px 0; border-radius: 2px; background: var(--nx-product); }
.add-step { display: flex; align-items: center; gap: 4px; margin: var(--nx-space-3) auto 0; }
.nonsequential {
  margin: 0 0 var(--nx-space-3);
  padding: 8px 12px;
  border-radius: var(--nx-radius-sm);
  color: var(--nx-warning);
  background: var(--nx-warning-light);
  font-size: var(--nx-font-size-sm);
}
.presets { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.preset {
  padding: 2px 10px;
  border: 1px solid var(--nx-border-strong);
  border-radius: var(--nx-radius-pill);
  background: var(--nx-surface);
  color: var(--nx-text-regular);
  font-size: var(--nx-font-size-xs);
  cursor: pointer;
}
.preset.active, .preset:hover { border-color: var(--nx-product); color: var(--nx-product); background: var(--nx-product-light); }
.preset-hint { display: block; margin-top: 6px; color: var(--nx-text-secondary); font-size: var(--nx-font-size-xs); }
.props input { width: 100%; box-sizing: border-box; border: 1px solid var(--nx-border-strong); border-radius: 6px; padding: 6px 8px; font: inherit; }
@media (max-width: 960px) {
  .shell { grid-template-columns: 1fr; }
}
</style>