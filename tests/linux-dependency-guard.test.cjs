const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {assertIsolated} = require('../tools/linux_dependency_guard.cjs');
test('rejects external module and symlink escape; accepts isolated file', () => {
 const root = fs.mkdtempSync(path.join(os.tmpdir(), 'linux-guard-'));
 try {
  const prefix=path.join(root,'modules');fs.mkdirSync(prefix);
  const inside=path.join(prefix,'inside.cjs');fs.writeFileSync(inside,'');
  const outside=path.join(root,'outside.cjs');fs.writeFileSync(outside,'');
  assert.doesNotThrow(()=>assertIsolated(inside,prefix));
  assert.throws(()=>assertIsolated(outside,prefix),/outside isolated/);
  const link=path.join(prefix,'linked.cjs');fs.symlinkSync(outside,link);
  assert.throws(()=>assertIsolated(link,prefix),/outside isolated/);
 } finally {fs.rmSync(root,{recursive:true,force:true});}
});
