# Majiang · കോഡ് ജനറേറ്റർ (CodeGen) — പദ്ധതി ആമുഖം

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · **മലയാളം** · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> ബഹു-പ്രോജക്റ്റ് കോഡ് ജനറേറ്റർ **Spring Boot + Vue** നുള്ളത്: ഡാറ്റാബേസ് ടേബിൾ ഘടനകളിൽ നിന്ന് ആരംഭിച്ച്, ഇത് **കംപൈൽ ചെയ്യാവുന്ന, പ്രവർത്തിപ്പിക്കാവുന്ന, മെനു-ആൻഡ്-അനുമതി-റെഡി** ഫ്രണ്ട്-എൻഡും ബാക്ക്-എൻഡ് ബിസിനസ് കോഡും ഉത്പാദിപ്പിക്കുകയും, ഡിസ്കിൽ എഴുതുന്നത്, ബാക്കപ്പ്, ലെഡ്ജർ ചെയ്യൽ, റോൾബാക്ക് എന്നിവയുടെ പൂർണ്ണ ഡെലിവറി ലൂപ്പ് കൈകാര്യം ചെയ്യുകയും ചെയ്യുന്നു.

GitHub: https://github.com/hustxia/CodeGen

## 1. ഇതെന്താണ്

CodeGen ഒരു 'ടേബിൾ-ഡ്രൈവൻ' കോഡ് ഫാക്ടറിയാണ്. നിങ്ങളുടെ ബിസിനസ് ടേബിളുകൾ (ഡാറ്റാബേസ് ഇംപോർട്ട് വഴിയോ DDL പേസ്റ്റ് ചെയ്തോ) കൊണ്ടുവരിക, ഫീൽഡുകളും ജനറേഷൻ സ്വിച്ചുകളും ക്രമീകരിക്കുക, തുടർന്ന് 'Generate' ക്ലിക്ക് ചെയ്യുക — ഇത് ബാക്ക്-എൻഡ് Entity/Mapper/Service/Controller/DTO/VO, ഫ്രണ്ട്-എൻഡ് `api.js` എന്നിവയും Vue പേജുകൾ, മെനുകൾ, റൂട്ടുകൾ, പോലും A4 പ്രിന്റ് പേജുകളും ഇൻറിഗ്രേഷൻ-ടെസ്റ്റ് സ്കെലിറ്റണുകളും ഉത്പാദിപ്പിക്കുകയും നിങ്ങളുടെ ടാർഗെറ്റ് പ്രോജക്റ്റിലേക്ക് സുരക്ഷിതമായി എഴുതുകയും ചെയ്യുന്നു.

ഇത് കേവലം 'കോഡ് തുപ്പുന്നതല്ല' — ഗുണനിലവാര പോർട്ടലുകളും റോൾബാക്ക് ശേഷിയുമുള്ള ഒരു **ഡെലിവറി പൈപ്പ്ലൈനാണ്**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. ഉപയോക്താക്കൾ ആരാണ്, എങ്ങനെ ഉപയോഗിക്കും

| ഉപയോക്താവ് | സാധാരണ ആവശ്യം | പ്ലാറ്റ്ഫോം നൽകുന്ന പാത |
| --- | --- | --- |
| ബാക്ക്-എൻഡ് ഡെവലപ്പർ | ഒരു കൂട്ടം ടേബിളുകൾ (അല്ലെങ്കിൽ DDL) ഉണ്ട്, CRUD വേഗത്തിൽ സെറ്റപ്പ് ചെയ്യാൻ ആഗ്രഹിക്കുന്നു | ടേബിൾ കോൺഫിഗ് പേജ്: ഇംപോർട്ട് → ഫീൽഡുകൾ ക്രമീകരിക്കുക → ഒരു ക്ലിക്കിൽ ജനറേറ്റ് ചെയ്യുക |
| ഫുൾ-സ്റ്റാക്ക് / സ്വതന്ത്ര ഡെവലപ്പർ | പൂജ്യത്തിൽ നിന്ന് ഒരു പൂർണ്ണ സിസ്റ്റം (ലോഗിൻ, അനുമതികൾ, ഡിക്ഷനറി ഉൾപ്പെടെ) കെട്ടിപ്പടുക്കാൻ ആഗ്രഹിക്കുന്നു | പൈപ്പ്ലൈൻ പേജ്: ഒരു വാചകത്തിൽ സിസ്റ്റം കെട്ടിപ്പടുക്കുക |
| ടീം ലീഡ് | കോഡ് സ്റ്റൈൽ, കോഡ് കൺവെൻഷനുകൾ, ഡയറക്ടറി ഘടന എന്നിവ ഏകീകരിക്കാൻ ആഗ്രഹിക്കുന്നു | സിസ്റ്റം മാനേജ്മെന്റ്: ഒരു സിസ്റ്റം = ഒരു ഔട്ട്പുട്ട് കോൺഫിഗറേഷൻ, ബാച്ച് ആയി പ്രാബല്യത്തിൽ വരുന്ന |
| പ്ലാറ്റ്ഫോം മെയിന്റൈനർ | കോഡ് മാറ്റാതെ ടെംപ്ലേറ്റുകൾ മാറ്റാൻ ആഗ്രഹിക്കുന്നു | ടെംപ്ലേറ്റ് മാനേജ്മെന്റ്: ഓൺലൈനിൽ എഡിറ്റ് ചെയ്യുക, സേവ് ചെയ്താൽ പ്രാബല്യത്തിൽ വരും, ചരിത്രപരമായ റോൾബാക്ക് പിന്തുണയ്ക്കുന്നു |
---

## 3. ഇത് ഏതൊക്കെ പ്രശ്നങ്ങൾ പരിഹരിക്കുന്നു

| വേദനാ ബിന്ദു | CodeGen എന്ത് ചെയ്യുന്നു |
| --- | --- |
| പുനരാവൃത്ത CRUD നരകം — ഓരോ ടേബിളിനും ഒരു കൂമ്പാരം boilerplate | ടേബിൾ-തിരിഞ്ഞുള്ള സ്വിച്ചുകൾ ഒരു ക്ലിക്കിൽ പൂർണ്ണ ഫ്രണ്ട് & ബാക്ക് എൻഡ് ഉത്പാദിപ്പിക്കുന്നു, CRUD, പേജിനേഷൻ, വാലിഡേഷൻ, import/export എന്നിവ ഉൾപ്പെടെ |
| ഇൻറിഗ്രേഷൻ പിശകുകൾ വരുത്തുന്ന ഫ്രണ്ട്/ബാക്ക് ഫീൽഡ് & API പൊരുത്തക്കേടുകൾ | അതേ ടേബിൾ മെറ്റാഡേറ്റ ബാക്ക്-എൻഡ് API-കളെയും ഫ്രണ്ട്-എൻഡ് പേജുകളെയും രണ്ടിനെയും ഓടിക്കുന്നു — സെമാന്റിക് ഡ്രിഫ്റ്റ് ഇല്ല |
| ടീം കൺവെൻഷനുകൾ ഏകീകരിക്കാൻ പ്രയാസം; ഓരോരുത്തരുടെയും ശൈലി വ്യത്യസ്തമാണ് | ടെംപ്ലേറ്റുകളാണ് കൺവെൻഷൻ: 22 FreeMarker ടെംപ്ലേറ്റുകൾ കേന്ദ്രീകൃതമായി മാനേജ് ചെയ്യുന്നു, പതിപ്പ് റോൾബാക്കോടെ ഓൺലൈനിൽ എഡിറ്റ് ചെയ്യാവുന്ന |
| ജനറേറ്ററുകൾ 'ഓവർറൈറ്റ് ചെയ്ത് നഷ്ടപ്പെടുത്തുന്ന' കൈയെഴുത്ത് ലോജിക് | മാർക്കർ-ബ്ലോക്ക് മെർജ് മെക്കാനിസം: കൈയെഴുത്ത് കോഡ് സംരക്ഷിക്കപ്പെടുന്നു, ജനറേറ്റ് ചെയ്ത പ്രദേശങ്ങൾ മാത്രമേ മാറ്റിസ്ഥാപിക്കൂ |
| കംപൈൽ ചെയ്യുന്നതുവരെ ജനറേറ്റ് ചെയ്ത കോഡ് വിശ്വസിക്കാനാവില്ല | ഇൻ-മെമ്മറി കംപൈൽ സെൽഫ്-ചെക്ക് (യഥാർത്ഥ `javac`), സ്ട്രക്ചറൽ കോൺട്രാക്റ്റ് ചെക്കുകൾ, ഡിസ്ക്-ഓൺ ഡിഫ് പ്രിവ്യൂ — കംപൈൽ ചെയ്യാതെ ഒന്നും ഡിസ്കിൽ വീഴില്ല |
| പൂജ്യത്തിൽ നിന്ന് പുതിയ സിസ്റ്റം സ്കാഫോൾഡ് ചെയ്യുന്നത് സമയമെടുക്കുന്നു | പൈപ്പ്‌ലൈൻ 'ഒരു വാചകത്തിൽ സിസ്റ്റം കെട്ടിപ്പടുക്കുക': DB സൃഷ്ടിക്കുക → scaffold → generate → package → മെനു രജിസ്റ്റർ ചെയ്യുക, പൂർണ്ണമായും ഓട്ടോമേറ്റഡ് |

---

## 4. കഴിവ് ഭൂപ്രകൃതി (Capability Landscape)

- **ടേബിൾ ഇൻടേക്ക്**: DDL പാഴ്‌സിംഗ്, നേരിട്ടുള്ള ഡാറ്റാബേസ് ഇംപോർട്ട്, ബാച്ച് ഇൻടേക്ക് വിസാർഡ്, ഓട്ടോ-ഡിറൈവ്ഡ് കോളം കോൺഫിഗറേഷൻ (കൺട്രോളുകൾ / വാലിഡേഷൻ / ഡിക്ഷനറികൾ / റിലേഷനുകൾ).
- **ടേബിൾ ആകൃതികൾ**: സിംഗിൾ ടേബിൾ, ട്രീ ടേബിൾ (ട്രീ API + ട്രീ ഗ്രിഡ്), മാസ്റ്റർ-ഡീറ്റെയിൽ (ഒരേ ട്രാൻസാക്ഷനിൽ സേവ് + ഡീറ്റെയിൽ ടാബുകൾ), many-to-many ജംഗ്ഷൻ ടേബിളുകൾ.
- **ജനറേറ്റ് ചെയ്ത artifacts**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + Vue പേജുകൾ + A4 പ്രിന്റ് പേജുകൾ + ഇൻറിഗ്രേഷൻ-ടെസ്റ്റ് സ്കെലിറ്റണുകൾ.
- **ബിസിനസ് വർദ്ധനവുകൾ**: റിലേഷൻ ഡ്രോപ്പ്ഡൗണുകൾ, ഡിക്ഷനറി ട്രാൻസ്ലേഷൻ, ഡാറ്റ മാസ്കിംഗ്, യൂണിക്വ് ചെക്കുകൾ, ഫോം ലിങ്കേജ്, ഡാറ്റ-സ്കോപ്പ് പെർമിഷനുകൾ, Excel import/export, അപ്രൂവൽ വർക്ക്ഫ്ലോ, പേയ്‌മെന്റ് / ഫയൽ / മൊബൈൽ കപ്പാസിറ്റി പായ്ക്കുകൾ.
- **ഗുണനിലവാര പോർട്ടലുകൾ**: ഇൻ-മെമ്മറി കംപൈൽ സെൽഫ്-ചെക്ക് (യഥാർത്ഥ `javac`), സ്ട്രക്ചറൽ കോൺട്രാക്റ്റ് ചെക്കുകൾ, പെർമിഷൻ-കൺസിസ്റ്റൻസി ചെക്കുകൾ, ഡിസ്ക്-ഓൺ ഡിഫ് പ്രിവ്യൂ, ഫ്രണ്ട്-എൻഡ് artifacts-ന്റെ യഥാർത്ഥ വാലിഡേഷൻ.
- **സുരക്ഷിത ലാൻഡിംഗ്**: ഡിസ്കിൽ എഴുത്ത് + `.gen-backup` ബാച്ച് ബാക്കപ്പുകൾ, കൈയെഴുത്ത് കോഡ് സംരക്ഷിക്കാനുള്ള മാർക്കർ-ബ്ലോക്ക് മെർജിംഗ്, നാല്-സ്റ്റേറ്റ് ലെഡ്ജർ `gen_output` (created / merged / skipped / failed), ചരിത്രപരമായ റോൾബാക്ക്.
- **മൾട്ടി-സിസ്റ്റം ഐസൊലേഷൻ**: ഓരോ ടാർഗെറ്റ് പ്രോജക്റ്റും ഒരു 'സിസ്റ്റം കോൺഫിഗറേഷൻ' ആണ് (front/back പാതകൾ, പാക്കേജ് നാമങ്ങൾ, gateway prefix, ഡിപൻഡൻസി classpath, ജനറേഷൻ-സ്വിച്ച് ഡിഫോൾട്ടുകൾ) — ഒന്നിലധികം പ്രോജക്റ്റുകൾ സമാന്തരമായി സേവിക്കപ്പെടുന്നു.
- **AI വർദ്ധനവുകൾ**: ഒരു വാചകത്തിൽ സിസ്റ്റം കെട്ടിപ്പടുക്കുക (AI മോഡലിംഗ്), seed-data AI വർദ്ധനവ്, ടേബിൾ-തിരിഞ്ഞുള്ള AI ശുപാർശ ചെയ്ത കോൺഫിഗറേഷൻ (ഡിഫ് പ്രിവ്യൂ + ചെക്ക് ചെയ്ത അപ്ലൈ).

---

## 5. സാധാരണ സാഹചര്യങ്ങൾ

| സാഹചര്യം | വിവരണം |
| --- | --- |
| ട്രീ ടേബിൾ | ഉൽപ്പന്ന വിഭാഗങ്ങൾ പോലുള്ള ഹൈറാർക്കിക്കൽ ഡാറ്റ — ഓട്ടോ-പ്രൊഡ്യൂസ്ഡ് ട്രീ API, ട്രീ ഗ്രിഡ്, പാരന്റ്-സെലക്ഷൻ ഡ്രോപ്പ്ഡൗൺ |
| സിംഗിൾ-ടേബിൾ CRUD | ഉപഭോക്താക്കൾ, ഉൽപ്പന്നങ്ങൾ മുതലായവ — FK ഡ്രോപ്പ്ഡൗണുകൾ + ഡിക്ഷനറി ട്രാൻസ്ലേഷൻ + import/export പെട്ടെന്ന് ലഭ്യമാകുന്ന |
| മാസ്റ്റർ-ഡീറ്റെയിൽ | വിൽപ്പന ഓർഡറുകൾ + ഓർഡർ ഇനങ്ങൾ — ഒരേ ട്രാൻസാക്ഷനിൽ സേവ്, ഡ്യുവൽ ടാബുകളോടു കൂടിയ ഡീറ്റെയിൽ ഡ്രാവർ (മാസ്റ്റർ വിവരങ്ങൾ / വിശദാംശങ്ങൾ) |
| റീഡ്-ഓൺലി / സ്റ്റാറ്റ്സ് | ഇൻവെന്ററി ഫ്ലോകൾ മുതലായവ — റൈറ്റ് സ്വിച്ചുകൾ ഓഫ്, ചോദ്യം ചെയ്യൽ എക്സ്പോർട്ട് മാത്രം |
| പ്രിന്റ് ഡോക്യുമെന്റുകൾ | 'print' സ്വിച്ച് ടിക്ക് ചെയ്യുക ഒരു A4 പ്രിന്റ് പേജും റൂട്ടും, ഹെഡർ + ഡീറ്റെയിൽ ടേബിളോടു കൂടി ഉത്പാദിപ്പിക്കാൻ |
| ഒരു വാചകത്തിൽ സിസ്റ്റം കെട്ടിപ്പടുക്കൽ | ബിസിനസ് ഡൊമെയ്ൻ വിവരിക്കുക — AI ഒരേ ഊഴിൽ DB & ടേബിളുകൾ + scaffold + പൂർണ്ണ ജനറേഷൻ + മെനു രജിസ്ട്രേഷൻ സൃഷ്ടിക്കുന്നു |

> എല്ലാ സാഹചര്യങ്ങൾക്കും റൺ ചെയ്യാവുന്ന demos (`demo_admin` പ്രോജക്റ്റ്) സ്ക്രീൻഷോട്ടുകളോടു കൂടി ഉണ്ട് — `docs/典型场景操作指南-演示后台demo.md` (ചൈനീസ്) കാണുക.

---

## 6. കോർ വർക്ക്ഫ്ലോ

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **ഡിഫ് പ്രിവ്യൂ**: ജനറേറ്റ് ചെയ്ത artifacts നിലവിലെ ഡിസ്ക് സ്റ്റേറ്റിനെതിരെ ഫയൽ-തിരിഞ്ഞ് diff ചെയ്യുന്നു; എന്തെങ്കിലും എഴുതുന്നതിന് മുമ്പ് നിങ്ങൾ സ്ഥിരീകരിക്കുന്നു, അപകടകരമായ ഓവർറൈറ്റുകൾ ഒഴിവാക്കി.
- **ജനറേഷൻ ലെഡ്ജർ**: ഓരോ ഫയലും അതിന്റെ ആക്ഷൻ, ബൈറ്റ് എണ്ണം, MD5, ബാക്കപ്പ് ലൊക്കേഷൻ എന്നിവ രേഖപ്പെടുത്തുന്നു; മൊത്തം ഒരു ബാച്ച് റോൾബാക്ക് ചെയ്യാവുന്നതാണ്.

---

## 7. ഒരു വാചകത്തിൽ സിസ്റ്റം കെട്ടിപ്പടുക്കുക (പൈപ്പ്ലൈൻ)

"പൂജ്യത്തിൽ നിന്ന് ഒരു ബാക്ക്-എൻഡ് സ്കാഫോൾഡ് ചെയ്യുക" എന്നതിനായുള്ള ഒരു ഉന്നത-തല കഴിവാണിത്. ബിസിനസ് ഡൊമെയ്ൻ വിവരിച്ചുകഴിഞ്ഞാൽ, പൈപ്പ്ലൈൻ 5 ഘട്ടങ്ങളിലൂടെ യാന്ത്രികമായി മുന്നോട്ട് പോകുന്നു:

| ഘട്ടം | അർത്ഥം |
| --- | --- |
| `CHECK` | ടാർഗെറ്റ് പ്രോജക്റ്റ് കോൺഫിഗറേഷനും ഡിപൻഡൻസി classpath ഉം വാലിഡേറ്റ് ചെയ്യുക |
| `WRITE` | ഡാറ്റാബേസും ടേബിളുകളും സൃഷ്ടിക്കുക, scaffold ഉം ജനറേറ്റ് ചെയ്ത artifacts ഉം എഴുതുക |
| `COMPILE` | ബാക്ക്-എൻഡ് `mvn package` / ഫ്രണ്ട്-എൻഡ് ബിൽഡ്, ഇൻ-മെമ്മറി കംപൈൽ സെൽഫ്-ചെക്ക് |
| `MENU` | മെനു പെർമിഷനുകൾ ഉത്പാദിപ്പിച്ച് രജിസ്റ്റർ ചെയ്യുക (ഡ്യുവൽ ഔട്ട്‌ലെറ്റുകൾ: artifact SQL / ഇന്റേണൽ ഡയറക്റ്റ് റൈറ്റ്) |
| `ROUTE` | ഫ്രണ്ട്-എൻഡ് റൂട്ട് ഫ്രാഗ്‌മെന്റുകൾ പുനർനിർമ്മിക്കുക |

ഏതെങ്കിലും ഘട്ടത്തിലെ പരാജയം എളുപ്പത്തിൽ കണ്ടെത്താൻ വായിക്കാവുന്ന ഒരു എററോടെ ഉടൻ നിർത്തുന്നു.

---

## 8. സിസ്റ്റം ആർക്കിടെക്ചർ

ഫ്രണ്ട്-എൻഡും ബാക്ക്-എൻഡും വേർപെടുത്തി, ചേർന്ന് പ്രവർത്തിക്കുന്ന രണ്ട് റിപ്പോകൾ:

| റിപ്പോസിറ്ററി | റോൾ | സ്റ്റാക്ക് |
| --- | --- | --- |
| `codegen-server` | ബാക്ക്-എൻഡ്: ജനറേഷൻ എഞ്ചിൻ, ടെംപ്ലേറ്റുകൾ, വാലിഡേഷൻ, ലെഡ്ജർ, പൈപ്പ്ലൈൻ | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | ഫ്രണ്ട്-എൻഡ്: കൺസോൾ (ടേബിൾ കോൺഫിഗ് / ടെംപ്ലേറ്റുകൾ / ലെഡ്ജർ / സിസ്റ്റം മാനേജ്മെന്റ്) | Vue 2.7 · Element UI · axios · Vue Router (history mode) |

ബാക്ക്-എൻഡ് ലെയറിംഗ്:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**ടാർഗെറ്റ് പ്രോജക്റ്റ് നൽകേണ്ട അടിത്തറ കഴിവുകൾ** (ജനറേറ്റർ ബിസിനസ് കോഡ് മാത്രമേ ഉത്പാദിപ്പിക്കൂ; കാണാതായ അടിത്തറ നിശബ്ദം പരാജയപ്പെടുന്നു):

1. MyBatis-Plus പേജിനേഷൻ പ്ലഗിൻ (`PaginationInnerInterceptor`)
2. `Result` / `BaseEntity` / `PageQuery` പോലുള്ള അടിത്തറ ക്ലാസുകൾ (package prefix `root-package`-നോട് പൊരുത്തപ്പെടണം)
3. `toPageData` ഉള്ള ഫ്രണ്ട്-എൻഡ് `utils/request.js` (`Page{records,total}`-നെ `{list,total}` ആക്കി മാറ്റുന്നു)
4. ഡിപൻഡൻസികൾ `springdoc-openapi-starter-webmvc-ui` (ജനറേറ്റ് ചെയ്ത കോഡ് `@Operation` ഉപയോഗിക്കുന്നു) കൂടാതെ `poi-ooxml` (Excel)

---

## 9. ടെക് സ്റ്റാക്ക് ഒറ്റനോട്ടത്തിൽ

| ഡൈമൻഷൻ | തിരഞ്ഞെടുപ്പ് |
| --- | --- |
| ബാക്ക്-എൻഡ് ഭാഷ / ഫ്രെയിംവർക്ക് | Java 21 / Spring Boot 3.2.5 |
| പേർസിസ്റ്റൻസ് | MyBatis-Plus 3.5.5 |
| ടെംപ്ലേറ്റ് എഞ്ചിൻ | FreeMarker (22 seed ടെംപ്ലേറ്റുകൾ, ഓൺലൈൻ എഡിറ്റിംഗ് + പതിപ്പ് റോൾബാക്ക്) |
| സെക്യൂരിറ്റി | JWT ഓതന്റിക്കേഷൻ |
| ഡാറ്റാബേസ് | MySQL 8 |
| ഫ്രണ്ട്-എൻഡ് | Vue 2.7 + Element UI |
| ലോക്കൽ AI | qwen2.5-coder:7b (മോഡലിംഗ് / ശുപാർശ ചെയ്ത കോൺഫിഗ് / seed-data വർദ്ധനവ്) |
| API ഡോക്സ് | springdoc-openapi (Swagger UI) |

---

## 10. ക്വിക്ക് സ്റ്റാർട്ട്

### 1. ഡാറ്റാബേസ് സൃഷ്ടിക്കുക (13 പ്ലാറ്റ്ഫോം ടേബിളുകൾ + ഡിഫോൾട്ട് അക്കൗണ്ട് admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. ടാർഗെറ്റ് പ്രോജക്റ്റ് കോൺഫിഗർ ചെയ്യുക

കോൺഫിഗ് ടെംപ്ലേറ്റ് കോപ്പി ചെയ്ത് നിങ്ങളുടെ ലോക്കൽ പാതകളിലേക്ക് ക്രമീകരിക്കുക:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` `.gitignore`-നാൽ ഒഴിവാക്കപ്പെടുകയും കമ്മിറ്റ് ചെയ്യപ്പെടുകയുമില്ല. പ്രധാന സെറ്റിംഗുകൾ:

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # write-to-disk base for artifacts
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # frontend subdirectory for monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefix for foundation-class imports in artifacts
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

സെൽഫ്-ചെക്ക് ഡിപൻഡൻസി ഡയറക്ടറി ഇങ്ങനെ എക്സ്പോർട്ട് ചെയ്യുക:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. ബാക്ക്-എൻഡ് ആരംഭിക്കുക

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

സർവീസ് ഡിഫോൾട്ട് പോർട്ട് **8120**; API ഡോക്സ് `/swagger-ui/index.html`-ൽ.

### 4. ഫ്രണ്ട്-എൻഡ് ആരംഭിക്കുക

```bash
cd ../codegen-web
npm install
npm run serve
```

ഫ്രണ്ട്-എൻഡ് ഡിഫോൾട്ട് പോർട്ട് **9530**, `/dev-api`-നെ ബാക്ക്-എൻഡ് പോർട്ട് 8120-ലേക്ക് പ്രോക്സി ചെയ്യുന്നു. ഡിഫോൾട്ട് അക്കൗണ്ട് `admin / admin123`.

### 5. പ്രധാന കോൺഫിഗറേഷൻ ഇനങ്ങൾ

| സെറ്റിംഗ് | വിവരണം |
| --- | --- |
| `gen.output.*` | Artifact റൈറ്റ് പാതകൾ, പാക്കേജ് നാമങ്ങൾ, മൊഡ്യൂൾ മാപ്പിംഗ് |
| `gen.selfcheck.classpath` | ഇൻ-മെമ്മറി കംപൈൽ സെൽഫ്-ചെക്കിനുള്ള ഡിപൻഡൻസി classpath, `dir/*` പിന്തുണയ്ക്കുന്നു |
| `gen.menu.outlet` | `artifact` മെനു SQL & റൂട്ട് ഫ്രാഗ്‌മെന്റുകൾ മാത്രം ഉത്പാദിപ്പിക്കുന്നു; `internal` പ്ലാറ്റ്ഫോമിന്റെ സ്വന്തം `sys_menu` എഴുതുകയും ചെയ്യുന്നു |

---

## 11. ഡോക്യുമെന്റേഷൻ മാപ്പ്

| ഡോക്യുമെന്റ് | ഉള്ളടക്കം |
| --- | --- |
| `docs/代码生成器-操作手册.md` (ചൈനീസ്) | ഡിപ്ലോയ്മെന്റ്, 5-മിനിറ്റ് സ്റ്റാർട്ട്, ടേബിൾ കോൺഫിഗ്, ബാച്ച് ഇൻടേക്ക്, ടെംപ്ലേറ്റ് മാനേജ്മെന്റ്, ലെഡ്ജർ, API കാറ്റലോഗ്, പൂർണ്ണ പൈപ്പ്ലൈൻ (സ്ക്രീൻഷോട്ടുകളോടു കൂടി) |
| `docs/典型场景操作指南-演示后台demo.md` (ചൈനീസ്) | 5 സാധാരണ സാഹചര്യങ്ങൾ ഉൾക്കൊള്ളുന്ന 6 ടേബിളുകൾ, റൺ ചെയ്യാവുന്ന demo ഉം യഥാർത്ഥ-റൺ സ്ക്രീൻഷോട്ടുകളും ഉള്ളത് |
| `docs/项目介绍.md` | പ്രോജക്റ്റ് ആമുഖ ഡോക്യുമെന്റ്, ഈ ഫയലിന് തുല്യമായ സോഴ്സ് |
| `docs/代码生成器-Vx.x-规划.md` | V3.1 → V7.3 വരെയുള്ള പതിപ്പ് പ്ലാനുകളും ഫീച്ചർ ഇൻവെന്ററികളും |
| `codegen-web/README.md` | ഫ്രണ്ട്-എൻഡ് കൺസോൾ ക്വിക്ക്-സ്റ്റാർട്ട് റഫറൻസ് |

---

## 12. പതിപ്പ് ചരിത്രം

| പതിപ്പ് | തീം / പ്ലാൻ ഡോക്യുമെന്റ് | ലാൻഡിംഗ് കമ്മിറ്റ് |
| --- | --- | --- |
| V1.2 / V1.3 | വൺ-ക്ലിക്ക് ജനറേഷൻ / ബാച്ച് / റോൾബാക്ക്; ഡിഫ് പ്രിവ്യൂ & സ്ട്രക്ചർ സിങ്ക് | — (`src/api/gen.js` കമന്റുകളിൽ മാത്രം) |
| V2.0 | ഡൈനമിക് റൂട്ടുകൾ (ബാക്ക്-എൻഡ് ഡെലിവർ ചെയ്ത മെനു ട്രീ) | — |
| V3.0 / V3.1 | മൾട്ടി-സിസ്റ്റം ലെഡ്ജർ; മൾട്ടി-സിസ്റ്റം ലൂപ്പ് + ആദ്യ ഓട്ടോമേറ്റഡ് ടെസ്റ്റുകൾ | V3.1 (174 ലൈനുകൾ) |
| V4.2 | ട്രേസബിൾ, റോൾബാക്ക്-ചെയ്യാവുന്ന ജനറേഷൻ റിസൾട്ടുകൾ (റൈറ്റ് ലെഡ്ജർ) | V4.2 (292 ലൈനുകൾ) |
| V4.3 | ടെംപ്ലേറ്റ് റിഗ്രഷൻ & ക്വാളിറ്റി ഗാർഡ്‌റെയിൽസ് | V4.3 (200 ലൈനുകൾ) |
| V4.4 | വൺ-മാസ്റ്റർ-മേനി-ഡീറ്റെയിൽ, കാസ്കേഡ്-റൈറ്റ് കംപ്ലീറ്റ്നസ്, ടെംപ്ലേറ്റ് ലെയ്ൻസ് | V4.4 (154 ലൈനുകൾ) |
| V4.5 | A4 പ്രിന്റ് ഡോക്യുമെന്റുകൾ (സീറോ തേർഡ്-പാർട്ടി ഡിപൻഡൻസികൾ) | V4.5 (127 ലൈനുകൾ) |
| V5.0 | ഒരു വാചകത്തിൽ സിസ്റ്റം കെട്ടിപ്പടുക്കൽ: പൈപ്പ്ലൈൻ + AI മോഡലിംഗ് + scaffold ബേസ് | — |
| V5.1 | DDL ഓഡിറ്റ് കോളങ്ങൾ ഓട്ടോ-കംപ്ലീഷൻ | V5.1 (100 ലൈനുകൾ) |
| V5.2 | പൈപ്പ്ലൈൻ seed-data ഇനിഷ്യലൈസേഷൻ സ്റ്റെപ്പ് | V5.2 (107 ലൈനുകൾ), 84ec8dd |
| V5.5 | മൾട്ടി-മൊഡ്യൂൾ പ്രോജക്റ്റ് ജനറേഷൻ റാപ്പ്-അപ്പ് | af73dd3 |
| V6.0 | മൈക്രോസർവീസ് ഇൻഫ്രാസ്ട്രക്ചർ (Nacos / Gateway / Feign, മൂന്നാമത്തെ മോഡ് "micro") | V6.0 (111 ലൈനുകൾ), 1deecbd |
| V6.1 | Activiti വർക്ക്ഫ്ലോ ഡ്യുവൽ എഞ്ചിൻ | V6.1 (100 ലൈനുകൾ), 02b671e |
| V6.2 | Artifact ഡെലിവറി സർഫേസ് (CLI / ഡിപ്ലോയ്മെന്റ് artifacts / കോൺഫിഗ് സെക്യൂരിറ്റി) | V6.2 (114 ലൈനുകൾ), 02b671e |
| V6.3 | പേയ്‌മെന്റ് കപ്പാസിറ്റി പാക്ക് (WeChat / Alipay + Mock) | V6.3 (104 ലൈനുകൾ), 1c4ee6a, 22eede5 |
| V6.4 | പെർമിഷൻ & സെക്യൂരിറ്റി ബേസ്‌ലൈൻ (ടോക്കൺ പേഴ്സിസ്റ്റൻസ് / BCrypt / API പെർമിഷനുകൾ / ലോഗിൻ പ്രൊട്ടക്ഷൻ) | V6.4 (107 ലൈനുകൾ), 007a202 |
| V6.5 | മൊബൈൽ uni-app (H5 + WeChat Mini Program) | V6.5 (105 ലൈനുകൾ), 779ac41 |
| V6.6 | ഫയൽ കപ്പാസിറ്റി പാക്ക് | 98f3c09 |
| V6.7 | Artifact ടെസ്റ്റ് ഫാക്ടറി അക്സപ്റ്റൻസ് (test_enabled tri-state + H2 സ്കെലിറ്റൺ) | V6.7 (134 ലൈനുകൾ), 4e7d318 |
| V6.8 | Artifact മെയിന്റൈനബിലിറ്റി (dryRun / പ്രൊട്ടക്റ്റഡ് skip / ബ്രാൻഡ് പാരമീറ്ററൈസേഷൻ) | V6.8 (141 ലൈനുകൾ), be6f858 |
| V6.9 | പ്ലാറ്റ്ഫോം ഡോക്യുമെന്റേഷൻ സെന്റർ (ബാക്ക്-എൻഡ് ബേസ്, ഫ്രണ്ട്-എൻഡ് അതേ ബാച്ചിൽ പൂർത്തിയാക്കി) | — |
| V6.10 | പ്ലാറ്റ്ഫോം ഒബ്സർവബിലിറ്റി: Actuator + ഇൻ-മെമ്മറി മെട്രിക് വിൻഡോ + എംബഡഡ് മോണിറ്റർ പേജ് | — |
| V7.0 | AI പേജ് & മെനു ശുപാർശകൾ (പ്രിവ്യൂ / അപ്ലൈ) | V7.0 (94 ലൈനുകൾ), 45f2670 |
| V7.1 | Seed-data AI വർദ്ധനവ് (AI സെമാന്റിക്സ് ഉടമസ്ഥതയെടുക്കുന്നു, റൂളുകൾ സ്ട്രക്ചർ ഉടമസ്ഥതയെടുക്കുന്നു) | V7.1 (42 ലൈനുകൾ), 5ca6e72 |
| V7.2 | വിജയകരമായ പൈപ്പ്ലൈനിന് ശേഷം `db/`-ലേക്ക് എഴുതിയ SQL ഡെലിവറബിളുകൾ | eebff88 |
| **V7.3** | **ഡെലിവറി വിശ്വാസ്യത: സെൽഫ്-ചെക്ക് കംപൈൽ ചെയ്ത artifact ടെസ്റ്റുകൾ / യൂണിഫൈഡ് SQL ഡെലിവറി ഔട്ട്‌ലെറ്റ് / ടേബിൾ-തിരിഞ്ഞുള്ള AI ശുപാർശകൾ** | **V7.3 (138 ലൈനുകൾ)** |
| **V7.4** | റിപ്പീറ്റബിൾ ഡെലിവറി: ഒരു ശൂന്യ ഡാറ്റാബേസിൽ നിന്ന് ഡിപ്ലോയ് ചെയ്യാവുന്ന, ഓരോ കമ്മിറ്റും റൺ ചെയ്യാവുന്നതായി ഗ്യാരണ്ടി ചെയ്യപ്പെട്ടിട്ടുള്ളത് | c306e29 |
| **V7.5** | പ്രോജക്റ്റ് ഭാഷ കോൺഫിഗറേഷൻ: 206 ഭാഷകളുടെ പട്ടിക / സ്ഥിര ഭാഷ / പ്രവർത്തനക്ഷമമാക്കിയ ഭാഷകൾ, ജനറേറ്റ് ചെയ്ത കോഡിൽ lang & dir ഇൻജക്ഷൻ | 7a9afa0 + abac56d |
| **V8.0** | പൂർണ്ണ പേജ് i18n: 206 ഭാഷാ ഫ്രെയിംവർക്ക് (8 പ്രൊഫഷണൽ വിവർത്തനം), OFF സീറോ-ഡിഫ് കരാറുള്ള ഇരട്ട-ട്രാക്ക് i18n | 9e5dd57 → 7d57aea |
| **V8.1** | ഗുണനിലവാര ശക്തിപ്പെടുത്തൽ: ഓഫ്‌ലൈൻ കരാർ ഹാർനസ് (9 മോഡുകൾ, OFF സീറോ-ഡിഫ്) + i18n-core യൂണിറ്റ് ടെസ്റ്റുകൾ + CI ഗേറ്റ് + സത്യസന്ധമായ README വിഭജനം (ഫ്രെയിംവർക്ക് 206 / വിവർത്തനം 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | ജനറേറ്റ് ചെയ്ത ആർട്ടിഫാക്ടുകൾ Vue 3 + Element Plus + Vite ലേക്ക് ആധുനികവൽക്കരിച്ചു; റൺടൈമിൽ സ്ഥിരീകരിച്ചു (40/40 അസെർഷനുകൾ, വിജയകരമായ vite build, ar-SA RTL ഉൾപ്പെടെ 14 സ്ക്രീൻഷോട്ടുകൾ); 3 റൺടൈം വൈകല്യങ്ങൾ ശരിയാക്കി | 93a12d5 + e49330b  |
| **V9.1** | **പരിപാലനക്ഷമത പുനഃക്രമീകരണം · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **നിലവിലെ പതിപ്പ്** |

---

> **V6.10 പ്ലാറ്റ്ഫോം ഒബ്സർവബിലിറ്റി**: Actuator + Micrometer ഇൻറഗ്രേറ്റ് ചെയ്യുന്നു, ഇൻ-മെമ്മറി റിംഗ് ബഫറിലേക്ക് (30-മിനിറ്റ് വിൻഡോ) ഓരോ 5 സെക്കൻഡിലും സാമ്പിൾ ചെയ്യൽ, പുതിയ എംബഡഡ് "System Monitor" പേജ് കൂടാതെ — ബാക്ക്-എൻഡ് `/monitor/*` അഗ്രിഗേഷൻ APIs + ഫ്രണ്ട്-എൻഡ് ECharts പാനലുകൾ. `health/info/metrics` എൻഡ്‌പോയിന്റുകൾ മാത്രമേ whitelist-ൽ ആകൂ; `/actuator/health` ടോക്കൺ-ഫ്രീ ആണ്, `metrics` മുതലായവ auth ആവശ്യപ്പെടുന്നു; `env/beans/heapdump/threaddump/loggers` ഒരിക്കലും എക്സ്പോസ് ചെയ്യപ്പെടില്ല. സാമ്പിൾ വിൻഡോ പെർ-പ്രോസസ്സ് മാത്രം, റീസ്റ്റാർട്ടിൽ നഷ്ടപ്പെടുന്നു, ഇൻസ്റ്റൻസുകൾക്കിടയിൽ പങ്കിടുന്നില്ല. വിശദാംശങ്ങൾക്ക് `docs/可观测性说明.md` (ചൈനീസ്) കാണുക.

## 13. പ്രോജക്റ്റ് സ്റ്റാറ്റസ്

- നിലവിലെ പ്ലാൻ ചെയ്ത പതിപ്പ് ലൈൻ **V9.1** എത്തിയിരിക്കുന്നു (കഴിവുകൾ തുടർച്ചയായി ഇറ്ററേറ്റ് ചെയ്യപ്പെടുന്നു: AI മോഡലിംഗ്, seed-data വർദ്ധനവ്, കപ്പാസിറ്റി-പാക്ക് സിസ്റ്റം മുതലായവ).
- ഒരു കൂട്ടാളി demo പ്രോജക്റ്റ് `demo_admin` (ബാക്ക്-എൻഡ് 8140 / ഫ്രണ്ട്-എൻഡ് 9040) ട്രീ / CRUD / മാസ്റ്റർ-ഡീറ്റെയിൽ / റീഡ്-ഓൺലി / പ്രിന്റ് സാഹചര്യങ്ങൾ ഉൾക്കൊള്ളുന്നു, വെരിഫിക്കേഷന് സ്വയം റൺ ചെയ്യാവുന്നതാണ്.
- രണ്ട് റിപ്പോകളും Gitee-ൽ ഹോസ്റ്റ് ചെയ്തിരിക്കുന്നു: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. രചയിതാവ് & സപ്പോർട്ട്

- **രചയിതാവ്**: ഒരു ചൈനീസ് പ്രോഗ്രാമർ (സ്വതന്ത്ര ഡെവലപ്പർ)
- **ഇമെയിൽ**: `hustxiayuhong@126.com`
- **ടെക്നിക്കൽ സപ്പോർട്ട്**: ദയവായി നിങ്ങളുടെ ഇമെയിൽ **ചൈനീസിൽ** എഴുതുക, പ്രശ്നത്തിന്റെ വിവരണം, റൺടൈം എൻവയൺമെന്റ് (JDK / MySQL / പതിപ്പ്), റീപ്രൊഡക്ഷൻ സ്റ്റെപ്പുകൾ എന്നിവ ഉൾപ്പെടുത്തി

> ഞാൻ ഓരോ ഇമെയിലും വായിച്ച് മറുപടി നൽകുന്നു. ബഗ് റിപ്പോർട്ടുകൾ, ഫീച്ചർ റിക്വസ്റ്റുകൾ, കോളബറേഷൻ എന്നിവ എല്ലാം സ്വാഗതം ചെയ്യുന്നു.

---

## 15. സമാന ടൂളുകളുമായി താരതമ്യം

CodeGen is neither a mere code snippet generator nor a full-stack low-code platform — it sits in a specific niche. Here is how it positions itself relative to the more commonly known tools in the Spring Boot / Java ecosystem.

### vs. Snippet Generators (JPA Buddy · MapStruct · Spring Initializr)

Tools like **JPA Buddy**, **MapStruct**, and **Spring Initializr** are great for scaffolding and small ergonomic wins, but they only produce **fragments**: an entity here, a mapper there, some DTO conversion helpers, or an empty starter project. You still need to wire up the CRUD pages, menus, routes, and permission codes by hand — and when the table changes, you re-do it.

CodeGen, by contrast, produces a **zero-edit, runnable vertical slice**: full backend (Entity → Mapper → Service → Controller → DTO/VO) + full frontend (`api.js` + Vue pages + A4 print page) + menu + route registration. Bring your DDL, click once, and the feature is live.

### vs. Scaffold Frameworks (JeecgBoot · RuoYi · dlow)

**JeecgBoot**, **RuoYi**, and similar frameworks ship a complete admin scaffold first, then bolt a code generator on top. You are required to **fork their scaffold**, carry its conventions, its authentication layer, its menu schema, its file layout, and — critically — its upgrade story. The generator **inherits the parent framework's biases** (e.g., dynamic datasource conventions, Shiro/Spring Security choices, front-end tech-lock).

CodeGen takes the opposite stance: **it never touches your project's infrastructure**. It assumes your project already has `Result`, `BaseEntity`, `PageQuery`, `PaginationInnerInterceptor`, and a `utils/request.js` that returns `{list, total}`. It only **drops business code into your existing layout**, alongside whatever auth stack, UI, and conventions you already chose. No fork. No convention hijack. Clean, surgical.

### vs. Built-in Generators (Yunzhai · JeecgBoot built-in · RuoYi built-in)

Many scaffolds now include a code generator inside them. The generated code works — but quality gates are usually weak: no real compilation check, no structured contract test, no template versioning, no rollback, and no change ledger. When a template change produces broken output, you won't find out until runtime.

CodeGen's **quality-gate chain** is its differentiator:

| Step | What it actually does |
| --- | --- |
| Preview & diff | Generates to memory, renders per-file diffs against disk, you confirm before anything is written |
| Write with backup | Every touched file is copied to `.gen-backup/<batchNo>/` before being overwritten |
| Marked-zone merge | Hand-written code inside `// gen:begin-end` blocks is preserved; only generated zones are replaced |
| In-memory self-check | A real `javac` compile of all generated back-end artifacts (not a regex lint) — nothing lands on disk unless it compiles |
| Independent class-path compile | Generated integration tests are compiled against your target project's classpath — missing dependencies fail at write time, not runtime |
| Four-state ledger | `gen_output` records every file as **created / merged / skipped / failed**, with byte count, MD5, and backup path |
| Rollback by batch | Any batch can be rolled back in one click, restoring exactly the state before that generation ran |

These seven links — preview → backup → merge → javac self-check → class-path compile → ledger → rollback — are **not present in the built-in generators of any popular scaffold framework** the author is aware of.
