from pathlib import Path
import importlib.util,json,subprocess,sys
repo=Path('/workspace/BookreaderM'); scratch=Path('/workspace/scratch/resume-ci')
sha,buildrun,devicerun=sys.argv[1:]
spec=importlib.util.spec_from_file_location('check',repo/'scripts/check-test-results.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
manifest=json.loads(subprocess.check_output(['git','show',sha+':scripts/required-tests.json'],cwd=repo))
summary={'source_sha':sha,'device':{},'build':{}}
for api in (26,34,36):
 root=scratch/devicerun/f'mobile-device-evidence-api-{api}';d=root/'build/device-evidence'
 assert (d/'source.log').read_text().strip()==sha
 assert not (d/'source-status.log').read_text().strip()
 assert (d/'device-api.log').read_text().strip()==str(api)
 count,cases=m.verify('android-device',root/'androidApp/build/outputs/androidTest-results',manifest)
 hosts={}
 for name,suite in [('reader','android-host'),('epub','android-host-epub'),('comic','android-host-comic')]:
  h=d/f'{name}-host-process';num,_=m.verify(suite,h,manifest);s=json.loads((h/'summary.json').read_text())
  assert s['status']=='PASS' and all(c['exit_code']==0 for c in s['commands'])
  old=(h/'live-pid-before-stop.log').read_text().strip();new=(h/'cold-pid.log').read_text().strip()
  assert old==s['old_pid'] and new==s['cold_launcher_pid'] and old.isdigit() and new.isdigit() and old!=new
  assert (h/'pid-after-force-stop.log').read_text().strip()=='absent'
  hosts[name]={'cases':num,'old_pid':old,'cold_pid':new,'absent_after_force_stop':True}
 obs=json.loads((d/'epub-restore-observations.log').read_text());assert [o['stage'] for o in obs]==['search','recreation','font-size','reopen']
 for o in obs: assert 'p179' in o['viewport']['visibleParagraphs']
 summary['device'][str(api)]={'ordinary':count,'hosts':hosts,'failures':0,'errors':0,'skipped':0,'observations':[{'stage':o['stage'],'font_size':o['viewport']['fontSize'],'progression':o['actual']['locations']['progression'],'p179_visible':True} for o in obs]}
root=scratch/buildrun/'mobile-build-evidence'
assert json.loads((scratch/buildrun/'workflow.json').read_text())['head_sha']==sha
for suite,path in [('android-unit','androidApp/build/test-results/testDebugUnitTest'),('shared-jvm','shared/build/test-results/jvmTest'),('epub-unit','epubAndroid/build/test-results/testDebugUnitTest'),('shared-enospc','shared/build/test-results/jvmEnospcTest')]:
 count,_=m.verify(suite,root/path,manifest);summary['build'][suite]=count
(scratch/('verified-'+sha[:7]+'.json')).write_text(json.dumps(summary,indent=2)+'\n')
print('PASS',sha,summary['build'],{api:{'ordinary':v['ordinary'],'host':sum(h['cases'] for h in v['hosts'].values())} for api,v in summary['device'].items()})
