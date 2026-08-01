import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const Layout = () => import('@/components/layout/Layout.vue')
const ConnectionView = () => import('@/views/ConnectionView.vue')
const QueryView = () => import('@/views/QueryView.vue')
const TableDataView = () => import('@/views/TableDataView.vue')
const SavedQueriesView = () => import('@/views/SavedQueriesView.vue')
const HistoryView = () => import('@/views/HistoryView.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/connections',
    children: [
      {
        path: 'connections',
        name: 'Connections',
        component: ConnectionView,
        meta: { title: '连接管理' }
      },
      {
        path: 'query',
        name: 'Query',
        component: QueryView,
        meta: { title: 'SQL查询' }
      },
      {
        path: 'query/:connectionId',
        name: 'QueryWithConnection',
        component: QueryView,
        meta: { title: 'SQL查询' }
      },
      {
        path: 'table/:connectionId/:dbName/:tableName',
        name: 'TableData',
        component: TableDataView,
        meta: { title: '表数据' }
      },
      {
        path: 'saved',
        name: 'SavedQueries',
        component: SavedQueriesView,
        meta: { title: '收藏查询' }
      },
      {
        path: 'history',
        name: 'History',
        component: HistoryView,
        meta: { title: '查询历史' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const title = to.meta.title as string
  if (title) {
    document.title = `${title} - 数据库可视化管理工具`
  }
  next()
})

export default router
