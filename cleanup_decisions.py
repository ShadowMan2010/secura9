#!/usr/bin/env python3
"""Delete all decision documents from Firestore (stale cleanup)."""

#!/usr/bin/env python3
"""Delete all decision documents from Firestore (stale cleanup)."""

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from firebase_admin import firestore, initialize_app, credentials

BASE = os.path.dirname(os.path.abspath(__file__))
cred = credentials.Certificate(os.path.join(BASE, 'firebase_adapter', 'serviceAccountKey.json'))
initialize_app(cred, {'projectId': 'secura9-pi-security-system'})
db = firestore.client()

DEVICE_ID = 'secura9_pi_01'
device_ref = db.collection('devices').document(DEVICE_ID)
total = 0

# 1. Clean all decisions (processed ones are auto-deleted, but stale ones remain)
print('== Decisions ==')
docs = device_ref.collection('decisions').stream()
count = 0
for doc in docs:
    data = doc.to_dict()
    print(f'  {doc.id}  {data.get("decision","?")}  name={data.get("name","")}')
    doc.reference.delete()
    count += 1
total += count
print(f'  → {count} deleted')

# 2. Clean old approvals that are not pending (already decided)
print('\n== Old approvals (decided) ==')
import time
now_sec = time.time()
cutoff = now_sec - 86400  # 24h old
docs = device_ref.collection('approvals').stream()
count = 0
for doc in docs:
    data = doc.to_dict()
    status = data.get('status', 'unknown')
    ts = data.get('timestamp')
    if status != 'pending':
        if ts and hasattr(ts, 'timestamp') and ts.timestamp() < cutoff:
            print(f'  {doc.id}  status={status}  name={data.get("name","")}  ts={ts}')
            doc.reference.delete()
            count += 1
        elif not ts:
            print(f'  {doc.id}  status={status}  name={data.get("name","")}  (no timestamp)')
            doc.reference.delete()
            count += 1
total += count
print(f'  → {count} deleted')

# 3. Clean old WebRTC sessions (completed/failed)
print('\n== Old WebRTC sessions ==')
docs = device_ref.collection('webrtc').stream()
count = 0
for doc in docs:
    data = doc.to_dict()
    if data.get('answer'):
        print(f'  {doc.id}  has answer → completed session')
        doc.reference.delete()
        count += 1
total += count
print(f'  → {count} deleted')

print(f'\nTotal: {total} documents cleaned up.')
