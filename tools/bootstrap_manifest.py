"""Fail-closed inspection of the generated diagnostic variant manifest."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'
ACTIVITY='dev.mobilecodex.app.p1.ProbeActivity'
SERVICE='dev.mobilecodex.app.p1.ProbeService'

def inspect_manifest(xml: str) -> dict:
    root=ET.fromstring(xml)
    if root.get('package')!='dev.mobilecodex.apk.probe': raise ValueError('wrong_package')
    app=root.find('application')
    if app is None or app.get(A+'name')!='android.app.Application': raise ValueError('engine_application_enabled')
    sensitive_permissions={'android.permission.BIND_ACCESSIBILITY_SERVICE','android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'}
    sensitive_actions={'android.accessibilityservice.AccessibilityService','android.service.notification.NotificationListenerService'}
    for node in app.iter():
        if node.get(A+'permission') in sensitive_permissions or (node.tag=='action' and node.get(A+'name') in sensitive_actions):
            raise ValueError('sensitive_service_declared')
    active=[]
    for node in app:
        if node.tag not in ('activity','activity-alias','service','receiver','provider') or node.get(A+'enabled')=='false':continue
        name=node.get(A+'name')
        if node.tag=='activity' and name==ACTIVITY:
            active.append(name)
        elif node.tag=='service' and name==SERVICE:
            if node.get(A+'isolatedProcess')!='true':raise ValueError('service_not_isolated')
            if node.get(A+'exported')!='false':raise ValueError('service_exported')
            active.append(name)
        elif node.tag=='receiver' and name=='androidx.profileinstaller.ProfileInstallReceiver' and node.get(A+'permission')=='android.permission.DUMP':
            active.append(name)
        else:raise ValueError('unexpected_component')
    if ACTIVITY not in active or SERVICE not in active:raise ValueError('missing_probe_component')
    allowed={'android.permission.INTERNET','dev.mobilecodex.apk.probe.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    permissions={node.get(A+'name') for node in root if node.tag in ('uses-permission','uses-permission-sdk-23')}
    if not permissions.issubset(allowed):raise ValueError('unexpected_permission')
    return {'package':root.get('package'),'application':app.get(A+'name'),'activeComponents':active,'permissions':sorted(permissions),'originalComponentsDisabled':True,'sensitiveServiceDeclarationsAbsent':True}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('manifest',type=Path)
    print(json.dumps(inspect_manifest(parser.parse_args().manifest.read_text()),indent=2))
if __name__=='__main__':main()
