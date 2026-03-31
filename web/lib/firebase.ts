import { initializeApp, getApps } from 'firebase/app';
import { getAnalytics, isSupported, type Analytics } from 'firebase/analytics';

const firebaseConfig = {
  apiKey: "AIzaSyBGT90EVqdJZJNqb2CNl9miHladl2RuQ2Q",
  authDomain: "life-planner-29fbb.firebaseapp.com",
  projectId: "life-planner-29fbb",
  storageBucket: "life-planner-29fbb.firebasestorage.app",
  messagingSenderId: "820663438333",
  appId: "1:820663438333:web:b291124480c98d4ba76794",
  measurementId: "G-1H4753H0LX",
};

const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];

let analyticsInstance: Analytics | null = null;

export async function getFirebaseAnalytics(): Promise<Analytics | null> {
  if (analyticsInstance) return analyticsInstance;
  const supported = await isSupported();
  if (supported) {
    analyticsInstance = getAnalytics(app);
  }
  return analyticsInstance;
}

export { app };
