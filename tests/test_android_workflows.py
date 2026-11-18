import importlib.util
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('workflow',ROOT/'tools/verify_android_workflows.py')
workflow=importlib.util.module_from_spec(spec);spec.loader.exec_module(workflow)

class WorkflowTest(unittest.TestCase):
 def artifact(self,**overrides):
  value={'package':'com.example.flow','versionCode':1,'versionName':'1.0','signer':'a'*64,'sha256':'b'*64};value.update(overrides);return value
 def test_update_requires_same_identity_signer_and_new_version_content(self):
  base=self.artifact();updated=self.artifact(versionCode=2,versionName='2.0',sha256='c'*64)
  workflow.check_update(base,updated)
  for field,value in [('package','other.pkg'),('signer','d'*64),('versionCode',1),('sha256','b'*64)]:
   with self.subTest(field=field),self.assertRaises(ValueError):workflow.check_update(base,{**updated,field:value})
 def test_restore_rejects_different_signer_and_version(self):
  before=self.artifact();workflow.check_restore(before,before.copy())
  for field,value in [('signer','c'*64),('package','other.pkg'),('versionCode',2),('versionName','2.0')]:
   with self.subTest(field=field),self.assertRaises(ValueError):workflow.check_restore(before,{**before,field:value})
 def test_expected_failure_requires_real_compiler_diagnostic(self):
  workflow.check_failure(1,'MainActivity.java:5: error: expected')
  for code,log in [(0,'error:'),(1,'BUILD FAILED network timeout'),(-1,'MainActivity.java error:')]:
   with self.subTest(code=code,log=log),self.assertRaises(ValueError):workflow.check_failure(code,log)
 def test_recovery_requires_executed_compile_and_packaging_not_cached_success(self):
  good='> Task :app:compileDebugJavaWithJavac\n> Task :app:packageDebug\nBUILD SUCCESSFUL in 20s'
  workflow.check_recompiled(good)
  for log in [good.replace('compileDebugJavaWithJavac','compileDebugJavaWithJavac UP-TO-DATE'),good.replace('packageDebug\n','packageDebug FROM-CACHE\n'),'BUILD SUCCESSFUL']:
   with self.subTest(log=log),self.assertRaises(ValueError):workflow.check_recompiled(log)
if __name__=='__main__':unittest.main()
