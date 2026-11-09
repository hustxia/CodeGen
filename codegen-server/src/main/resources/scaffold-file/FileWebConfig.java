package __PKG__.common.file;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 图片静态直链：/uploads/** 直接映射上传目录，不走鉴权（图片 URL 可进 img 标签/浏览器直开）。
 * 目录取绝对路径（相对路径基于进程工作目录），末尾斜杠必须保留否则映射失效。
 */
@Configuration
public class FileWebConfig implements WebMvcConfigurer {

    @Autowired
    private FileProps props;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = Paths.get(props.getUploadDir()).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + dir + "/");
    }
}
