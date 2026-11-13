import { initializeApp, cert, applicationDefault, getApps } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue, Timestamp } from 'firebase-admin/firestore';

function parseServiceAccount(raw) {
  if (!raw) return null;
  const trimmed = raw.trim();
  const jsonText = trimmed.startsWith('{')
    ? trimmed
    : Buffer.from(trimmed, 'base64').toString('utf8');
  return JSON.parse(jsonText);
}

export function initFirebaseAdmin(config) {
  let app = getApps()[0];
  if (!app) {
    const parsedSa = parseServiceAccount(config.firebaseServiceAccountJson);
    const credential = parsedSa ? cert(parsedSa) : applicationDefault();
    app = initializeApp({
      credential,
      projectId: config.firebaseProjectId
    });
  }

  const auth = getAuth(app);
  const db =
    config.firestoreDatabaseId && config.firestoreDatabaseId !== '(default)'
      ? getFirestore(app, config.firestoreDatabaseId)
      : getFirestore(app);

  return { app, auth, db, FieldValue, Timestamp };
}
