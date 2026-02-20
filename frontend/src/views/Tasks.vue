<template>
  <div class="page">
    <div class="page-header">
      <h1>Tasks</h1>
      <div class="filter-bar">
        <select v-model="source" @change="page = 0; load()">
          <option value="">All sources</option>
          <option value="ASANA">Asana</option>
          <option value="LINEAR">Linear</option>
        </select>
        <select v-model="status" @change="page = 0; load()">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="COMPLETED">Done</option>
        </select>
      </div>
    </div>

    <div v-if="loading" class="loading">Loading...</div>

    <div v-else-if="items.length === 0" class="empty">
      <p>No tasks found</p>
      <router-link to="/sync" class="btn btn-sm mt-4">Sync data</router-link>
    </div>

    <div v-else class="task-list">
      <div v-for="item in items" :key="item.id" class="task-row">
        <div class="task-info">
          <div class="task-title">{{ item.title }}</div>
          <div class="task-meta">
            <span class="tag tag-outline">{{ item.sourceSystem }}</span>
            <span v-if="item.priority" class="tag" :class="priorityTag(item.priority)">{{ item.priority }}</span>
          </div>
        </div>
        <span v-if="item.status" class="task-status">{{ item.status }}</span>
      </div>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button class="btn btn-sm" :disabled="page === 0" @click="page--; load()">Previous</button>
      <span class="text-sm text-muted">{{ page + 1 }} / {{ totalPages }}</span>
      <button class="btn btn-sm" :disabled="page >= totalPages - 1" @click="page++; load()">Next</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { tasksApi } from '../api'

const items = ref([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const source = ref('')
const status = ref('')

const priorityTag = (p) => {
  const pr = p?.toUpperCase()
  if (pr === 'CRITICAL' || pr === 'HIGH') return 'tag-error'
  if (pr === 'MEDIUM') return 'tag-warning'
  return ''
}

const load = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (source.value) params.sourceSystem = source.value
    if (status.value) params.status = status.value
    const res = await tasksApi.getAll(params)
    items.value = res.data.content || []
    totalPages.value = res.data.totalPages || 0
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

onMounted(load)
</script>

<style scoped>
.page { padding: var(--space-8); max-width: 1200px; }

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
}

.filter-bar {
  display: flex;
  gap: var(--space-2);
}

.filter-bar select {
  width: auto;
  height: 36px;
  padding: 0 var(--space-3);
  font-size: 13px;
}

.task-list {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.task-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border);
  transition: background var(--duration) var(--ease);
}

.task-row:last-child { border-bottom: none; }
.task-row:hover { background: var(--bg-surface-hover); }

.task-info { flex: 1; min-width: 0; }

.task-title {
  font-weight: 500;
  color: var(--text);
  margin-bottom: var(--space-2);
}

.task-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.task-status {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-weight: 500;
  flex-shrink: 0;
  margin-left: var(--space-4);
}
</style>
