#!/usr/bin/env python3
"""Run the candidate against a disposable Paper installation, without players."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--paper', type=Path, required=True)
    parser.add_argument('--cache-dir', type=Path, required=True)
    parser.add_argument('--libraries-dir', type=Path, required=True)
    parser.add_argument('--paper-api', type=Path, required=True)
    parser.add_argument('--slimefun', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, required=True)
    args = parser.parse_args()
    for name, path in vars(args).items():
        if name != 'work_dir' and not path.exists():
            parser.error(f'{name}: missing {path}')
    args.work_dir.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='quaptics-', dir=args.work_dir)).resolve()
    (run / 'plugins').mkdir()
    (run / 'classes').mkdir()
    for name, source in [('libraries', args.libraries_dir), ('cache', args.cache_dir)]:
        shutil.copytree(source, run / name)
    for source, target in [(args.paper, 'server.jar'), (args.slimefun, 'plugins/Slimefun.jar'),
                           (args.candidate, 'plugins/Quaptics.jar')]:
        shutil.copy2(source, run / target)
    for plugin, text in {
        'Slimefun': 'options:\n  auto-update: false\nmetrics:\n  auto-update: false\n  analytics: false\n',
        'Quaptics': 'auto-update: false\n',
        'bStats': 'enabled: false\n',
    }.items():
        folder = run / 'plugins' / plugin
        folder.mkdir()
        (folder / 'config.yml').write_text(text)
    (run / 'eula.txt').write_text('eula=true\n')
    layers = {'layers': [{'block': 'minecraft:bedrock', 'height': 1},
                         {'block': 'minecraft:dirt', 'height': 2},
                         {'block': 'minecraft:grass_block', 'height': 1}],
              'biome': 'minecraft:plains', 'features': False, 'lakes': False}
    (run / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
        'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
        'level-type=minecraft:flat\ngenerate-structures=false\n'
        'spawn-npcs=false\nspawn-monsters=false\nallow-nether=false\nenable-rcon=false\n'
        'generator-settings=' + json.dumps(layers) + '\n')
    source = Path(__file__).resolve().parents[1] / 'src/runtime-test/java/probe/QuapticsProbe.java'
    cp = os.pathsep.join(str(p.resolve()) for p in [args.paper_api, args.candidate,
        args.slimefun, *(run / 'libraries').rglob('*.jar')])
    subprocess.run(['javac', '-cp', cp, '-d', str(run / 'classes'), str(source)], check=True)
    (run / 'classes/plugin.yml').write_text(
        'name: QuapticsProbe\nversion: 1.0\nmain: probe.QuapticsProbe\n'
        'api-version: "26.2"\ndepend: [Quaptics, Slimefun]\n')
    subprocess.run(['jar', 'cf', str(run / 'plugins/Probe.jar'), '-C', str(run / 'classes'), '.'], check=True)
    start = time.monotonic()
    with (run / 'console.log').open('w') as log:
        proc = subprocess.Popen(['java', '-Xms256m', '-Xmx1536m', '-XX:ActiveProcessorCount=2',
            '-Ddrakes.lab.autoupdate.disable=true', '-Dcom.mojang.eula.agree=true',
            '-jar', 'server.jar', '--nogui'], cwd=run, stdin=subprocess.PIPE,
            stdout=log, stderr=subprocess.STDOUT)
        try:
            code = proc.wait(timeout=300)
        except subprocess.TimeoutExpired:
            try:
                proc.communicate(b'stop\n', timeout=45)
            except subprocess.TimeoutExpired:
                proc.terminate()
                try:
                    proc.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()
            code = 124
    text = (run / 'console.log').read_text()
    result = {
        'candidate_sha256': hashlib.sha256(args.candidate.read_bytes()).hexdigest(),
        'probe_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'slimefun_sha256': hashlib.sha256(args.slimefun.read_bytes()).hexdigest(),
        'paper_sha256': hashlib.sha256(args.paper.read_bytes()).hexdigest(),
        'warnings': [line for line in text.splitlines() if '/WARN]' in line or ' WARN]' in line],
        'exit_code': code, 'seconds': round(time.monotonic() - start, 2),
        'markers': [line for line in text.splitlines() if 'QUAPTICS_' in line],
        'errors': [line for line in text.splitlines() if any(s in line for s in
                   ['ERROR', 'Exception', 'Error:', 'Caused by:', 'SEVERE'])],
    }
    (run / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'run': str(run), **result}, indent=2))
    return 0 if code == 0 and 'QUAPTICS_DONE failures=0' in text and not result['errors'] else 1


if __name__ == '__main__':
    sys.exit(main())
