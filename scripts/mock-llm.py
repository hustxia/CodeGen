"""V7.3 P0-3 端到端验证用的假 LLM（OpenAI 兼容 /v1/chat/completions）。

只做一件事：从 user content 里解析出表名/列名，回一份**合法**的页面配置建议，
让「推荐 → 落盘 token → 预览 diff → 勾选写库」整条链路可以离线跑通。
不模拟任何 AI 判断能力，纯粹让接口契约可验证。
"""
import json
import re
from http.server import BaseHTTPRequestHandler, HTTPServer

TABLE_RE = re.compile(r'"tableName":"([^"]+)"')
COL_RE = re.compile(r'"name":"([^"]+)"')


def build_suggestion(text: str) -> dict:
    tables = []
    # 按表切块，块内取列名（顺序与 prompt 一致：tableName 先出现）
    chunks = text.split('{"tableName":"')[1:]
    for chunk in chunks:
        table_name = chunk.split('"', 1)[0]
        columns = []
        for name in COL_RE.findall(chunk):
            if name in ('id', 'create_time', 'update_time', 'create_by', 'update_by', 'deleted'):
                continue
            columns.append({
                "columnName": name,
                "isQuery": 1,
                "queryType": "LIKE",
                "isList": 1,
                "isForm": 1,
                "htmlType": "input",
                "sort": len(columns) + 1,
            })
        tables.append({
            "tableName": table_name,
            "moduleName": "mockStock",
            "treeParentColumn": "",
            "treeLabelColumn": "",
            "masterTableName": "",
            "relationType": "",
            "columns": columns,
        })
    return {"tables": tables}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length') or 0)
        body = json.loads(self.rfile.read(length) or b'{}')
        text = ''
        for msg in body.get('messages') or []:
            if msg.get('role') == 'user':
                text = msg.get('content') or ''
        content = json.dumps(build_suggestion(text), ensure_ascii=False)
        payload = json.dumps({
            "id": "mock",
            "object": "chat.completion",
            "choices": [{"index": 0, "message": {"role": "assistant", "content": content},
                         "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
        }, ensure_ascii=False).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


if __name__ == '__main__':
    HTTPServer(('127.0.0.1', 8899), Handler).serve_forever()
