'use strict';
const fs = require('node:fs');
const path = require('node:path');
function assertIsolated(resolved, prefix) {
  const relative = path.relative(fs.realpathSync(prefix), fs.realpathSync(resolved));
  if (relative === '..' || relative.startsWith('..' + path.sep) || path.isAbsolute(relative)) {
    throw new Error('Dependency resolves outside isolated Linux npm directory: ' + resolved);
  }
}
module.exports = { assertIsolated };
