<template>
  <div class="page">
    <div class="page-header">
      <h1>Projects</h1>
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
      <p>No projects found</p>
      <router-link to="/sync" class="btn btn-sm mt-4">Sync data</router-link>
    </div>

    <div v-else class="grid">
      <div v-for="item in items" :key="item.id" class="card project-card">
        <div class="project-name">{{ item.name }}</div>
        <div class="project-meta">
          <span class="tag tag-outline">{{ item.sourceSystem }}</span>
          <span v-if="item.status" class="text-muted text-xs">{{ item.status }}</span>
        </div>
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
import { projectsApi } from '../api'

const items = ref([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const source = ref('')

const load = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (source.value) params.sourceSystem = source.value
    const res = await projectsApi.getAll(params)
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

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}

.project-card {
  padding: var(--space-5);
  cursor: default;
}

.project-name {
  font-weight: 500;
  font-size: 15px;
  color: var(--text);
  margin-bottom: var(--space-3);
}

.project-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
</style>
