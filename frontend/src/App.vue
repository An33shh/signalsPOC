<template>
  <div class="app">
    <!-- Sidebar -->
    <aside v-if="authStore.isAuthenticated" class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-mark">S</div>
        <span class="brand-text">Signals</span>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="currentColor"><path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z"/></svg>
          <span>Dashboard</span>
        </router-link>
        <router-link to="/projects" class="nav-item" :class="{ active: $route.path === '/projects' }">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M2 6a2 2 0 012-2h4l2 2h4a2 2 0 012 2v1H8a3 3 0 00-3 3v1.5a1.5 1.5 0 01-3 0V6z" clip-rule="evenodd"/><path d="M6 12a2 2 0 012-2h8a2 2 0 012 2v2a2 2 0 01-2 2H2h2a2 2 0 002-2v-2z"/></svg>
          <span>Projects</span>
        </router-link>
        <router-link to="/tasks" class="nav-item" :class="{ active: $route.path === '/tasks' }">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="currentColor"><path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z"/><path fill-rule="evenodd" d="M4 5a2 2 0 012-2 3 3 0 003 3h2a3 3 0 003-3 2 2 0 012 2v11a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm9.707 5.707a1 1 0 00-1.414-1.414L9 12.586l-1.293-1.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
          <span>Tasks</span>
        </router-link>
        <router-link to="/users" class="nav-item" :class="{ active: $route.path === '/users' }">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="currentColor"><path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z"/></svg>
          <span>Team</span>
        </router-link>

        <div class="nav-divider"></div>

        <router-link to="/sync" class="nav-item" :class="{ active: $route.path === '/sync' }">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z" clip-rule="evenodd"/></svg>
          <span>Sync</span>
        </router-link>
        <router-link to="/alerts" class="nav-item" :class="{ active: $route.path === '/alerts' }">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="currentColor"><path d="M10 2a6 6 0 00-6 6v3.586l-.707.707A1 1 0 004 14h12a1 1 0 00.707-1.707L16 11.586V8a6 6 0 00-6-6zM10 18a3 3 0 01-3-3h6a3 3 0 01-3 3z"/></svg>
          <span>Alerts</span>
          <span v-if="alertCount > 0" class="nav-badge">{{ alertCount }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button @click="logout" class="nav-item nav-logout">
          <svg class="nav-icon" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M3 3a1 1 0 00-1 1v12a1 1 0 102 0V4a1 1 0 00-1-1zm10.293 9.293a1 1 0 001.414 1.414l3-3a1 1 0 000-1.414l-3-3a1 1 0 10-1.414 1.414L14.586 9H7a1 1 0 100 2h7.586l-1.293 1.293z" clip-rule="evenodd"/></svg>
          <span>Sign out</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main :class="{ 'with-sidebar': authStore.isAuthenticated }">
      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { alertsApi } from './api'

const router = useRouter()
const authStore = useAuthStore()
const alertCount = ref(0)

const logout = () => {
  authStore.logout()
  router.push('/login')
}

const loadAlertCount = async () => {
  if (!authStore.isAuthenticated) return
  try {
    const res = await alertsApi.getUnreadCount()
    alertCount.value = res.data.count || 0
  } catch (e) {}
}

onMounted(() => {
  loadAlertCount()
  setInterval(loadAlertCount, 60000)
})
</script>

<style scoped>
.app {
  display: flex;
  min-height: 100vh;
  background: var(--bg);
}

/* Sidebar */
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  width: var(--sidebar-width);
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-right: 1px solid var(--border);
  z-index: 50;
  overflow-y: auto;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-5);
  margin-bottom: var(--space-2);
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--white);
  color: var(--black);
  font-size: 16px;
  font-weight: 700;
  border-radius: var(--radius);
  flex-shrink: 0;
}

.brand-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--white);
}

.sidebar-nav {
  flex: 1;
  padding: 0 var(--space-3);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  font-size: 14px;
  font-weight: 450;
  color: var(--text-secondary);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all var(--duration) var(--ease);
  text-decoration: none;
  border: none;
  background: none;
  width: 100%;
  text-align: left;
}

.nav-item:hover {
  color: var(--text);
  background: rgba(255,255,255,0.05);
}

.nav-item.active {
  color: var(--white);
  background: rgba(255,255,255,0.08);
}

.nav-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  opacity: 0.7;
}

.nav-item.active .nav-icon {
  opacity: 1;
}

.nav-badge {
  margin-left: auto;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  background: var(--error);
  color: var(--white);
  border-radius: var(--radius-full);
}

.nav-divider {
  height: 1px;
  background: var(--border);
  margin: var(--space-3) var(--space-3);
}

.sidebar-footer {
  padding: var(--space-3);
  border-top: 1px solid var(--border);
}

.nav-logout {
  color: var(--text-muted);
  font-family: inherit;
}

.nav-logout:hover {
  color: var(--error);
}

/* Main */
main {
  flex: 1;
  min-height: 100vh;
}

main.with-sidebar {
  margin-left: var(--sidebar-width);
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 150ms var(--ease);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
