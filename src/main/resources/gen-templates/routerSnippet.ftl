{
  path: '${moduleName}',
  name: '${className}',
  component: () => import('@/views/${moduleName}/index.vue'),
  meta: { title: '${comment}' }
},