<template>
  <div class="app-layout">
    <aside class="app-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
      <div class="sidebar-header">
        <span v-if="!appStore.sidebarCollapsed" class="logo-text">DB Manager</span>
        <span v-else class="logo-icon">DB</span>
      </div>
      <nav class="nav-menu">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span v-if="!appStore.sidebarCollapsed" class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>
    <div class="app-main">
      <header class="app-header">
        <button class="btn btn-default btn-sm collapse-btn" @click="appStore.toggleSidebar">
          {{ appStore.sidebarCollapsed ? '☰' : '✕' }}
        </button>
        <div class="header-connection">
          <select
            v-if="connectionStore.currentConnection"
            class="connection-selector"
            :value="connectionStore.currentConnection.id"
            @change="onConnectionChange"
          >
            <option
              v-for="conn in connectionStore.connections"
              :key="conn.id"
              :value="conn.id"
            >
              {{ conn.name }}
            </option>
          </select>
          <span v-else class="no-connection">未选择连接</span>
        </div>
        <div class="header-spacer"></div>
        <div class="header-actions">
          <span class="header-user">用户</span>
        </div>
      </header>
      <main class="app-content">
        <router-view />
      </main>
    </div>
    <ToastContainer />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { useConnectionStore } from '@/stores/connection'
import ToastContainer from '@/components/common/ToastContainer.vue'

const appStore = useAppStore()
const connectionStore = useConnectionStore()

const menuItems = [
  { path: '/connections', label: '连接管理', icon: '🔗' },
  { path: '/query', label: 'SQL查询', icon: '📝' },
  { path: '/saved', label: '收藏查询', icon: '⭐' },
  { path: '/history', label: '查询历史', icon: '🕐' }
]

onMounted(() => {
  connectionStore.fetchConnections().catch(() => {})
  connectionStore.fetchGroups().catch(() => {})
})

function onConnectionChange(e: Event) {
  const target = e.target as HTMLSelectElement
  const id = Number(target.value)
  const conn = connectionStore.connections.find((c) => c.id === id) || null
  connectionStore.setCurrentConnection(conn)
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.sidebar-header {
  height: $header-height;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);

  .logo-text {
    font-size: 18px;
    font-weight: 700;
    color: #fff;
    letter-spacing: 1px;
  }

  .logo-icon {
    font-size: 20px;
    font-weight: 700;
    color: $primary-color;
  }
}

.app-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  min-width: 36px;
}

.header-connection {
  display: flex;
  align-items: center;
}

.connection-selector {
  padding: 6px 12px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  background: #fff;
  font-size: 14px;
  min-width: 200px;
}

.no-connection {
  color: $text-light;
  font-size: 13px;
}

.header-spacer {
  flex: 1;
}

.header-user {
  color: $text-secondary;
  font-size: 13px;
}
</style>
