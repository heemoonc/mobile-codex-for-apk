import unittest
from tools.bootstrap_manifest import inspect_manifest

BASE='''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.mobilecodex.apk.probe"><uses-permission android:name="android.permission.INTERNET"/><application android:name="android.app.Application"><activity android:name="dev.mobilecodex.app.p1.ProbeActivity" android:exported="true"/><service android:name="dev.mobilecodex.app.p1.ProbeService" android:exported="false" android:isolatedProcess="true"/><service android:name="dev.mobilecodex.app.EngineService" android:enabled="false"/></application></manifest>'''
class BootstrapManifestTest(unittest.TestCase):
    def test_diagnostic_manifest_is_accepted(self):
        self.assertEqual(inspect_manifest(BASE)['package'],'dev.mobilecodex.apk.probe')
    def test_original_application_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'engine_application_enabled'):
            inspect_manifest(BASE.replace('android.app.Application','dev.mobilecodex.app.MobileCodexApp'))
    def test_enabled_original_execution_route_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'unexpected_component'):
            inspect_manifest(BASE.replace('android:enabled="false"','android:enabled="true"'))
    def test_nonisolated_service_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'service_not_isolated'):
            inspect_manifest(BASE.replace('android:isolatedProcess="true"','android:isolatedProcess="false"'))
    def test_exported_probe_service_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'service_exported'):
            inspect_manifest(BASE.replace('android:exported="false"','android:exported="true"'))
    def test_disabled_accessibility_declaration_is_rejected(self):
        extra='<service android:name="dev.mobilecodex.app.PhoneUseService" android:enabled="false" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" />'
        with self.assertRaisesRegex(ValueError,'sensitive_service_declared'):
            inspect_manifest(BASE.replace('</application>',extra+'</application>'))
    def test_disabled_notification_listener_declaration_is_rejected(self):
        extra='<service android:name="example.Listener" android:enabled="false" android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />'
        with self.assertRaisesRegex(ValueError,'sensitive_service_declared'):
            inspect_manifest(BASE.replace('</application>',extra+'</application>'))
    def test_sdk23_permission_cannot_bypass_permission_guard(self):
        extra='<uses-permission-sdk-23 android:name="android.permission.RECORD_AUDIO" />'
        with self.assertRaisesRegex(ValueError,'unexpected_permission'):
            inspect_manifest(BASE.replace('<application ',extra+'<application '))
if __name__=='__main__':unittest.main()
