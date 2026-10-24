{
  path: '${moduleName}',
  name: '${className}',
  component: () => import('@/views/${moduleName}/index.vue'),
  meta: { title: '${comment}' }
},
<#if genPrint>
{
  // V4.5 打印单据：无 layout 顶层路由，打印页必须脱离后台导航壳，否则打出来带侧边栏
  path: '/print/${moduleName}/:id',
  name: 'Print${className}',
  component: () => import('@/views/${moduleName}/print.vue'),
  meta: { title: '${comment}打印' }
},
</#if>
