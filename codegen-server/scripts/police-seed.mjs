// 智慧警务系统演示数据生成器
//
// 背景：快速建表产出的 12 个模块共用通用词库，主表会写出「不锈钢板材」「综合管理示例1」，
// 与警务业务无关。本脚本按模块替换为贴合业务的取值（警情编号/报案人/受理单位/处置结果）。
//
// 用法：node scripts/police-seed.mjs [输出SQL路径] [库名]
//   node scripts/police-seed.mjs police_seed.sql police_affairs
// 执行：mysql -uroot -p --default-character-set=utf8mb4 -e "source police_seed.sql"
//
// 产物：每张主表/明细/条目/操作记录/统计 30 行，分类树 7 节点，参数配置 4 项。
// 注意：脚本会先 DELETE 对应表再 INSERT，仅用于演示环境。

const DOMAINS = [
  {
    key: 'v94_blkq2s', name: '基础数据',
    places: ['城东派出所辖区', '城西派出所辖区', '城南派出所辖区', '开发区派出所辖区', '火车站派出所辖区'],
    types: ['派出所警务室登记', '警用装备入库', '重点单位备案', '辖区人口信息核录', '监控点位维护', '警力资源配置'],
    results: ['已完成', '待审核', '审核通过', '已归档'],
    items: ['执法记录仪', '警用对讲机', '防刺服', '单警装备包', '警务通终端', '警戒带', '伸缩警棍'],
    actions: ['新增备案', '信息核录', '资料归档', '数据变更'],
    configs: [['辖区警务室数量', '26'], ['单警装备配备标准', '8件套'], ['监控点位在线率阈值', '95%'], ['人口信息更新周期', '7天']],
    cats: ['辖区管理', '装备管理', '基础台账', '派出所', '警务室', '重点单位', '警用装备'],
  },
  {
    key: 'v94_1a9iwwc', name: '接处警管理',
    places: ['阳光花园小区', '翠湖苑小区', '世纪广场', '火车站东广场', '第三中学门口', '人民医院急诊楼', '滨江大道', '城东工业园'],
    types: ['入室盗窃', '电动车被盗', '邻里纠纷', '噪音扰民', '老人走失', '醉酒滋事', '电信网络诈骗', '交通事故', '家庭暴力', '财物遗失'],
    results: ['已现场调解', '已移交刑侦', '已立案侦查', '求助人已找到', '已办结', '正在核查'],
    items: ['被盗电动车', '涉案手机', '现场遗留背包', '损坏的门锁', '涉案银行卡', '现场监控硬盘'],
    actions: ['接警登记', '指派民警', '到场处置', '调查取证', '办结归档'],
    configs: [['一级警情响应时限', '5分钟'], ['二级警情响应时限', '15分钟'], ['警情回访时限', '48小时'], ['重复报警合并窗口', '30分钟']],
    cats: ['警情类型', '处置状态', '刑事警情', '治安警情', '求助警情', '已办结', '处置中'],
  },
  {
    key: 'v94_dho2pf', name: '案件管理',
    places: ['城东街道', '城西街道', '开发区', '老城区商业街', '滨江新区'],
    types: ['盗窃案', '诈骗案', '抢夺案', '故意伤害案', '非法经营案', '职务侵占案', '掩饰隐瞒犯罪所得案', '寻衅滋事案'],
    results: ['已破案', '侦查中', '已移送起诉', '已撤案', '补充侦查'],
    items: ['涉案现金', '作案工具', '涉案车辆', '涉案手机', '赃物首饰', '作案刀具'],
    actions: ['受案登记', '立案审批', '调查取证', '移送起诉', '结案归档'],
    configs: [['立案审查时限', '24小时'], ['侦查羁押期限提醒', '30天'], ['涉案财物入库时限', '12小时'], ['案件质量评查周期', '季度']],
    cats: ['案件类别', '办案阶段', '刑事案件', '行政案件', '侦查中', '已移送起诉', '已破案'],
  },
  {
    key: 'v94_e3pnoz', name: '治安管理',
    places: ['KTV娱乐会所', '金桥宾馆', '网咖连锁店', '城东农贸市场', '滨江夜市', '火车站周边'],
    types: ['场所治安检查', '涉赌线索核查', '卖淫嫖娼查处', '噪音扰民处置', '扰乱公共秩序', '非法携带管制器具'],
    results: ['当场处罚', '责令整改', '行政拘留', '警告教育', '已立案'],
    items: ['赌资', '涉案麻将机', '管制刀具', '违规音响设备', '涉案账本'],
    actions: ['检查登记', '现场取证', '行政处罚', '整改复查', '归档'],
    configs: [['场所检查频次', '每周1次'], ['行政处罚公示期', '7天'], ['整改复查时限', '15天'], ['涉赌线索核查时限', '72小时']],
    cats: ['场所类型', '处罚种类', '娱乐场所', '旅馆业', '当场处罚', '行政拘留', '责令整改'],
  },
  {
    key: 'v94_aisa9n', name: '交通管理',
    places: ['人民路与建设大道交叉口', '滨江大道隧道口', 'G15高速城东出口', '中山路小学门前', '火车站站前广场'],
    types: ['醉驾查处', '超速违法', '闯红灯', '违停整治', '交通事故快处', '货车超载', '无证驾驶'],
    results: ['已处罚', '现场教育', '已扣留车辆', '事故已调解', '已移交'],
    items: ['违法车辆', '驾驶证', '行驶证', '测速设备记录', '事故车辆'],
    actions: ['违法录入', '现场取证', '处罚决定', '缴款确认', '结案'],
    configs: [['醉驾血液检测阈值', '80mg/100ml'], ['区间测速限值', '80km/h'], ['事故快处时限', '30分钟'], ['违停拖移阈值', '3次未处理']],
    cats: ['违法类型', '事故等级', '酒驾醉驾', '超速行驶', '轻微事故', '一般事故', '已处罚'],
  },
  {
    key: 'v94_cta9ss', name: '户政服务',
    places: ['城东派出所户籍窗口', '城西派出所户籍窗口', '政务服务中心公安窗口', '开发区便民服务点'],
    types: ['居民身份证办理', '户口迁移', '新生儿出生登记', '居住证申领', '户籍信息变更', '无犯罪记录证明'],
    results: ['已办结', '材料待补正', '审核中', '已出证'],
    items: ['居民身份证', '户口簿', '居住证', '出生医学证明', '准迁证'],
    actions: ['受理登记', '材料审核', '信息录入', '制证发送', '办结归档'],
    configs: [['身份证办理时限', '15个工作日'], ['居住证签注周期', '1年'], ['户口迁移审批层级', '县级'], ['窗口预约放号量', '120/日']],
    cats: ['业务类型', '办理状态', '证件办理', '户口业务', '已办结', '审核中', '材料待补正'],
  },
  {
    key: 'v94_cg4a0f', name: '巡逻防控',
    places: ['步行街商圈', '火车站广场', '滨江公园', '城东工业园', '第三中学', '地铁2号线站点'],
    types: ['街面巡逻', '武装盘查', '护学岗', '夜间亮灯巡逻', '重点部位值守', '治安卡口检查'],
    results: ['正常', '发现可疑人员已盘查', '收缴违禁物品', '已移交派出所'],
    items: ['盘查人员', '可疑车辆', '违禁刀具', '涉案物品', '巡逻装备'],
    actions: ['巡逻签到', '盘查登记', '情况上报', '移交处置', '归队'],
    configs: [['巡逻路线数量', '18条'], ['护学岗上岗时间', '07:00'], ['夜间巡逻频次', '每2小时1次'], ['盘查数据上传时限', '2小时']],
    cats: ['巡防类型', '勤务状态', '街面巡逻', '卡口查控', '护学岗', '在岗', '已结束'],
  },
  {
    key: 'v94_fndjg7', name: '社区警务',
    places: ['阳光社区', '翠湖社区', '幸福里社区', '工业园社区', '老城厢社区'],
    types: ['入户走访', '矛盾纠纷调解', '反诈宣传', '重点人员走访', '出租房屋核查', '警民恳谈会'],
    results: ['已完成', '已调解', '跟进中', '已上报'],
    items: ['走访户数', '宣传资料', '调解协议', '出租房屋台账', '重点人员档案'],
    actions: ['走访登记', '矛盾调解', '宣传开展', '情况上报', '归档'],
    configs: [['社区民警每周走访户数', '20户'], ['矛盾回访周期', '7天'], ['反诈宣传覆盖率目标', '90%'], ['出租房屋核查频次', '每月1次']],
    cats: ['社区类型', '工作类别', '入户走访', '矛盾调解', '反诈宣传', '已完成', '跟进中'],
  },
  {
    key: 'v94_fs3i5y', name: '禁毒管理',
    places: ['城东街道社区戒毒点', '化工园区', '娱乐场所集中区', '城西社区卫生中心'],
    types: ['吸毒人员尿检', '社区戒毒报到', '易制毒化学品企业检查', '禁毒宣传进校园', '涉毒线索核查'],
    results: ['检测阴性', '已报到', '整改通知', '已立案', '宣传完成'],
    items: ['尿检样本', '易制毒化学品', '禁毒宣传册', '戒毒人员档案'],
    actions: ['检测登记', '报到登记', '企业检查', '线索核查', '归档'],
    configs: [['尿检周期', '每月1次'], ['社区戒毒报到时限', '7天'], ['易制毒企业检查频次', '季度'], ['禁毒宣传覆盖学校数', '32所']],
    cats: ['管控类别', '检查类型', '社区戒毒', '强制隔离戒毒', '易制毒企业', '已报到', '整改中'],
  },
  {
    key: 'v94_cc0b7z', name: '应急指挥',
    places: ['市体育中心', '会展中心', '火车站枢纽', '滨江景观带', '城东收费站'],
    types: ['大型活动安保', '突发事件处置', '应急演练', '警力调度', '跨区域协查'],
    results: ['已完成', '演练结束', '处置完毕', '持续跟进'],
    items: ['安保警力', '应急车辆', '通信设备', '警戒设施', '救援物资'],
    actions: ['指令下达', '警力集结', '现场处置', '情况反馈', '复盘归档'],
    configs: [['一级响应集结时限', '20分钟'], ['安保警力储备', '200人'], ['应急演练频次', '每半年1次'], ['指挥调度通信频道', '3个']],
    cats: ['事件等级', '响应状态', '一级响应', '二级响应', '大型活动', '已完成', '处置中'],
  },
  {
    key: 'v94_atflvx', name: '决策分析',
    places: ['全市', '城东片区', '城西片区', '开发区片区'],
    types: ['周警情研判', '案件态势分析', '重点区域整治评估', '警力投入效能分析', '反诈成效评估'],
    results: ['已发布', '待审核', '已采纳'],
    items: ['警情数据', '案件数据', '警力数据', '研判报告', '整改建议'],
    actions: ['数据汇总', '模型分析', '报告生成', '审核发布', '归档'],
    configs: [['研判报告发布周期', '每周一'], ['数据统计口径', '自然日'], ['警情热力图刷新间隔', '30分钟'], ['分析维度数量', '12']],
    cats: ['分析主题', '报告状态', '警情分析', '案件分析', '效能评估', '已发布', '待审核'],
  },
  {
    key: 'v94_gao8uh', name: '系统管理',
    places: ['市局指挥中心', '分局机房', '派出所终端'],
    types: ['账号权限维护', '角色授权调整', '系统参数配置', '数据备份', '安全巡检'],
    results: ['已完成', '待复核'],
    items: ['账号', '角色', '权限点', '备份文件', '巡检记录'],
    actions: ['新增', '变更', '授权', '复核', '归档'],
    configs: [['密码有效期', '90天'], ['登录失败锁定次数', '5次'], ['操作日志保留期', '180天'], ['数据备份周期', '每日']],
    cats: ['管理类别', '操作状态', '账号管理', '权限管理', '参数配置', '已完成', '待复核'],
  },
];

const CODES = {
  'v94_blkq2s': 'JC', 'v94_1a9iwwc': 'JQ', 'v94_dho2pf': 'AJ', 'v94_e3pnoz': 'ZA',
  'v94_aisa9n': 'JT', 'v94_cta9ss': 'HZ', 'v94_cg4a0f': 'XL', 'v94_fndjg7': 'SQ',
  'v94_fs3i5y': 'JD', 'v94_cc0b7z': 'YJ', 'v94_atflvx': 'FX', 'v94_gao8uh': 'XT',
};

const DOMAIN_META = {
  '基础数据': { no: '档案编号', person: '登记人', result: '核录结果', desc: '信息已核对并录入基础台账', units: ['治安大队', '城东派出所', '城西派出所', '开发区派出所'] },
  '接处警管理': { no: '警情编号', person: '报警人', result: '处置结果', desc: '民警到场处置，现场情况已核实并登记', units: ['指挥中心', '城东派出所', '城西派出所', '城南派出所', '巡特警大队'] },
  '案件管理': { no: '案件编号', person: '报案人', result: '办理结果', desc: '现场已勘验并固定证据，相关证人已询问', units: ['刑侦大队', '城东派出所', '城西派出所', '开发区派出所'] },
  '治安管理': { no: '检查编号', person: '被检查人', result: '处理结果', desc: '现场检查已记录，依法告知整改要求', units: ['治安大队', '城东派出所', '城西派出所'] },
  '交通管理': { no: '违法编号', person: '当事人', result: '处理结果', desc: '现场已核实违法事实并固定证据，依法告知当事人权利义务', units: ['交警大队', '交警一中队', '交警二中队', '事故处理中队'] },
  '户政服务': { no: '业务编号', person: '申请人', result: '办理结果', desc: '材料齐全，已按规定流程受理并录入人口信息系统', units: ['政务服务中心公安窗口', '城东派出所户籍窗口', '城西派出所户籍窗口'] },
  '巡逻防控': { no: '勤务编号', person: '盘查对象', result: '处置结果', desc: '勤务正常，盘查信息已录入', units: ['巡特警大队', '城东派出所', '城西派出所'] },
  '社区警务': { no: '走访编号', person: '走访对象', result: '办理结果', desc: '已按走访计划完成入户走访，听取群众意见', units: ['社区警务队', '城东派出所', '开发区派出所'] },
  '禁毒管理': { no: '管控编号', person: '检测对象', result: '处理结果', desc: '已按规定完成检测与检查，结果记录在案', units: ['禁毒大队', '城东街道社区戒毒点', '城西社区卫生中心'] },
  '应急指挥': { no: '指挥编号', person: '现场负责人', result: '处置结果', desc: '现场已完成警力集结与处置，秩序恢复正常', units: ['指挥中心', '巡特警大队', '交警大队'] },
  '决策分析': { no: '报告编号', person: '编制人', result: '发布结果', desc: '已汇总周期内警情与案件数据，形成研判结论', units: ['指挥中心', '情报研判中心'] },
  '系统管理': { no: '操作编号', person: '操作人', result: '处理结果', desc: '已按运维流程完成操作，执行结果已记录', units: ['指挥中心', '分局机房'] },
};

const SURNAMES = ['张', '王', '李', '刘', '陈', '杨', '赵', '黄', '周', '吴', '徐', '孙', '马', '朱', '胡', '郭', '何', '高', '林', '罗'];
const GIVEN = ['伟', '芳', '娜', '强', '静', '洋', '磊', '敏', '杰', '丽', '明', '娟', '超', '婷', '军', '涛', '洋', '鹏', '斌', '霞', '建国', '志强', '小燕', '文博'];
const POLICE = ['城东派出所', '城西派出所', '城南派出所', '刑侦大队', '治安大队', '交警大队', '禁毒大队', '巡特警大队', '指挥中心', '社区警务队'];

let seed = 20261118 >>> 0;
// mulberry32：避免 LCG 在 JS 浮点下退化成固定序列（会导致同一小区/同一警情连刷）
function rnd(n) {
  seed = (seed + 0x6D2B79F5) >>> 0;
  let t = seed;
  t = Math.imul(t ^ (t >>> 15), t | 1);
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return ((t ^ (t >>> 14)) >>> 0) % n;
}
function pick(a) { return a[rnd(a.length)]; }
function person() { return pick(SURNAMES) + pick(GIVEN); }
function pad(n, w) { return String(n).padStart(w, '0'); }
function q(s) { return "'" + String(s).replace(/\\/g, '\\\\').replace(/'/g, "''") + "'"; }

const NOW = new Date('2026-11-18T10:00:00+08:00');
const DAY = 86400000;
function daysAgo(d, h = 9, m = 0) {
  const t = new Date(NOW.getTime() - d * DAY);
  t.setHours(h, m, 0, 0);
  const p = x => pad(x, 2);
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} ${p(t.getHours())}:${p(t.getMinutes())}:00`;
}
function dateAgo(d) {
  const t = new Date(NOW.getTime() - d * DAY);
  const p = x => pad(x, 2);
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())}`;
}

const N = 30;
const DB = process.argv[3] || 'police_affairs';
const out = [];
out.push('SET NAMES utf8mb4;');
out.push('SET FOREIGN_KEY_CHECKS = 0;');
out.push(`USE \`${DB}\`;`);

for (const d of DOMAINS) {
  const p = d.key;
  const tables = ['main', 'category', 'config', 'detail', 'item', 'log', 'stat'].map(s => `${p}_${s}`);
  out.push(`\n-- ===== ${d.name} =====`);
  tables.forEach(t => out.push(`DELETE FROM \`${t}\`;`));

  // ---- 分类树：3 根 + 4 子 ----
  const cats = d.cats;
  const catRows = [];
  const roots = cats.slice(0, 3);
  const leaves = cats.slice(3);
  roots.forEach((c, i) => catRows.push(`(${i + 1}, 0, ${q(c)}, ${i + 1}, ${q(daysAgo(90 - i))}, ${q(daysAgo(90 - i))}, 1, 1, 0)`));
  leaves.forEach((c, i) => catRows.push(`(${i + 4}, ${1 + (i % 3)}, ${q(c)}, ${i + 1}, ${q(daysAgo(85 - i))}, ${q(daysAgo(85 - i))}, 1, 1, 0)`));
  out.push(`INSERT INTO \`${p}_category\` (id,parent_id,name,sort_no,create_time,update_time,create_by,update_by,deleted) VALUES\n${catRows.join(',\n')};`);

  // ---- 主表 30 行 ----
  const mainRows = [];
  const mainMeta = [];
  for (let i = 0; i < N; i++) {
    const day = 88 - Math.floor(i * 2.9);
    const hour = 7 + (i % 13);
    const minute = (i * 17) % 60;
    const place = pick(d.places);
    const type = pick(d.types);
    const title = `${type}（${place}）`;
    const officerA = person();
    const officerB = person();
    const reporter = person();
    const phone = `1${pick(['3', '5', '7', '8', '9'])}${pad(100000000 + rnd(900000000), 9)}`;
    const caseNo = `${CODES[p]}${String(202608000 + i + 1)}`;
    const status = i % 6 === 0 ? 0 : (i % 4 === 0 ? 1 : 2);
    const result = pick(d.results);
    const reportTime = daysAgo(day, hour, minute);
    const meta = DOMAIN_META[d.name];
    const content = `${meta.no}：${caseNo}；受理单位：${pick(meta.units)}；事发地点：${place}；类别：${type}；`
      + `${meta.person}：${reporter}（${phone}）；受理时间：${reportTime}；承办民警：${officerA}、${officerB}；`
      + `简要情况：${type}，${meta.desc}；`
      + `${meta.result}：${result}；办结时限：${daysAgo(day - 2, 17, 20)}。`;
    mainRows.push(`(${i + 1}, ${q(title)}, ${status}, ${q(content)}, ${q(reportTime)}, ${q(daysAgo(day - 1, 18, 30))}, 1, 1, 0)`);
    mainMeta.push({ day, hour, minute, title, status, result });
  }
  out.push(`INSERT INTO \`${p}_main\` (id,name,status,content,create_time,update_time,create_by,update_by,deleted) VALUES\n${mainRows.join(',\n')};`);

  // ---- 明细 30 行（涉案/业务金额） ----
  const detailRows = [];
  for (let i = 0; i < N; i++) {
    const m = mainMeta[i];
    const amount = (d.name === '户政服务' ? (rnd(3) * 10 + 0.5) : (rnd(48) * 500 + 200)).toFixed(2);
    const bizNo = `${CODES[p]}${20260001 + i}`;
    detailRows.push(`(${i + 1}, ${i + 1}, ${q(bizNo)}, ${amount}, ${q(daysAgo(m.day, m.hour, m.minute))}, ${q(daysAgo(m.day - 1, 18, 30))}, 1, 1, 0)`);
  }
  out.push(`INSERT INTO \`${p}_detail\` (id,${p}_main_id,biz_no,amount,create_time,update_time,create_by,update_by,deleted) VALUES\n${detailRows.join(',\n')};`);

  // ---- 条目 30 行 ----
  const itemRows = [];
  for (let i = 0; i < N; i++) {
    const m = mainMeta[i];
    itemRows.push(`(${i + 1}, ${i + 1}, ${q(pick(d.items))}, ${1 + rnd(20)}, ${q(daysAgo(m.day, m.hour, m.minute))}, ${q(daysAgo(m.day - 1, 18, 30))}, 1, 1, 0)`);
  }
  out.push(`INSERT INTO \`${p}_item\` (id,${p}_main_id,item_name,qty,create_time,update_time,create_by,update_by,deleted) VALUES\n${itemRows.join(',\n')};`);

  // ---- 操作记录 30 行 ----
  const logRows = [];
  for (let i = 0; i < N; i++) {
    const m = mainMeta[i];
    const action = pick(d.actions);
    logRows.push(`(${i + 1}, ${i + 1}, ${q(action)}, ${q(daysAgo(m.day, Math.min(23, m.hour + 1), m.minute))}, ${q(daysAgo(m.day, Math.min(23, m.hour + 1), m.minute))}, ${q(daysAgo(m.day - 1, 18, 30))}, 1, 1, 0)`);
  }
  out.push(`INSERT INTO \`${p}_log\` (id,${p}_main_id,action,operate_time,create_time,update_time,create_by,update_by,deleted) VALUES\n${logRows.join(',\n')};`);

  // ---- 参数配置 ----
  const cfgRows = d.configs.map((kv, i) =>
    `(${i + 1}, ${q(kv[0])}, ${q(kv[1])}, ${q(daysAgo(90))}, ${q(daysAgo(30))}, 1, 1, 0)`);
  out.push(`INSERT INTO \`${p}_config\` (id,config_key,config_value,create_time,update_time,create_by,update_by,deleted) VALUES\n${cfgRows.join(',\n')};`);

  // ---- 统计 30 天 ----
  const statRows = [];
  for (let i = 0; i < N; i++) {
    const dayStr = dateAgo(29 - i);
    const total = (rnd(40) * 800 + 1200).toFixed(2);
    statRows.push(`(${i + 1}, ${q(dayStr)}, ${total}, ${q(dayStr + ' 23:59:00')}, ${q(dayStr + ' 23:59:00')}, 1, 1, 0)`);
  }
  out.push(`INSERT INTO \`${p}_stat\` (id,stat_date,total_amount,create_time,update_time,create_by,update_by,deleted) VALUES\n${statRows.join(',\n')};`);
}

out.push('SET FOREIGN_KEY_CHECKS = 1;');
import { writeFileSync } from 'node:fs';
const target = process.argv[2] || 'seed.sql';
writeFileSync(target, out.join('\n'), 'utf8');
console.log(`written ${target}: ${out.length} statements, ${(out.join('\n').length / 1024).toFixed(1)} KB`);

