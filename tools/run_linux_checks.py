#!/usr/bin/env python3
"""Run development checks with project-local Linux Node and npm dependencies."""
import argparse
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
NODE = '/workspace/.runtime-cache/linux-node/node-v24.22.3-linux-arm64/bin/node'
ENV = ['PATH=/workspace/.runtime-cache/linux-node/node-v24.22.3-linux-arm64/bin:/usr/bin:/bin',
       'NODE_PATH=/workspace/.runtime-cache/linux-checks/node_modules',
       'PLAYWRIGHT_BROWSERS_PATH=/workspace/.runtime-cache/playwright',
       'UI_REVIEW_REQUIRE_BROWSER=1']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', choices=['unit', 'layout'], required=True)
    args = parser.parse_args()
    preflight = '''const fs=require('node:fs');
if(process.platform!=='linux'||process.versions.node!=='24.22.3')throw Error('Expected Linux Node 24.22.3');
for(const name of ['package.json','package-lock.json'])if(fs.readFileSync(name,'utf8')!==fs.readFileSync('.runtime-cache/linux-checks/'+name,'utf8'))throw Error('Linux npm manifest/lock is stale; repeat the documented npm ci setup');
const {assertIsolated}=require('./tools/linux_dependency_guard.cjs');
for(const name of ['jsdom','playwright'])assertIsolated(require.resolve(name),'.runtime-cache/linux-checks/node_modules');
console.log('Linux Node '+process.versions.node);require('jsdom');
'''
    if args.suite == 'layout':
        preflight += "const p=require('playwright');if(!fs.existsSync(p.chromium.executablePath()))throw Error('Install the pinned Playwright Chromium using the documented Linux setup');\n"
    base = ['mc-linux', '--', '/usr/bin/env', *ENV, NODE]
    command = (['--test', *[str(p.relative_to(ROOT)) for p in sorted((ROOT/'tests').glob('*.test.cjs'))]]
               if args.suite == 'unit' else ['tools/verify_browser_suites.cjs'])
    try:
        result = subprocess.run([*base, '-e', preflight], cwd=ROOT)
        if result.returncode:
            print("Linux preflight failed; follow docs/android-development-checks.ko.md", file=sys.stderr)
            return result.returncode
        return subprocess.run([*base, *command], cwd=ROOT).returncode
    except FileNotFoundError:
        print('mc-linux is unavailable; enable the optional Linux environment and follow docs/android-development-checks.ko.md', file=sys.stderr)
        return 127


if __name__ == '__main__':
    sys.exit(main())
