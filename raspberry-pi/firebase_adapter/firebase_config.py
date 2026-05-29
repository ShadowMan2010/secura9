"""
Firebase configuration for SECURA-9.

Setup instructions:
───────────────────

1. CREATE FIREBASE PROJECT
   - Go to https://console.firebase.google.com
   - Click "Add project" → follow wizard
   - Disable Google Analytics (optional)

2. ENABLE AUTHENTICATION
   - In Firebase Console → Authentication → Sign-in method
   - Enable "Google" provider
   - Configure OAuth consent screen if prompted

3. CREATE FIRESTORE DATABASE
   - Firestore Database → Create database
   - Choose a location (e.g., "asia-south1")
   - Start in "test mode" (we'll secure it later)

4. GENERATE SERVICE ACCOUNT KEY (for Python on Raspberry Pi)
   - Project Settings → Service accounts
   - Firebase Admin SDK → "Generate new private key"
   - Download the JSON file
   - Save it as:  /home/shadowman/raspberry-pi/firebase_adapter/serviceAccountKey.json

5. DOWNLOAD ANDROID CONFIG (for the Android app)
   - Project Settings → General → Your apps → Add app → Android
   - Package name: com.secura9.app
   - Download google-services.json
   - Place it in: android/app/google-services.json

6. UPDATE FIRESTORE SECURITY RULES (after testing)
   Rules → Edit rules:
   
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /devices/{device} {
         allow read, write: if request.auth != null;
         match /{document=**} {
           allow read, write: if request.auth != null;
         }
       }
     }
   }

7. DEVICE ID
   The default device ID is 'secura9_pi_01'.
   Change it in firebase_service.py or set it when creating FirebaseService.
"""

# Device identifier — change if you have multiple doors
DEVICE_ID = 'secura9_pi_01'

# Path to the Firebase service account key JSON file
SERVICE_ACCOUNT_PATH = 'serviceAccountKey.json'
