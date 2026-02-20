import axios from 'axios'

const API_URL = '/api/v1'

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Add JWT token to requests
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Handle 401 errors
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  login: (username, password) => api.post('/auth/login', { username, password })
}

export const projectsApi = {
  getAll: (params) => api.get('/projects', { params }),
  getById: (id) => api.get(`/projects/${id}`)
}

export const tasksApi = {
  getAll: (params) => api.get('/tasks', { params }),
  getById: (id) => api.get(`/tasks/${id}`),
  getByProject: (projectId) => api.get(`/tasks/project/${projectId}`)
}

export const usersApi = {
  getAll: (params) => api.get('/users', { params }),
  getById: (id) => api.get(`/users/${id}`)
}

export const commentsApi = {
  getAll: (params) => api.get('/comments', { params }),
  getByTask: (taskId) => api.get(`/comments/task/${taskId}`)
}

export const syncApi = {
  testConnection: (connector) => api.get(`/sync/${connector}/test`),
  syncAll: (connector) => api.post(`/sync/${connector}/all`),
  syncProjects: (connector) => api.post(`/sync/${connector}/projects`),
  syncTasks: (connector) => api.post(`/sync/${connector}/tasks`),
  syncUsers: (connector) => api.post(`/sync/${connector}/users`),
  syncComments: (connector) => api.post(`/sync/${connector}/comments`),
  getLogs: (params) => api.get('/sync/logs', { params })
}

export const alertsApi = {
  getAll: (params) => api.get('/alerts', { params }),
  getUnread: (params) => api.get('/alerts/unread', { params }),
  getUnreadCount: () => api.get('/alerts/count'),
  markAsRead: (id) => api.post(`/alerts/${id}/read`),
  resolve: (id) => api.post(`/alerts/${id}/resolve`),
  approve: (id) => api.post(`/alerts/${id}/approve`)
}

export default api
