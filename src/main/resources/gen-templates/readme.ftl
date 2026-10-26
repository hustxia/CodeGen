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
5. 打印单据：<#if (genPrint)!false>本次产物含 src/views/*/print.vue（A4 打印页，
   路由为无 layout 顶层路由 /print/<模块>/:id，已在 router-snippet.txt 里），
   列表操作列有「打印」按钮，浏览器打印或另存为 PDF 均可。
   <#else>本次产物不含打印页（表配置 gen_print 未开启）。</#if>
6. 明细打印列超过 8 列时自动精简到前 8 列；如需全部列打印，
   在表配置里把非关键列的「列表显示」关掉后重新生成。
