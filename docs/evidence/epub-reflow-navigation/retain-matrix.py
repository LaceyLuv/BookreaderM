from pathlib import Path
import json,shutil,subprocess,sys,importlib.util,xml.etree.ElementTree as E
sha,buildrun,devicerun=sys.argv[1:]
repo=Path('/workspace/BookreaderM');scratch=Path('/workspace/scratch/resume-ci');out=repo/f'docs/evidence/epub-reflow-navigation/ci-{sha[:7]}';out.mkdir(exist_ok=True)
manifest=json.loads(subprocess.check_output(['git','show',sha+':scripts/required-tests.json'],cwd=repo))
(out/'required-tests.json').write_text(json.dumps(manifest,indent=2)+'\n')
spec=importlib.util.spec_from_file_location('check',repo/'scripts/check-test-results.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
summary={'source_sha':sha,'build':{},'device':{}}
for run in [buildrun,devicerun]:
 src=scratch/run
 assert json.loads((src/'workflow.json').read_text())['head_sha']==sha
 for f in src.iterdir():
  if f.is_file() and f.suffix in ['.json','.sha256']:
   d=out/run/f.name; d.parent.mkdir(exist_ok=True); shutil.copy2(f,d)
 for f in src.rglob('*'):
  if f.is_file() and f.suffix in ['.xml','.log','.txt','.json'] and ('mobile-device-evidence' in str(f) or 'mobile-build-evidence' in str(f)) and '/reports/' not in str(f):
   d=out/run/f.relative_to(src);d.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,d)
for api in [26,34,36]:
 r=scratch/devicerun/f'mobile-device-evidence-api-{api}';d=r/'build/device-evidence'
 assert (d/'source.log').read_text().strip()==sha and not (d/'source-status.log').read_text().strip()
 root=E.parse(next(r.rglob('TEST-emulator*.xml'))).getroot()
 assert set(map(tuple,manifest['android-device'])).issubset({(t.get('classname'),t.get('name')) for t in root.iter('testcase')})
 s={k:int(root.get(k,0)) for k in ['tests','failures','errors','skipped']};s.update(failed_cases=[],host_status='NOT_RUN')
 for t in root.iter('testcase'):
  for f in t.findall('failure')+t.findall('error'): s['failed_cases'].append({'class':t.get('classname'),'name':t.get('name'),'trace':f.text})
 if not any(s[k] for k in ['failures','errors','skipped']):
  m.verify('android-device',r/'androidApp/build/outputs/androidTest-results',manifest);hosts={}
  for name,suite in [('reader','android-host'),('epub','android-host-epub'),('comic','android-host-comic')]:
   h=d/f'{name}-host-process';n,_=m.verify(suite,h,manifest);hs=json.loads((h/'summary.json').read_text());old=(h/'live-pid-before-stop.log').read_text().strip();new=(h/'cold-pid.log').read_text().strip()
   assert hs['status']=='PASS' and all(c['exit_code']==0 for c in hs['commands']) and old!=new and old.isdigit() and new.isdigit() and (h/'pid-after-force-stop.log').read_text().strip()=='absent'
   hosts[name]={'count':n,'old_pid':old,'new_pid':new,'absent_after_stop':True}
  s.update(host_status='PASS',hosts=hosts)
 summary['device'][str(api)]=s
r=scratch/buildrun/'mobile-build-evidence'
for suite,path in [('android-unit','androidApp/build/test-results/testDebugUnitTest'),('shared-jvm','shared/build/test-results/jvmTest'),('epub-unit','epubAndroid/build/test-results/testDebugUnitTest'),('shared-enospc','shared/build/test-results/jvmEnospcTest')]: summary['build'][suite]=m.verify(suite,r/path,manifest)[0]
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({'source':sha,'build':summary['build'],'device':{k:{a:v[a] for a in ['tests','failures','errors','skipped','host_status']} for k,v in summary['device'].items()}}))
