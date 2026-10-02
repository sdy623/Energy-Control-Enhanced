"""Production-only two-block shutdown reproducer; no test mod or client is loaded."""
from pathlib import Path
import subprocess,os,time,json,sys,hashlib,uuid,socket
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent/'Energy-Control-neoforge-1.21.1'
label=sys.argv[1];run=ROOT/'minimal-runs'/(time.strftime('%Y%m%d-%H%M%S')+'-'+label+'-'+uuid.uuid4().hex[:6]);run.mkdir(parents=True)
(run/'eula.txt').write_text('eula=true\n')
(run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25588\nonline-mode=false\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngamemode=creative\ndifficulty=peaceful\nview-distance=3\nsimulation-distance=3\n')
with socket.socket() as s:s.bind(('127.0.0.1',25588))
env=os.environ.copy();env['JAVA_HOME']=r'C:\Program Files\Zulu\zulu-21';env['GRADLE_USER_HOME']=str(ROOT.parent/'Energy-Control/.gradle-user-home')
cmd=['cmd.exe','/d','/c','gradlew.bat','--console=plain','--max-workers=2','-I',str(ROOT/'minimal.init.gradle'),'-PshutdownQaDir='+str(run),'-Dorg.gradle.java.installations.paths='+env['JAVA_HOME'],'-Dorg.gradle.java.installations.auto-download=false','runServer']
result={'label':label,'directory':str(run),'minecraft':'1.21.1','neoforge':'21.1.209','qa_mod_loaded':False,'passed':False,'jar_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (REPO/'build/libs').glob('*.jar')}}
log=(run/'process.log').open('wb');p=subprocess.Popen(cmd,cwd=REPO,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
(run/'launch.json').write_text(json.dumps({'pid':p.pid,'command':cmd}))
def content():
 f=run/'logs/latest.log';return f.read_text(errors='replace') if f.exists() else ''
def send(c):p.stdin.write((c+'\n').encode());p.stdin.flush()
try:
 print('MINIMAL_RUN',run.name,flush=True);deadline=time.monotonic()+180
 while 'Done (' not in content():
  if p.poll() is not None:raise RuntimeError('server startup failed')
  if time.monotonic()>deadline:raise TimeoutError('startup timeout')
  time.sleep(1)
 send('forceload add -1 -2 0 -2');time.sleep(3)
 send('setblock 0 -59 -2 energycontrol:info_panel_advanced_extender[facing=south]')
 send('setblock -1 -59 -2 energycontrol:info_panel_advanced[facing=south]')
 time.sleep(8)
 send('data get block -1 -59 -2');send('data get block 0 -59 -2');time.sleep(2)
 send('save-all flush');time.sleep(2)
 result['commands_complete']=True;start=time.monotonic();send('stop');p.stdin.close()
 try:p.wait(timeout=20)
 except subprocess.TimeoutExpired:
  ps="Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*Energy-Control-neoforge-1.21.1*' } | Select-Object -ExpandProperty ProcessId"
  for pid in subprocess.check_output(['powershell','-NoProfile','-Command',ps],text=True).split():
   if pid.isdigit():
    d=subprocess.run([str(Path(env['JAVA_HOME'])/'bin/jcmd.exe'),pid,'Thread.print'],capture_output=True,timeout=15)
    (run/('threads-'+pid+'.txt')).write_bytes(d.stdout+d.stderr)
  raise TimeoutError('production-only two-block server did not stop within 20s')
 result['shutdown_seconds']=round(time.monotonic()-start,3)
 t=content();result['passed']=p.returncode==0 and 'All dimensions are saved' in t and 'screenData' in t
except Exception as e:result['error']=str(e)
finally:
 if p.poll() is None:subprocess.run(['taskkill','/PID',str(p.pid),'/T','/F'],capture_output=True);p.wait(timeout=30)
 log.close();result['exit']=p.returncode;(run/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2),flush=True)
raise SystemExit(0 if result['passed'] else 1)
