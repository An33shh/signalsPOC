<template>
  <div class="page">
    <div class="page-header">
      <h1>Alerts</h1>
      <div class="tabs">
        <button class="tab-btn" :class="{ active: filter === 'unread' }" @click="filter = 'unread'">
          Unread
          <span v-if="unreadCount > 0" class="tab-count">{{ unreadCount }}</span>
        </button>
        <button class="tab-btn" :class="{ active: filter === 'all' }" @click="filter = 'all'">All</button>
      </div>
    </div>

    <div v-if="loading" class="loading">Loading...</div>

    <div v-else-if="items.length === 0" class="empty">
      <p>No alerts</p>
      <p class="text-sm text-muted mt-1">Alerts appear when sync issues are detected</p>
    </div>

    <div v-else class="alert-list">
      <div v-for="alert in items" :key="alert.id" class="alert-card" :class="[alert.severity?.toLowerCase(), { unread: !alert.read }]">
        <div class="alert-header">
          <span class="tag" :class="severityTag(alert.severity)">{{ alert.severity }}</span>
          <span class="text-xs text-muted">{{ formatTime(alert.createdAt) }}</span>
        </div>

        <h3 class="alert-title">{{ alert.title }}</h3>
        <p class="alert-message">{{ alert.message }}</p>

        <div v-if="alert.aiSuggestion" class="alert-suggestion">
          <span class="suggestion-label">AI Suggestion</span>
          {{ alert.aiSuggestion }}
        </div>

        <div v-if="alert.sourceUrl" class="alert-link">
          <a :href="alert.sourceUrl" target="_blank">View on {{ alert.sourceSystem }}</a>
        </div>

        <div class="alert-actions">
          <button v-if="!alert.read" class="btn btn-sm" @click="markRead(alert)">Mark read</button>
          <button v-if="isActionable(alert)" class="btn btn-sm btn-approve" :disabled="approving[alert.id]" @click="showApproveConfirm(alert)">
            {{ approving[alert.id] ? 'Executing...' : 'Approve Action' }}
          </button>
          <button class="btn btn-sm" @click="resolve(alert)">Resolve</button>
        </div>
      </div>
    </div>

    <!-- Confirm Modal -->
    <Transition name="fade">
      <div v-if="confirmAlert" class="modal-overlay" @click.self="confirmAlert = null">
        <div class="modal">
          <h3>Approve Action</h3>
          <p class="modal-text">{{ confirmAlert.aiSuggestion }}</p>
          <p class="modal-sub">This will automatically update the linked platform.</p>
          <div class="modal-actions">
            <button class="btn btn-sm" @click="confirmAlert = null">Cancel</button>
            <button class="btn btn-sm btn-primary" @click="approve(confirmAlert)">Confirm</button>
          </div>
        </div>
      </div>
    </Transition>

    <div v-if="totalPages > 1" class="pagination">
      <button class="btn btn-sm" :disabled="page === 0" @click="page--; load()">Previous</button>
      <span class="text-sm text-muted">{{ page + 1 }} / {{ totalPages }}</span>
      <button class="btn btn-sm" :disabled="page >= totalPages - 1" @click="page++; load()">Next</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { alertsApi } from '../api'

const items = ref([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const filter = ref('unread')
const unreadCount = ref(0)
const approving = ref({})
const confirmAlert = ref(null)

const severityTag = (s) => {
  if (s === 'CRITICAL') return 'tag-error'
  if (s === 'WARNING') return 'tag-warning'
  return 'tag-outline'
}

const isActionable = (alert) => {
  const actionableTypes = ['PR_MERGED_TASK_OPEN', 'PR_READY_TASK_NOT_UPDATED', 'STALE_PR']
  return actionableTypes.includes(alert.alertType) && alert.targetSystem
}

const formatTime = (d) => {
  if (!d) return ''
  const diff = Date.now() - new Date(d).getTime()
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 60) return `${mins}m ago`
  if (hours < 24) return `${hours}h ago`
  return `${days}d ago`
}

const load = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    const res = filter.value === 'unread'
      ? await alertsApi.getUnread(params)
      : await alertsApi.getAll(params)
    items.value = res.data.content || []
    totalPages.value = res.data.totalPages || 0
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

const loadCount = async () => {
  try {
    const res = await alertsApi.getUnreadCount()
    unreadCount.value = res.data.count || 0
  } catch (e) { console.warn('Failed to load unread count', e) }
}

const markRead = async (alert) => {
  try {
    await alertsApi.markAsRead(alert.id)
    alert.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (e) { console.error(e) }
}

const resolve = async (alert) => {
  try {
    await alertsApi.resolve(alert.id)
    items.value = items.value.filter(a => a.id !== alert.id)
    loadCount()
  } catch (e) { console.error(e) }
}

const showApproveConfirm = (alert) => { confirmAlert.value = alert }

const approve = async (alert) => {
  confirmAlert.value = null
  approving.value[alert.id] = true
  try {
    const res = await alertsApi.approve(alert.id)
    if (res.data.success) {
      items.value = items.value.filter(a => a.id !== alert.id)
      loadCount()
    }
  } catch (e) { console.error(e) }
  approving.value[alert.id] = false
}

watch(filter, () => { page.value = 0; load() })

onMounted(() => { load(); loadCount() })
</script>

<style scoped>
.page { padding: var(--space-8); max-width: 1200px; }

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
}

.tabs {
  display: flex;
  gap: 2px;
  padding: 3px;
  background: rgba(255,255,255,0.04);
  border-radius: var(--radius);
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 32px;
  padding: 0 var(--space-4);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all var(--duration) var(--ease);
}

.tab-btn.active {
  color: var(--white);
  background: rgba(255,255,255,0.08);
}

.tab-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  background: var(--error);
  color: var(--white);
  border-radius: var(--radius-full);
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.alert-card {
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  border-left: 3px solid var(--border);
  transition: all var(--duration) var(--ease);
}

.alert-card:hover { border-color: var(--border-strong); }
.alert-card.unread { border-left-color: var(--accent); }
.alert-card.critical { border-left-color: var(--error); }
.alert-card.warning { border-left-color: var(--warning); }

.alert-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.alert-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--white);
  margin-bottom: var(--space-2);
}

.alert-message {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: var(--space-4);
}

.alert-suggestion {
  font-size: 13px;
  padding: var(--space-3) var(--space-4);
  background: rgba(59, 130, 246, 0.06);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: var(--radius);
  margin-bottom: var(--space-4);
  line-height: 1.5;
  color: var(--text-secondary);
}

.suggestion-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--accent);
  margin-bottom: var(--space-2);
}

.alert-link {
  margin-bottom: var(--space-4);
}

.alert-link a {
  font-size: 13px;
  color: var(--accent);
  transition: opacity var(--duration) var(--ease);
}

.alert-link a:hover { opacity: 0.8; }

.alert-actions {
  display: flex;
  gap: var(--space-2);
}

.btn-approve {
  background: var(--success);
  color: var(--white);
  border-color: var(--success);
}
.btn-approve:hover {
  background: #16a34a;
  border-color: #16a34a;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  backdrop-filter: blur(4px);
}

.modal {
  background: var(--bg-elevated);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-xl);
  padding: var(--space-8);
  max-width: 440px;
  width: 90%;
}

.modal h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--white);
  margin-bottom: var(--space-4);
}

.modal-text {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: var(--space-2);
}

.modal-sub {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: var(--space-6);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.fade-enter-active, .fade-leave-active { transition: opacity 200ms var(--ease); }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
