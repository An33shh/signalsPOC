<template>
  <div class="login">
    <div class="login-content">
      <div class="login-brand">
        <div class="brand-mark">S</div>
        <h1>Signals</h1>
        <p>Cross-platform sync monitoring</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="field">
          <label for="username" class="sr-only">Username</label>
          <input id="username" v-model="username" type="text" placeholder="Username" autocomplete="username" required />
        </div>

        <div class="field">
          <label for="password" class="sr-only">Password</label>
          <input id="password" v-model="password" type="password" placeholder="Password" autocomplete="current-password" required />
        </div>

        <Transition name="shake">
          <p v-if="error" class="error-msg">{{ error }}</p>
        </Transition>

        <button type="submit" class="btn btn-primary w-full login-btn" :disabled="loading">
          {{ loading ? 'Signing in...' : 'Sign in' }}
        </button>
      </form>

      <p class="login-hint">Use <strong>admin</strong> / <strong>admin123</strong></p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

const handleLogin = async () => {
  loading.value = true
  error.value = ''
  const result = await authStore.login(username.value, password.value)
  if (result.success) {
    router.push('/')
  } else {
    error.value = result.error
  }
  loading.value = false
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  background: var(--bg);
}

.login-content {
  width: 100%;
  max-width: 360px;
}

.login-brand {
  text-align: center;
  margin-bottom: var(--space-10);
}

.brand-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto var(--space-5);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--white);
  color: var(--black);
  font-size: 24px;
  font-weight: 700;
  border-radius: var(--radius-lg);
}

.login-brand h1 {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.04em;
  margin-bottom: var(--space-2);
}

.login-brand p {
  font-size: 15px;
  color: var(--text-muted);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.field input {
  height: 44px;
  background: var(--bg-surface);
  border-color: var(--border-strong);
  font-size: 15px;
}

.field input:focus {
  border-color: var(--accent);
}

.error-msg {
  text-align: center;
  font-size: 14px;
  color: var(--error);
}

.login-btn {
  height: 44px;
  font-size: 15px;
  margin-top: var(--space-2);
}

.login-hint {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  margin-top: var(--space-8);
}

.login-hint strong {
  color: var(--text-secondary);
  font-weight: 500;
}

.shake-enter-active { animation: shake 300ms ease; }

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}
</style>
