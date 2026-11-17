import json, urllib.request, time
B='http://127.0.0.1:8120'
def c(p,b=None,t=None):
    d=json.dumps(b,ensure_ascii=False).encode() if b is not None else None
    r=urllib.request.Request(B+p,data=d,method='POST' if d else 'GET')
    r.add_header('Content-Type','application/json; charset=utf-8')
    if t: r.add_header('Authorization','Bearer '+t)
    with urllib.request.urlopen(r,timeout=30) as x: return json.loads(x.read().decode())
t=c('/auth/login',{'username':'admin','password':'admin123'})['data']['token']
pid=json.load(open('village-pipeline-id.json',encoding='utf-8'))['pipelineId']
last=None
for i in range(90):
    d=c('/gen/pipeline/'+str(pid),t=t)['data']
    st=d.get('status')
    steps=d.get('steps') or []
    done=[s.get('stepKey') for s in steps if s.get('status') in ('success','failed','failed-degraded')]
    sig=(st,tuple(done))
    if sig!=last:
        print(f'[{time.strftime("%H:%M:%S")}] {st} | 完成步骤: {",".join(done) or "-"} | bePid={d.get("backendPid")} fePid={d.get("frontendPid")}',flush=True)
        if d.get('failReason'): print('  FAIL:',d['failReason'][:400],flush=True)
        last=sig
    if st in ('success','failed','stopped'):
        print('=== FINAL:',st,'===',flush=True)
        print('backendUrl=',d.get('backendUrl'),'frontendUrl=',d.get('frontendUrl'),flush=True)
        if d.get('failReason'): print('failReason=',d['failReason'],flush=True)
        break
    time.sleep(6)
