import type { Metadata } from 'next';
import { LegalLayout, LegalSection, LegalHighlight, LegalLink, LegalFooter } from '@/components/landing/legal-layout';

export const metadata: Metadata = {
  title: 'Support',
  description: 'Get help with Life Planner: contact support, manage your account and data, and find answers to common questions.',
};

const mailLink = 'text-lp-primary hover:underline';

export default function LifePlannerSupportPage() {
  return (
    <LegalLayout
      title="Support"
      lastUpdated="October 22, 2026"
      backLink="/"
      backLabel="Back to Life Planner"
      accentColor="emerald"
    >
      <p className="dark:text-white/80 text-gray-700 mb-8 text-lg">
        Need help with <span className="font-bold text-lp-primary">Life Planner: AI Coach</span>? Write to us and a real person
        will answer, usually within two business days.
      </p>

      <LegalHighlight title="Contact support" accentColor="emerald">
        <p><strong>Email:</strong> <a href="mailto:support@tribe.az?subject=Life%20Planner%20support" className={mailLink}>support@tribe.az</a></p>
        <p className="mt-3">
          To help us solve it faster, please include your device (for example iPhone 15 or Pixel 8), the app version
          (shown in your phone's app settings or the App Store / Google Play page), the email you signed in with, and a short description or screenshot of the problem.
        </p>
      </LegalHighlight>

      <LegalSection title="Account and sign-in">
        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">I can&apos;t sign in</h3>
        <p>
          Make sure you use the same sign-in method you used the first time (Google, email and password, or an email sign-in link). Check your spam folder for sign-in emails. If it still fails, email us
          with the address you signed up with and we will check your account.
        </p>
        <h3 className="font-semibold dark:text-white text-gray-900 mt-6 mb-2">My data is missing on a new phone</h3>
        <p>
          Your goals, habits and journal sync to the cloud when you are signed in. Sign in with the same account on the new
          device and give it a minute on a stable connection. Data created in guest mode stays on the original device only.
        </p>
      </LegalSection>

      <LegalSection title="Your data">
        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">Export or back up</h3>
        <p>Open Settings, then Backup &amp; Sync, to export your data to a file or restore it from one.</p>
        <h3 className="font-semibold dark:text-white text-gray-900 mt-6 mb-2">Delete your account</h3>
        <p>
          You can request permanent deletion of your account and all associated data on our{' '}
          <LegalLink href="/delete-account">account deletion page</LegalLink>, or by emailing{' '}
          <a href="mailto:support@tribe.az?subject=Life%20Planner%20account%20deletion" className={mailLink}>support@tribe.az</a>{' '}
          from the address linked to your account. We complete deletion within 30 days and confirm by email.
        </p>
        <h3 className="font-semibold dark:text-white text-gray-900 mt-6 mb-2">Privacy</h3>
        <p>
          How we collect and use data is described in our <LegalLink href="/privacy-policy">Privacy Policy</LegalLink>.
        </p>
      </LegalSection>

      <LegalSection title="Notifications and reminders">
        <p>
          If reminders do not arrive, check that notifications are allowed for Life Planner in your phone&apos;s Settings and
          that Focus or battery saving modes are not silencing them.
        </p>
      </LegalSection>

      <LegalSection title="Health data">
        <p>
          Health features read data from Apple Health on iPhone or Health Connect on Android, only after you grant permission.
          You can change this at any time in the Health app or in Health Connect settings.
        </p>
      </LegalSection>

      <LegalSection title="Feedback and feature requests">
        <p>
          We read every message. Send ideas and bug reports from the Feedback screen in the app, or email{' '}
          <a href="mailto:support@tribe.az?subject=Life%20Planner%20feedback" className={mailLink}>support@tribe.az</a>.
        </p>
      </LegalSection>

      <LegalSection title="About">
        <p>Life Planner is developed and published by Tribe Solutions MMC.</p>
      </LegalSection>

      <LegalFooter links={[
        { label: 'Privacy Policy', href: '/privacy-policy' },
        { label: 'Terms & Conditions', href: '/terms' },
        { label: 'Delete Account', href: '/delete-account' },
        { label: 'Back to Life Planner', href: '/' },
      ]} />
    </LegalLayout>
  );
}
