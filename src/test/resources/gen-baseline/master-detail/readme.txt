代码生成器产物使用说明
======================

1. 后端文件：按包内相对路径合并到对应 shop-service-* 模块的
   src/main/java/<package>/ 目录下（entity/mapper/service/dto/vo/controller）。
2. 前端文件：src/api/*.js 与 src/views/*/index.vue 直接放入
   CrossShopFrontPage 对应目录。
3. 路由注册：打开 router-snippet.txt，把片段追加到
   src/router/index.js 对应菜单的 children 中（手动合并）。
4. 后端如使用新模块，确认该模块已配置 MybatisPlusConfig
   （PaginationInnerInterceptor），否则分页 total 恒为 0。
