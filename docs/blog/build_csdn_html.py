# -*- coding: utf-8 -*-
"""把博客 Markdown + 本地截图打包成 CSDN 可直接粘贴的 HTML（图片 base64 内嵌）。"""
import base64
import re
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent
MD = ROOT / "智慧村务管理系统实战.md"
OUT = ROOT / "智慧村务管理系统实战-CSDN粘贴版.html"

text = MD.read_text(encoding="utf-8")
img_dir = ROOT / "images"


def inline_image(match):
    alt = match.group(1)
    rel = match.group(2)
    path = img_dir / Path(rel).name
    if not path.exists():
        print("WARN missing image:", rel)
        return match.group(0)
    mime = "image/png" if path.suffix.lower() == ".png" else "image/jpeg"
    b64 = base64.b64encode(path.read_bytes()).decode("ascii")
    return f'<img src="data:{mime};base64,{b64}" alt="{alt}" style="max-width:100%;height:auto;" />'


text = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", inline_image, text)

html_body = markdown.markdown(
    text,
    extensions=["tables", "fenced_code", "codehilite", "toc", "sane_lists"],
)

doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<title>智慧村务管理系统实战</title>
<style>
body {{ font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
       font-size: 15px; line-height: 1.85; color: #333; max-width: 860px; margin: 0 auto; padding: 24px; }}
h1 {{ font-size: 26px; border-bottom: 2px solid #4f46e5; padding-bottom: 10px; }}
h2 {{ font-size: 21px; margin-top: 34px; color: #2c3e50; border-left: 4px solid #4f46e5; padding-left: 10px; }}
h3 {{ font-size: 17px; color: #34495e; }}
img {{ border-radius: 6px; box-shadow: 0 2px 10px rgba(0,0,0,.08); margin: 12px 0; }}
blockquote {{ background: #f6f8ff; border-left: 4px solid #818cf8; padding: 10px 16px; color: #555; margin: 16px 0; }}
code {{ background: #f4f4f5; padding: 2px 6px; border-radius: 4px; color: #c7254e; font-size: 13px; }}
pre {{ background: #1e1e2f; color: #e8e8f0; padding: 16px; border-radius: 8px; overflow-x: auto; }}
pre code {{ background: transparent; color: inherit; padding: 0; }}
table {{ border-collapse: collapse; width: 100%; margin: 16px 0; }}
th, td {{ border: 1px solid #e2e8f0; padding: 8px 12px; text-align: left; }}
th {{ background: #eef2ff; }}
.tip {{ background: #fffbeb; border: 1px solid #fde68a; padding: 12px 16px; border-radius: 8px; font-size: 13px; }}
</style>
</head>
<body>
<div class="tip">📋 使用说明：全选本页内容（Ctrl+A）→ 复制（Ctrl+C）→ 粘贴到 CSDN 文章编辑器。图片为 base64 内嵌，粘贴后会自动转存到 CSDN 图床；若未自动转存，请改用 Markdown 版并手动上传 images 目录下的截图。</div>
{html_body}
</body>
</html>
"""

OUT.write_text(doc, encoding="utf-8")
print("生成完成:", OUT)
print("HTML 大小:", round(OUT.stat().st_size / 1024 / 1024, 2), "MB")
print("内嵌图片数:", doc.count("data:image"))
