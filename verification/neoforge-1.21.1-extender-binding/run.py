"""Launch only this task's dedicated Forge server/client; no production game paths."""
from pathlib import Path
import subprocess,os,time,json,uuid,socket,hashlib,sys,shutil
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent/'Energy-Control-neoforge-1.21.1'
runid=time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6]
RUN=ROOT/'run'/runid
for part in ['server','client']:(RUN/part).mkdir(parents=True,exist_ok=False)
reload_from = sys.argv[1] if len(sys.argv)>1 else None
if reload_from:
 assert all(c.isalnum() or c=='-' for c in reload_from)
 shutil.copytree(ROOT/'run'/reload_from/'server/world',RUN/'server/world')
serverdir=RUN/'server';clientdir=RUN/'client'
(serverdir/'eula.txt').write_text('eula=true\n# Official Minecraft EULA previously accepted by user for isolated tests.\n')
(serverdir/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25587\nonline-mode=false\nmax-players=2\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngamemode=creative\ndifficulty=peaceful\nspawn-protection=0\nview-distance=5\nsimulation-distance=5\nmax-tick-time=120000\n')
(clientdir/'options.txt').write_text('soundCategory_master:0.0\nnarrator:0\npauseOnLostFocus:false\ntutorialStep:none\nrenderDistance:5\nsimulationDistance:5\nguiScale:2\nmaxFps:60\nfullscreen:false\n')
with socket.socket() as probe:probe.bind(('127.0.0.1',25587))
env=os.environ.copy();env['JAVA_HOME']=r'C:\Program Files\Zulu\zulu-21';env['GRADLE_USER_HOME']=str(ROOT.parent/'Energy-Control/.gradle-user-home')
base=['cmd.exe','/d','/c','gradlew.bat','--console=plain','--max-workers=2','-I',str(ROOT/'qa.init.gradle'),'-PportableQa','-PportableQaRun='+runid,'-Duser.language=en','-Duser.country=US','-Dorg.gradle.java.installations.paths='+env['JAVA_HOME'],'-Dorg.gradle.java.installations.auto-download=false']
if reload_from: base.append('-PportableQaReload')
result={'minecraft':'1.21.1','neoforge':'21.1.209','reload_from':reload_from,'run_id':runid,'directory':str(RUN),'passed':False,'server_exit':None,'client_exit':None,'production_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=REPO,text=True).strip()}
result['working_tree_diff_sha256']=hashlib.sha256(subprocess.check_output(['git','diff','--binary'],cwd=REPO)).hexdigest()
result['production_jar_sha256']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (REPO/'build/libs').glob('*.jar')}
server=client=None
handles=[]
def start(task,name):
 f=(RUN/(name+'-process.log')).open('wb');handles.append(f)
 p=subprocess.Popen(base+[task],cwd=REPO,env=env,stdin=subprocess.PIPE,stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 (RUN/(name+'-launch.json')).write_text(json.dumps({'pid':p.pid,'command':base+[task],'cwd':str(REPO)},indent=2))
 return p
def content(path):return path.read_text(encoding='utf-8',errors='replace') if path.exists() else ''
try:
 print('RUN',runid,flush=True)
 server=start('runServer','server')
 deadline=time.monotonic()+300
 while 'Done (' not in content(serverdir/'logs/latest.log'):
  if server.poll() is not None:raise RuntimeError('server exited before ready')
  if time.monotonic()>deadline:raise TimeoutError('server ready timeout')
  time.sleep(1)
 print('SERVER_READY',flush=True)
 client=start('runClient','client')
 deadline=time.monotonic()+420
 while client.poll() is None:
  if time.monotonic()>deadline:raise TimeoutError('client acceptance timeout')
  time.sleep(1)
 result['client_exit']=client.returncode
 log=content(RUN/'client-process.log')
 if client.returncode!=0 or '[PORTABLE-QA] ALL_DONE' not in log or '[PORTABLE-QA] FAIL' in log:raise RuntimeError('client acceptance did not complete successfully')
 screenshots=sorted((clientdir/'screenshots').glob('[0-9][0-9]-*.png'))
 result['screenshots']=[{'file':str(p.relative_to(RUN)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in screenshots]
 if len(screenshots)!=(3 if reload_from else 11) or any(p.read_bytes()[:8]!=b'\x89PNG\r\n\x1a\n' for p in screenshots):raise RuntimeError('screenshots missing or invalid')
 result['server_checks']=sum('[PORTABLE-QA] SERVER_PASS ' in line for line in content(RUN/'server-process.log').splitlines())
 result['checks']=sum('[PORTABLE-QA] PASS ' in line for line in log.splitlines())
 server.stdin.write(b'stop\n');server.stdin.flush();server.stdin.close()
 try:
  result['server_exit']=server.wait(timeout=15)
 except subprocess.TimeoutExpired:
  ps="Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*Energy-Control-neoforge-1.21.1*' } | Select-Object -ExpandProperty ProcessId"
  pidtext=subprocess.check_output(['powershell','-NoProfile','-Command',ps],text=True)
  for token in pidtext.split():
   if token.isdigit():
    dump=subprocess.run([str(Path(env['JAVA_HOME'])/'bin/jcmd.exe'),token,'Thread.print'],capture_output=True,timeout=15)
    (RUN/('shutdown-threads-'+token+'.txt')).write_bytes(dump.stdout+dump.stderr)
  result['server_exit']=server.wait(timeout=75)
 serverlog=content(RUN/'server-process.log')
 if '[PORTABLE-QA]' in serverlog and ('AssertionError:' in serverlog or '[PORTABLE-QA] FAIL' in serverlog):raise RuntimeError('server assertion failed')
 if result['server_exit']!=0 or 'Saving' not in serverlog:raise RuntimeError('server did not shut down and save normally')
 result['passed']=True
except Exception as e:
 result['error']=str(e)
finally:
 if server is not None and server.poll() is None:
  try:
   if not server.stdin.closed:server.stdin.write(b'stop\n');server.stdin.flush();server.stdin.close()
   server.wait(timeout=30)
  except Exception:pass
 for p in [client,server]:
  if p is not None and p.poll() is None:
   subprocess.run(['taskkill','/PID',str(p.pid),'/T','/F'],capture_output=True)
   p.wait(timeout=30)
 for h in handles:h.close()
 result['server_exit']=None if server is None else server.returncode
 result['client_exit']=None if client is None else client.returncode
 (RUN/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
 print(json.dumps(result,indent=2),flush=True)
raise SystemExit(0 if result['passed'] else 1)
