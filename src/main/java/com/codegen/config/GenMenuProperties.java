package com.codegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 菜单出口配置。
 *
 * <p>旧工程里生成器住在目标工程内部，注册菜单 = 往自己的 sys_menu 插行 + 重建自己的路由文件；
 * 独立成平台后目标工程是外部系统，平台无权也不该往它的库里写数据，因此默认改为
 * 产出「可执行 SQL + 路由片段」两种工件，由目标工程自行执行。
 *
 * <ul>
 *   <li>{@code artifact}：只产出工件（默认，适配任意外部目标工程）</li>
 *   <li>{@code internal}：写入平台自身 sys_menu（仅当生成目标就是平台自己时使用）</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gen.menu")
public class GenMenuProperties {

    /**
     * 菜单出口：artifact / internal（direct 为 internal 的别名）
     */
    private String outlet = "artifact";

    /**
     * 工件输出目录（相对 backend-root），留空默认 .gen-artifacts
     */
    private String artifactDir = ".gen-artifacts";

    /**
     * 是否走「写入平台自身菜单表」的老路径。
     *
     * <p>{@code direct} 与 {@code internal} 等价：平台与目标共用同一个库时，
     * 直接写 sys_menu + sys_role_menu 并回写 gen_table.menu_id，省去手工执行 SQL。
     */
    public boolean isInternal() {
        return "internal".equalsIgnoreCase(outlet) || "direct".equalsIgnoreCase(outlet);
    }
}
