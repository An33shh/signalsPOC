<template>
  <div class="page">
    <!-- Header -->
    <div class="page-header">
      <h1>Dashboard</h1>
      <p>Overview of your cross-platform sync status</p>
    </div>

    <!-- Stats Row -->
    <div class="section">
      <div class="stats">
        <div class="stat">
          <div class="stat-value">{{ stats.projects }}</div>
          <div class="stat-label">Projects</div>
        </div>
        <div class="stat">
          <div class="stat-value">{{ stats.tasks }}</div>
          <div class="stat-label">Tasks</div>
        </div>
        <div class="stat">
          <div class="stat-value">{{ stats.users }}</div>
          <div class="stat-label">Team</div>
        </div>
        <div class="stat" :class="{ 'stat-alert': stats.alerts > 0 }">
          <div class="stat-value">{{ stats.alerts }}</div>
          <div class="stat-label">Alerts</div>
        </div>
      </div>
    </div>

    <!-- Platforms -->
    <div class="section">
      <div class="section-header">
        <span class="section-title">Connected Platforms</span>
      </div>
      <div class="platforms-row">
        <div class="platform-card" :class="{ connected: platforms.asana }">
          <div class="platform-icon">A</div>
          <div class="platform-info">
            <div class="platform-name">Asana</div>
            <div class="platform-status">{{ platforms.asana ? 'Connected' : 'Offline' }}</div>
          </div>
          <div class="platform-dot" :class="{ active: platforms.asana }"></div>
        </div>
        <div class="platform-card" :class="{ connected: platforms.linear }">
          <div class="platform-icon">L</div>
          <div class="platform-info">
            <div class="platform-name">Linear</div>
            <div class="platform-status">{{ platforms.linear ? 'Connected' : 'Offline' }}</div>
          </div>
          <div class="platform-dot" :class="{ active: platforms.linear }"></div>
        </div>
        <div class="platform-card" :class="{ connected: platforms.github }">
          <div class="platform-icon github">G</div>
          <div class="platform-info">
            <div class="platform-name">GitHub</div>
            <div class="platform-status">{{ platforms.github ? 'Monitoring' : 'Offline' }}</div>
          </div>
          <div class="platform-dot" :class="{ active: platforms.github }"></div>
        </div>
      </div>
    </div>

    <!-- Recent Alerts -->
    <div v-if="recentAlerts.length > 0" class="section">
      <div class="section-header">
        <span class="section-title">Recent Alerts</span>
        <router-link to="/alerts" class="text-sm text-secondary">View all</router-link>
      </div>
      <div class="alerts-grid">
        <div v-for="alert in recentAlerts" :key="alert.id" class="alert-item" :class="alert.severity?.toLowerCase()">
          <div class="alert-top">
            <span class="tag" :class="severityClass(alert.severity)">{{ alert.severity }}</span>
            <span class="text-xs text-muted">{{ formatTime(alert.createdAt) }}</span>
          </div>
          <div class="alert-body">{{ alert.title }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { projectsApi, tasksApi, usersApi, alertsApi, syncApi } from '../api'

const stats = ref({ projects: 0, tasks: 0, users: 0, alerts: 0 })
const recentAlerts = ref([])
const platforms = ref({ asana: false, linear: false, github: false })

const severityClass = (s) => {
  if (s === 'CRITICAL') return 'tag-error'
  if (s === 'WARNING') return 'tag-warning'
  return 'tag-outline'
}

const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 60) return `${mins}m`
  if (hours < 24) return `${hours}h`
  return `${days}d`
}

onMounted(async () => {
  try {
    const [projects, tasks, users, count, alerts] = await Promise.all([
      projectsApi.getAll({ size: 1 }),
      tasksApi.getAll({ size: 1 }),
      usersApi.getAll({ size: 1 }),
      alertsApi.getUnreadCount(),
      alertsApi.getUnread({ size: 3 })
    ])
    stats.value = {
      projects: projects.data.totalElements || 0,
      tasks: tasks.data.totalElements || 0,
      users: users.data.totalElements || 0,
      alerts: count.data.count || 0
    }
    recentAlerts.value = alerts.data.content || []

    const connectors = ['asana', 'linear', 'github']
    const results = await Promise.allSettled(connectors.map(c => syncApi.testConnection(c)))
    connectors.forEach((c, i) => {
      platforms.value[c] = results[i].status === 'fulfilled' && results[i].value.data.connected
    })
  } catch (e) {
    console.error(e)
  }
})
</script>

<style scoped>
.page {
  padding: var(--space-8) var(--space-8);
  max-width: 1200px;
}

.page-header {
  margin-bottom: var(--space-8);
}

.page-header h1 {
  margin-bottom: var(--space-1);
}

.page-header p {
  color: var(--text-muted);
  font-size: 15px;
}

/* Stats */
.stat-alert {
  border-color: rgba(239, 68, 68, 0.3);
}

.stat-alert .stat-value { color: var(--error); }

/* Platforms */
.platforms-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.platform-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  transition: all var(--duration) var(--ease);
}

.platform-card.connected {
  border-color: rgba(34, 197, 94, 0.2);
}

.platform-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gray-800);
  color: var(--white);
  font-size: 16px;
  font-weight: 700;
  border-radius: var(--radius);
  flex-shrink: 0;
}

.platform-icon.github { background: #24292e; }

.platform-info { flex: 1; }

.platform-name {
  font-weight: 500;
  font-size: 14px;
  color: var(--text);
}

.platform-status {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.platform-card.connected .platform-status {
  color: var(--success);
}

.platform-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gray-600);
  flex-shrink: 0;
}

.platform-dot.active {
  background: var(--success);
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.4);
}

/* Alerts */
.alerts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-4);
}

.alert-item {
  padding: var(--space-4);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  border-left: 3px solid var(--border);
  transition: all var(--duration) var(--ease);
}

.alert-item:hover { border-color: var(--border-strong); }
.alert-item.critical { border-left-color: var(--error); }
.alert-item.warning { border-left-color: var(--warning); }

.alert-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.alert-body {
  font-size: 14px;
  color: var(--text);
  line-height: 1.5;
}
</style>
