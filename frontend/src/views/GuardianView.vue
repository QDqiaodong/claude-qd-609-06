<template>
  <div class="page guardian-page">
    <div class="page-head">
      <h2 class="page-title">
        未成年会员监护授权台
        <em>版本确认 · 现场接管 · 课程/箭道/器材放行 · 安全交还</em>
      </h2>
      <el-input v-model="operator" placeholder="当前工作人员姓名" style="width: 180px" />
    </div>

    <div class="rule-tip">
      授权范围、生效日/终止日或接管人变化都会生成新版本并重新确认；旧版本可查但不能继续放行。
      每次到馆独立交接，未完成交还不能开启下一次到馆。接管后授权缩小、暂停或撤销时，只冻结未开始动作并保留异常轨迹，直至安全交还。
    </div>

    <el-tabs v-model="tab" class="guardian-tabs">
      <!-- ==================== 授权版本 ==================== -->
      <el-tab-pane label="监护授权" name="auth">
        <div class="panel toolbar-panel">
          <span class="field-label">未成年会员</span>
          <el-select v-model="minorId" filterable placeholder="选择未成年会员" style="width: 280px" @change="loadAuth">
            <el-option v-for="m in minors" :key="m.id" :label="`${m.name} · ${m.cardNo} · ${m.birthDate}`" :value="m.id" />
          </el-select>
          <el-button :disabled="!minorId" @click="openCreateDraft">新建授权草稿</el-button>
          <el-tag v-if="currentVersion" :type="statusType(currentVersion.status)" effect="dark">
            当前 v{{ currentVersion.versionNo }} · {{ currentVersion.statusName }}
          </el-tag>
        </div>

        <template v-if="auth">
          <div class="auth-grid">
            <div class="panel">
              <div class="panel-title">授权版本 <small>所有历史版本可追溯，旧版本不放行</small></div>
              <el-table :data="auth.versions" border size="small" :row-class-name="versionRowClass">
                <el-table-column label="版本" width="72">
                  <template #default="{ row }">
                    <b>v{{ row.versionNo }}</b>
                    <el-tag v-if="row.current" size="small" type="success" effect="dark" style="margin-left:4px">当前</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="statusType(row.status)" effect="plain">{{ row.statusName }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="有效期" width="190">
                  <template #default="{ row }">{{ row.effectiveDate }} ~ {{ row.terminationDate }}</template>
                </el-table-column>
                <el-table-column prop="guardianName" label="监护人" width="90" />
                <el-table-column label="范围" min-width="190">
                  <template #default="{ row }">
                    课程 {{ row.scope.courses.length }} · 箭道 {{ row.scope.lanes.length }} · 器材 {{ row.scope.equipment.length }} · 接管 {{ row.scope.caretakerNames.length }}
                  </template>
                </el-table-column>
              </el-table>

              <template v-if="currentVersion">
                <el-descriptions title="当前版本基线" :column="2" border class="baseline-desc">
                  <el-descriptions-item label="监护人">{{ currentVersion.guardianName }} {{ currentVersion.guardianPhone }}</el-descriptions-item>
                  <el-descriptions-item label="紧急联系人">{{ currentVersion.emergencyContactName }} {{ currentVersion.emergencyContactPhone }}</el-descriptions-item>
                  <el-descriptions-item label="生效期">{{ currentVersion.effectiveDate }} 至 {{ currentVersion.terminationDate }}</el-descriptions-item>
                  <el-descriptions-item label="确认人/时间">{{ currentVersion.confirmedBy || '-' }} {{ currentVersion.confirmedAt || '' }}</el-descriptions-item>
                  <el-descriptions-item label="课程范围">
                    <el-tag v-for="c in currentVersion.scope.courses" :key="c.id" size="small" class="scope-tag">{{ c.name }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="箭道范围">
                    <el-tag v-for="l in currentVersion.scope.lanes" :key="l.id" size="small" type="success" class="scope-tag">{{ l.name }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="器材范围">
                    <el-tag v-for="e in currentVersion.scope.equipment" :key="e.id" size="small" type="warning" class="scope-tag">{{ e.name }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="接管人员">
                    <el-tag v-for="n in currentVersion.scope.caretakerNames" :key="n" size="small" type="danger" class="scope-tag">{{ n }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="备注" :span="2">{{ currentVersion.remarks || '—' }}</el-descriptions-item>
                </el-descriptions>

                <div class="action-row">
                  <el-button v-if="currentVersion.status === 'DRAFT'" type="primary" @click="editDraft(currentVersion)">编辑草稿</el-button>
                  <el-button v-if="currentVersion.status === 'DRAFT'" type="success" @click="submitVersion(currentVersion)">提交监护人确认</el-button>
                  <el-button v-if="currentVersion.status === 'PENDING'" type="success" @click="confirmVersion(currentVersion)">监护人确认生效</el-button>
                  <el-button v-if="currentVersion.status === 'VALID'" type="warning" @click="pauseVersion(currentVersion)">暂停</el-button>
                  <el-button v-if="currentVersion.status === 'PAUSED'" type="success" @click="resumeVersion(currentVersion)">监护人确认恢复</el-button>
                  <el-button v-if="['VALID','PAUSED'].includes(currentVersion.status)" @click="openRevise(currentVersion)">范围/日期/接管人变更（新版本）</el-button>
                  <el-button v-if="['VALID','PAUSED'].includes(currentVersion.status)" type="danger" @click="revokeVersion(currentVersion)">撤销</el-button>
                </div>
              </template>
            </div>

            <div class="panel">
              <div class="panel-title">连续历史轨迹 <small>状态变化前后基线、操作人、时间、原因</small></div>
              <el-timeline class="history-timeline">
                <el-timeline-item v-for="h in auth.history" :key="h.id" :timestamp="h.operatedAt" placement="top">
                  <div class="trail-line">
                    <el-tag size="small" effect="plain">{{ actionName(h.action) }}</el-tag>
                    <b>v{{ h.versionNo }}</b>
                    <span>{{ h.fromStatus || '—' }} → {{ h.toStatus }}</span>
                    <em>{{ h.operator }}</em>
                  </div>
                  <div class="trail-reason">{{ h.reason }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>
        </template>
        <el-empty v-else-if="minorId" description="该未成年会员尚未建立监护授权，请先新建草稿" />
      </el-tab-pane>

      <!-- ==================== 现场交接 ==================== -->
      <el-tab-pane label="到馆交接" name="handover">
        <div class="panel toolbar-panel">
          <span class="field-label">未成年会员</span>
          <el-select v-model="minorId" filterable placeholder="选择未成年会员" style="width: 280px" @change="onMinorChange">
            <el-option v-for="m in minors" :key="m.id" :label="`${m.name} · ${m.cardNo} · ${m.birthDate}`" :value="m.id" />
          </el-select>
          <el-button type="primary" :disabled="!minorId" @click="createHandover">建立到馆交接</el-button>
          <el-button :disabled="!minorId" @click="loadHandovers">刷新</el-button>
        </div>

        <div class="handover-grid">
          <div class="panel">
            <div class="panel-title">交接记录 <small>未交还记录不能关闭，也不能开启下一次</small></div>
            <el-table :data="handovers" border size="small" highlight-current-row @current-change="onPickHandover">
              <el-table-column prop="handoverNo" label="交接号" width="150" />
              <el-table-column label="版本" width="72">
                <template #default="{ row }">v{{ row.versionNo }}</template>
              </el-table-column>
              <el-table-column label="阶段" width="112">
                <template #default="{ row }">
                  <el-tag size="small" :type="stageType(row.stage)" effect="dark">{{ row.stageName }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="接管人" min-width="100">
                <template #default="{ row }">{{ row.actualCaretaker || '—' }}</template>
              </el-table-column>
              <el-table-column label="异常" width="70">
                <template #default="{ row }">
                  <el-tag v-if="row.frozen" size="small" type="danger" effect="dark">冻结</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="panel" v-if="handover">
            <div class="panel-title">
              {{ handover.handoverNo }}
              <el-tag size="small" :type="stageType(handover.stage)" effect="dark">{{ handover.stageName }}</el-tag>
              <small>依据 v{{ handover.versionNo }}</small>
            </div>

            <el-alert v-if="handover.frozen" type="error" show-icon :closable="false" class="freeze-alert">
              <template #title>已冻结后续放行：{{ handover.freezeReason }}</template>
              <div>请按当前授权重新核对；已发生事实不回滚，训练只能进入安全交还。</div>
            </el-alert>

            <el-steps :active="stageIndex(handover.stage)" align-center finish-status="success" class="stage-steps">
              <el-step title="待接管" />
              <el-step title="已接管" />
              <el-step title="训练中" />
              <el-step title="待交还" />
              <el-step title="已交还" />
            </el-steps>

            <el-descriptions :column="2" border size="small" class="handover-desc">
              <el-descriptions-item label="接管人">{{ handover.actualCaretaker || '—' }}</el-descriptions-item>
              <el-descriptions-item label="证件号">{{ handover.caretakerIdentityNo || '—' }}</el-descriptions-item>
              <el-descriptions-item label="接管时间">{{ handover.takenOverAt || '—' }}</el-descriptions-item>
              <el-descriptions-item label="训练开始">{{ handover.trainingStartedAt || '—' }}</el-descriptions-item>
              <el-descriptions-item label="交还人">{{ handover.returnedCaretaker || '—' }}</el-descriptions-item>
              <el-descriptions-item label="交还时间">{{ handover.returnedAt || '—' }}</el-descriptions-item>
            </el-descriptions>

            <!-- 接管 -->
            <div v-if="handover.stage === 'WAITING_TAKEOVER'" class="stage-box">
              <div class="stage-title">1. 现场核对接管人身份与授权名单</div>
              <el-form label-width="92px" size="small">
                <el-form-item label="接管人姓名"><el-input v-model="takeoverForm.caretakerName" /></el-form-item>
                <el-form-item label="证件号"><el-input v-model="takeoverForm.identityNo" /></el-form-item>
                <el-button type="primary" @click="takeover">确认接管</el-button>
              </el-form>
            </div>

            <!-- 训练前整组校验 + 实际放行 -->
            <div v-if="handover.stage === 'TAKEN_OVER'" class="stage-box">
              <div class="stage-title">2. 训练开始前整组核对课程、箭道、器材</div>
              <el-form label-width="92px" size="small">
                <el-form-item label="课程">
                  <el-select v-model="precheckForm.courseId" filterable style="width:100%">
                    <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="箭道">
                  <el-select v-model="precheckForm.laneId" filterable style="width:100%">
                    <el-option v-for="l in lanes" :key="l.id" :label="`${l.laneNo} · ${l.distance}米 · ${l.statusName}`" :value="l.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="器材">
                  <el-select v-model="precheckForm.equipmentIds" multiple filterable style="width:100%">
                    <el-option v-for="e in equipment" :key="e.id" :label="`${e.equipCode} · ${e.typeName} · ${e.statusName}`" :value="e.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="开台时长">
                  <el-input-number v-model="laneHours" :min="1" :max="4" /> 小时
                </el-form-item>
                <el-button type="primary" @click="precheckTraining">整组校验并授权放行</el-button>
              </el-form>

              <template v-if="handover.resources.length">
                <div class="stage-title">3. 到业务页面完成实际入场 / 领用，或在此快捷确认</div>
                <el-table :data="handover.resources" border size="small">
                  <el-table-column label="类型" width="90">
                    <template #default="{ row }">{{ resourceTypeName(row.resourceType) }}</template>
                  </el-table-column>
                  <el-table-column prop="resourceName" label="资源" />
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.status === 'USED' ? 'success' : 'warning'" effect="dark">
                        {{ row.status === 'USED' ? '已使用' : '待使用' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="110">
                    <template #default="{ row }">
                      <el-button v-if="row.status === 'CLEARED'" link type="primary" @click="useResource(row)">确认使用</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-button class="stage-main-btn" type="success" @click="startTraining">训练签到并开始</el-button>
              </template>
            </div>

            <!-- 训练中 / 待交还 -->
            <div v-if="['TRAINING','WAITING_RETURN'].includes(handover.stage)" class="stage-box">
              <div class="stage-title">4. 离馆前安全交还</div>
              <el-form label-width="92px" size="small">
                <el-form-item label="交还接管人"><el-input v-model="returnForm.caretakerName" /></el-form-item>
                <el-form-item label="证件号"><el-input v-model="returnForm.identityNo" /></el-form-item>
                <el-button v-if="handover.stage === 'TRAINING'" @click="finishTraining">结束训练，等待交还</el-button>
                <el-button type="primary" @click="returnMinor">完成离馆交还</el-button>
              </el-form>
            </div>

            <!-- 基线变化后的重核 -->
            <div v-if="handover.frozen && ['TAKEN_OVER'].includes(handover.stage)" class="stage-box exception-box">
              <div class="stage-title">异常处置：按新版本重新核对</div>
              <el-input v-model="rebaselineReason" type="textarea" :rows="2" placeholder="说明工作人员如何按新基线重新核对" />
              <el-button type="warning" @click="rebaseline">依据当前版本重新核对</el-button>
            </div>

            <div class="trail-panel">
              <div class="stage-title">交接轨迹</div>
              <el-timeline>
                <el-timeline-item v-for="t in handover.trails" :key="t.id" :timestamp="t.operatedAt" placement="top">
                  <div class="trail-line">
                    <el-tag size="small" :type="t.blockReason ? 'danger' : 'info'" effect="plain">{{ actionName(t.action) }}</el-tag>
                    <b>v{{ t.versionNo }}</b>
                    <span>{{ t.fromStage || '—' }} → {{ t.toStage }}</span>
                    <em>{{ t.operator }}</em>
                  </div>
                  <div class="trail-reason">{{ t.reason }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 授权草稿编辑 -->
    <el-dialog v-model="draftVisible" :title="draftForm.id ? '编辑授权草稿' : '新建监护授权草稿'" width="860px">
      <el-form label-width="110px">
        <div class="dialog-grid">
          <el-form-item label="监护人姓名"><el-input v-model="draftForm.guardianName" /></el-form-item>
          <el-form-item label="监护人手机"><el-input v-model="draftForm.guardianPhone" /></el-form-item>
          <el-form-item label="生效日"><el-date-picker v-model="draftForm.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="终止日"><el-date-picker v-model="draftForm.terminationDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="紧急联系人"><el-input v-model="draftForm.emergencyContactName" /></el-form-item>
          <el-form-item label="紧急电话"><el-input v-model="draftForm.emergencyContactPhone" /></el-form-item>
        </div>
        <el-form-item label="允许课程">
          <el-select v-model="draftForm.courseIds" multiple filterable style="width:100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可用箭道">
          <el-select v-model="draftForm.laneIds" multiple filterable style="width:100%">
            <el-option v-for="l in lanes" :key="l.id" :label="`${l.laneNo} · ${l.distance}米`" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可用器材">
          <el-select v-model="draftForm.equipmentIds" multiple filterable style="width:100%">
            <el-option v-for="e in equipment" :key="e.id" :label="`${e.equipCode} · ${e.typeName}`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="接管人员">
          <el-select v-model="draftForm.caretakerNames" multiple allow-create filterable default-first-option placeholder="输入姓名后回车" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="draftForm.remarks" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="draftForm.reason" placeholder="建立/修订原因" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="draftVisible = false">取消</el-button>
        <el-button v-if="draftForm.id" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" @click="saveDraft">{{ draftForm.id ? '保存草稿' : '建立草稿' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { courseApi, equipApi, guardianApi, laneApi } from '../api'

const tab = ref('auth')
const operator = ref('前台小王')
const minors = ref([])
const minorId = ref(null)
const auth = ref(null)
const handovers = ref([])
const handover = ref(null)
const courses = ref([])
const lanes = ref([])
const equipment = ref([])
const draftVisible = ref(false)
const laneHours = ref(1)
const rebaselineReason = ref('')

const emptyDraft = () => ({
  id: null,
  authorizationId: null,
  guardianName: '',
  guardianPhone: '',
  effectiveDate: '',
  terminationDate: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  remarks: '',
  courseIds: [],
  laneIds: [],
  equipmentIds: [],
  caretakerNames: [],
  reason: ''
})
const draftForm = reactive(emptyDraft())
const takeoverForm = reactive({ caretakerName: '', identityNo: '' })
const returnForm = reactive({ caretakerName: '', identityNo: '' })
const precheckForm = reactive({ courseId: null, laneId: null, equipmentIds: [] })

const currentVersion = computed(() => auth.value?.versions?.find(v => v.current) || null)

function requireOperator() {
  if (!operator.value.trim()) {
    ElMessage.warning('请先填写当前工作人员姓名')
    return false
  }
  return true
}

function statusType(status) {
  return {
    DRAFT: 'info',
    PENDING: 'warning',
    VALID: 'success',
    PAUSED: 'warning',
    REVOKED: 'danger',
    EXPIRED: 'info',
    SUPERSEDED: 'info'
  }[status] || 'info'
}

function stageType(stage) {
  return {
    WAITING_TAKEOVER: 'warning',
    TAKEN_OVER: 'primary',
    TRAINING: 'success',
    WAITING_RETURN: 'warning',
    RETURNED: 'info'
  }[stage] || 'info'
}

function stageIndex(stage) {
  return ['WAITING_TAKEOVER', 'TAKEN_OVER', 'TRAINING', 'WAITING_RETURN', 'RETURNED'].indexOf(stage)
}

function resourceTypeName(type) {
  return { COURSE: '课程', LANE: '箭道', EQUIPMENT: '器材' }[type] || type
}

function actionName(action) {
  return {
    DRAFT_CREATED: '建立草稿',
    DRAFT_UPDATED: '修改草稿',
    SUBMIT_CONFIRMATION: '提交确认',
    GUARDIAN_CONFIRM: '监护人确认',
    REVISE_DRAFT: '发起修订',
    PAUSE: '暂停',
    RESUME: '恢复',
    REVOKE: '撤销',
    SUPERSEDE_OLD: '旧版被取代',
    ARRIVAL_CREATE: '到馆建单',
    TAKEOVER: '现场接管',
    TRAINING_PRECHECK: '训练前校验',
    TRAINING_START: '训练开始',
    FINISH_TRAINING: '结束训练',
    RETURNED: '安全交还',
    BASELINE_SUPERSEDED: '基线更新冻结',
    AUTHORIZATION_PAUSED: '授权暂停冻结',
    AUTHORIZATION_REVOKED: '授权撤销冻结',
    AUTHORIZATION_INVALID: '授权失效冻结',
    REBASELINE: '按新基线重核',
    FREEZE_CLEARED: '解除冻结',
    EXCEPTION_CLOSED: '异常闭环'
  }[action] || action
}

async function loadOptions() {
  const [minorList, courseList, laneList, equipList] = await Promise.all([
    guardianApi.minorOptions(),
    courseApi.options().catch(() => []),
    laneApi.options().catch(() => []),
    equipApi.options().catch(() => [])
  ])
  minors.value = minorList
  courses.value = courseList
  lanes.value = laneList
  equipment.value = equipList
  if (!minorId.value && minorList.length) minorId.value = minorList[0].id
  if (minorId.value) await onMinorChange()
}

async function onMinorChange() {
  await Promise.allSettled([loadAuth(), loadHandovers()])
}

async function loadAuth() {
  auth.value = null
  if (!minorId.value) return
  try {
    auth.value = await guardianApi.detailByMinor(minorId.value)
  } catch (e) {
    // 新未成年会员尚无授权属于正常空态
  }
}

async function loadHandovers() {
  handover.value = null
  if (!minorId.value) return
  handovers.value = await guardianApi.handovers(minorId.value)
  const open = handovers.value.find(h => h.stage !== 'RETURNED')
  handover.value = open || handovers.value[0] || null
  hydrateHandoverForms(handover.value)
}

function onPickHandover(row) {
  handover.value = row
  hydrateHandoverForms(row)
}

function hydrateHandoverForms(row) {
  takeoverForm.caretakerName = row?.actualCaretaker || ''
  takeoverForm.identityNo = row?.caretakerIdentityNo || ''
  returnForm.caretakerName = row?.actualCaretaker || row?.returnedCaretaker || ''
  returnForm.identityNo = row?.caretakerIdentityNo || ''
  rebaselineReason.value = ''
}

function versionPayload(extra = {}) {
  return {
    guardianName: draftForm.guardianName,
    guardianPhone: draftForm.guardianPhone,
    effectiveDate: draftForm.effectiveDate,
    terminationDate: draftForm.terminationDate,
    emergencyContactName: draftForm.emergencyContactName,
    emergencyContactPhone: draftForm.emergencyContactPhone,
    remarks: draftForm.remarks,
    courseIds: draftForm.courseIds,
    laneIds: draftForm.laneIds,
    equipmentIds: draftForm.equipmentIds,
    caretakerNames: draftForm.caretakerNames,
    operator: operator.value,
    reason: draftForm.reason,
    ...extra
  }
}

function openCreateDraft() {
  if (!requireOperator() || !minorId.value) return
  Object.assign(draftForm, emptyDraft())
  draftVisible.value = true
}

function fillDraft(version) {
  Object.assign(draftForm, {
    id: version.id,
    authorizationId: version.authorizationId,
    guardianName: version.guardianName,
    guardianPhone: version.guardianPhone,
    effectiveDate: version.effectiveDate,
    terminationDate: version.terminationDate,
    emergencyContactName: version.emergencyContactName,
    emergencyContactPhone: version.emergencyContactPhone,
    remarks: version.remarks || '',
    courseIds: version.scope.courses.map(c => c.id),
    laneIds: version.scope.lanes.map(l => l.id),
    equipmentIds: version.scope.equipment.map(e => e.id),
    caretakerNames: [...version.scope.caretakerNames],
    reason: ''
  })
  draftVisible.value = true
}

function editDraft(version) {
  fillDraft(version)
}

function openRevise(version) {
  fillDraft(version)
  draftForm.id = null
  draftForm.reason = ''
}

async function saveDraft() {
  if (!requireOperator()) return
  if (draftForm.id) {
    auth.value = await guardianApi.updateDraft(versionPayload({ versionId: draftForm.id }))
  } else if (draftForm.authorizationId) {
    auth.value = await guardianApi.revise(versionPayload({ authorizationId: draftForm.authorizationId }))
  } else {
    auth.value = await guardianApi.createDraft(versionPayload({ minorId: minorId.value }))
  }
  ElMessage.success('草稿已保存')
  draftVisible.value = false
}

async function reasonText(title, placeholder = '请填写原因') {
  const { value } = await ElMessageBox.prompt(placeholder, title, {
    inputPlaceholder: placeholder,
    inputValue: '',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  return value || placeholder
}

async function submitVersion(version) {
  if (!requireOperator()) return
  const reason = await reasonText('提交监护人确认', '请填写提交说明')
  auth.value = await guardianApi.submit(version.id, { operator: operator.value, reason })
}

async function confirmVersion(version) {
  if (!requireOperator()) return
  const reason = await reasonText('监护人确认', '请监护人填写确认说明')
  auth.value = await guardianApi.confirm(version.id, { operator: operator.value || version.guardianName, reason })
  await loadHandovers()
}

async function pauseVersion(version) {
  const reason = await reasonText('暂停授权', '请填写暂停原因')
  auth.value = await guardianApi.pause(version.id, { operator: operator.value, reason })
  await loadHandovers()
}

async function resumeVersion(version) {
  const reason = await reasonText('恢复授权', '请监护人填写恢复确认说明')
  auth.value = await guardianApi.resume(version.id, { operator: operator.value, reason })
  await loadHandovers()
}

async function revokeVersion(version) {
  const reason = await reasonText('撤销授权', '请填写撤销原因')
  auth.value = await guardianApi.revoke(version.id, { operator: operator.value, reason })
  await loadHandovers()
}

async function createHandover() {
  if (!requireOperator()) return
  handover.value = await guardianApi.createHandover({ minorId: minorId.value, operator: operator.value })
  ElMessage.success('交接单已建立')
  await loadHandovers()
}

async function refreshHandover(id = handover.value?.id) {
  if (!id) return
  handover.value = await guardianApi.handover(id)
  await loadAuth()
  await loadHandovers()
  handover.value = await guardianApi.handover(id)
  hydrateHandoverForms(handover.value)
}

async function takeover() {
  if (!requireOperator()) return
  await guardianApi.takeover({
    handoverId: handover.value.id,
    expectedVersionId: handover.value.authorizationVersionId,
    caretakerName: takeoverForm.caretakerName,
    identityNo: takeoverForm.identityNo,
    operator: operator.value
  })
  await refreshHandover()
}

async function precheckTraining() {
  if (!requireOperator()) return
  const requestId = `REQ-${Date.now()}`
  handover.value = await guardianApi.precheckTraining({
    handoverId: handover.value.id,
    courseId: precheckForm.courseId,
    laneId: precheckForm.laneId,
    equipmentIds: precheckForm.equipmentIds,
    requestId,
    operator: operator.value
  })
  ElMessage.success('整组授权校验通过，请完成实际入场/领用')
  await refreshHandover(handover.value.id)
}

async function useResource(row) {
  if (!minorId.value) return
  if (row.resourceType === 'COURSE') {
    await courseApi.enroll(row.resourceId, minorId.value)
  } else if (row.resourceType === 'LANE') {
    await laneApi.open(row.resourceId, { memberId: minorId.value, hours: laneHours.value })
  } else {
    await equipApi.rent(row.resourceId, minorId.value)
  }
  ElMessage.success(`${row.resourceName} 已完成实际使用确认`)
  await refreshHandover(handover.value.id)
}

async function startTraining() {
  handover.value = await guardianApi.startTraining(handover.value.id, { operator: operator.value })
  await refreshHandover(handover.value.id)
}

async function finishTraining() {
  handover.value = await guardianApi.finishTraining(handover.value.id, { operator: operator.value })
  await refreshHandover(handover.value.id)
}

async function returnMinor() {
  await guardianApi.returnMinor({
    handoverId: handover.value.id,
    caretakerName: returnForm.caretakerName,
    identityNo: returnForm.identityNo,
    operator: operator.value,
    reason: '离馆前安全交还'
  })
  await refreshHandover()
}

async function rebaseline() {
  if (!currentVersion.value) {
    ElMessage.warning('当前没有有效授权版本')
    return
  }
  handover.value = await guardianApi.rebaseline({
    handoverId: handover.value.id,
    expectedVersionId: currentVersion.value.id,
    operator: operator.value,
    reason: rebaselineReason.value || '工作人员按新基线重新核对'
  })
  await refreshHandover(handover.value.id)
}

function versionRowClass({ row }) {
  return row.current ? 'current-version-row' : ''
}

onMounted(loadOptions)
</script>

<style scoped>
.guardian-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar-panel {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-label {
  font-weight: 700;
  color: var(--ink-soft);
}

.auth-grid,
.handover-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 12px;
  margin-top: 12px;
}

.handover-grid {
  grid-template-columns: minmax(360px, 0.9fr) minmax(520px, 1.1fr);
}

.baseline-desc {
  margin-top: 12px;
}

.scope-tag {
  margin: 2px 4px 2px 0;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.history-timeline {
  padding: 12px 8px 0 8px;
}

.trail-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  font-size: 12px;
}

.trail-line em {
  font-style: normal;
  color: var(--ink-mute);
  margin-left: auto;
}

.trail-reason {
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 12px;
}

.freeze-alert {
  margin: 12px 0;
}

.stage-steps {
  margin: 18px 8px;
}

.handover-desc {
  margin: 8px 0 14px;
}

.stage-box {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px;
  margin: 10px 0;
  background: #fbfdfd;
}

.exception-box {
  border-color: #f56c6c;
  background: #fff7f7;
}

.stage-title {
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--ink);
}

.stage-main-btn {
  margin-top: 10px;
}

.trail-panel {
  margin-top: 12px;
}

.dialog-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

:deep(.current-version-row) {
  background: #f0f9eb;
}

@media (max-width: 1200px) {
  .auth-grid,
  .handover-grid {
    grid-template-columns: 1fr;
  }
}
</style>
