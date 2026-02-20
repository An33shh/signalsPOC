<template>
  <div class="page">
    <div class="page-header">
      <h1>Team</h1>
      <div class="filter-bar">
        <select v-model="source" @change="page = 0; load()">
          <option value="">All sources</option>
          <option value="ASANA">Asana</option>
          <option value="LINEAR">Linear</option>
        </select>
      </div>
    </div>

    <div v-if="loading" class="loading">Loading...</div>

    <div v-else-if="items.length === 0" class="empty">
      <p>No team members found</p>
      <router-link to="/sync" class="btn btn-sm mt-4">Sync data</router-link>
    </div>

    <div v-else class="user-grid">
      <div v-for="item in items" :key="item.id" class="card user-card">
        <div class="user-avatar">{{ initials(item.name) }}</div>
        <div class="user-info">
          <div class="user-name">{{ item.name }}</div>
          <div class="user-email">{{ item.email || 'No email' }}</div>
        </div>
        <span class="tag tag-outline">{{ item.sourceSystem }}</span>
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
import { usersApi } from '../api'

const items = ref([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const source = ref('')

const initials = (name) => {
  if (!name) return '?'
  return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
}

const load = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (source.value) params.sourceSystem = source.value
    const res = await usersApi.getAll(params)
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

.filter-bar select {
  width: auto;
  height: 36px;
  padding: 0 var(--space-3);
  font-size: 13px;
}

.user-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
}

.user-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
}

.user-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent);
  color: var(--white);
  font-size: 14px;
  font-weight: 600;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.user-info { flex: 1; min-width: 0; }

.user-name {
  font-weight: 500;
  color: var(--text);
  font-size: 14px;
}

.user-email {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}
</style>
