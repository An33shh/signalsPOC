<template>
  <div class="page">
    <div class="page-header">
      <h1>Sync</h1>
      <p>Manage data synchronization across platforms</p>
    </div>

    <!-- Connectors -->
    <div class="section">
      <div class="section-header">
        <span class="section-title">Connectors</span>
      </div>
      <div class="connectors-grid">
        <div v-for="c in connectors" :key="c.key" class="connector-card">
          <div class="connector-top">
            <div class="connector-icon" :class="c.iconClass">{{ c.icon }}</div>
            <div class="connector-info">
              <div class="connector-name">{{ c.name }}</div>
              <div class="connector-desc">{{ c.desc }}</div>
            </div>
          </div>
          <div v-if="status[c.key]" class="connector-status" :class="status[c.key].type">
            {{ status[c.key].msg }}
          </div>
          <div class="connector-actions">
            <button class="btn btn-sm" @click="test(c.key)" :disabled="testing[c.key]">
              {{ testing[c.key] ? 'Testing...' : 'Test' }}
            </button>
            <button class="btn btn-sm btn-primary" @click="sync(c.key)" :disabled="syncing[c.key]">
              {{ syncing[c.key] ? 'Syncing...' : 'Sync All' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Recent Syncs -->
    <div class="section">
      <div class="section-header">
        <span class="section-title">Recent Syncs</span>
        <button @click="loadLogs" class="btn btn-sm btn-ghost">Refresh</button>
      </div>

      <div v-if="logsLoading" class="loading">Loading...</div>
      <div v-else-if="logs.length === 0" class="empty"><p>No sync history</p></div>

      <div v-else class="logs-table">
        <div v-for="log in logs" :key="log.id" class="log-row">
          <div class="log-left">
            <span class="tag tag-outline">{{ log.connectorType }}</span>
            <span class="log-status" :class="log.status?.toLowerCase()">{{ log.status }}</span>
          </div>
          <div class="log-right">
            <span>{{ (log.projectsSynced || 0) + (log.tasksSynced || 0) + (log.usersSynced || 0) + (log.commentsSynced || 0) }} records</span>
            <span class="text-muted">{{ formatTime(log.startTime) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { syncApi } from '../api'

const connectors = [
  { key: 'asana', name: 'Asana', icon: 'A', iconClass: '', desc: 'Projects, tasks, users, comments' },
  { key: 'linear', name: 'Linear', icon: 'L', iconClass: '', desc: 'Issues, projects, team members' },
  { key: 'github', name: 'GitHub', icon: 'G', iconClass: 'github', desc: 'Repositories, pull requests, monitoring' }
]

const testing = ref({ asana: false, linear: false, github: false })
const syncing = ref({ asana: false, linear: false, github: false })
const status = ref({ asana: null, linear: null, github: null })
const logs = ref([])
const logsLoading = ref(true)

const test = async (c) => {
  testing.value[c] = true
  status.value[c] = null
  try {
    const res = await syncApi.testConnection(c)
    status.value[c] = {
      type: res.data.connected ? 'success' : 'error',
      msg: res.data.connected ? 'Connected' : 'Failed'
    }
  } catch (e) {
    status.value[c] = { type: 'error', msg: e.response?.data?.message || 'Failed' }
  }
  testing.value[c] = false
}

const sync = async (c) => {
  syncing.value[c] = true
  status.value[c] = { type: 'info', msg: 'Syncing...' }
  try {
    await syncApi.syncAll(c)
    status.value[c] = { type: 'success', msg: 'Completed' }
    loadLogs()
  } catch (e) {
    status.value[c] = { type: 'error', msg: e.response?.data?.message || 'Failed' }
  }
  syncing.value[c] = false
}

const loadLogs = async () => {
  logsLoading.value = true
  try {
    const res = await syncApi.getLogs({ page: 0, size: 10, sort: 'startTime,desc' })
    logs.value = res.data.content || []
  } catch (e) {
    console.error(e)
  }
  logsLoading.value = false
}

const formatTime = (d) => d ? new Date(d).toLocaleString() : ''

onMounted(loadLogs)
</script>

<style scoped>
.page { padding: var(--space-8); max-width: 1200px; }

.page-header {
  margin-bottom: var(--space-8);
}
.page-header h1 { margin-bottom: var(--space-1); }
.page-header p { color: var(--text-muted); font-size: 15px; }

.connectors-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-4);
}

.connector-card {
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.connector-top {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.connector-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gray-800);
  color: var(--white);
  font-size: 18px;
  font-weight: 700;
  border-radius: var(--radius);
  flex-shrink: 0;
}

.connector-icon.github { background: #24292e; }

.connector-name { font-weight: 500; color: var(--text); }
.connector-desc { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.connector-status {
  padding: var(--space-2) var(--space-3);
  font-size: 13px;
  border-radius: var(--radius);
  margin-bottom: var(--space-3);
}

.connector-status.success { background: var(--success-dim); color: var(--success); }
.connector-status.error { background: var(--error-dim); color: var(--error); }
.connector-status.info { background: rgba(255,255,255,0.04); color: var(--text-secondary); }

.connector-actions {
  display: flex;
  gap: var(--space-2);
}

/* Logs */
.logs-table {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.log-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border);
  transition: background var(--duration) var(--ease);
}

.log-row:last-child { border-bottom: none; }
.log-row:hover { background: var(--bg-surface-hover); }

.log-left { display: flex; align-items: center; gap: var(--space-2); }

.log-status { font-size: 13px; font-weight: 500; }
.log-status.success { color: var(--success); }
.log-status.failed { color: var(--error); }

.log-right {
  display: flex;
  gap: var(--space-4);
  font-size: 12px;
  color: var(--text-muted);
}
</style>
