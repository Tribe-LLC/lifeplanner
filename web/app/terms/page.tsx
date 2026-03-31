import { LegalLayout, LegalSection, LegalHighlight, LegalFooter } from '@/components/landing/legal-layout';

export default function LifePlannerTermsPage() {
  return (
    <LegalLayout
      title="Terms & Conditions"
      lastUpdated="November 29, 2024"
      backLink="/"
      backLabel="Back to Life Planner"
      accentColor="emerald"
    >
      <p className="dark:text-white/80 text-gray-700 mb-8 text-lg">
        Welcome to <span className="font-bold text-emerald-500">Life Planner - Goals Tracker</span>!
        These Terms and Conditions ("Terms") govern your use of the Life Planner mobile application ("App," "Service," or "we").
        By downloading, installing, or using our App, you agree to be bound by these Terms.
      </p>

      <LegalSection title="1. Acceptance of Terms">
        <p>By accessing or using Life Planner, you confirm that:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>You are at least 13 years of age (or the minimum age in your jurisdiction)</li>
          <li>You have read, understood, and agree to these Terms</li>
          <li>You have read and agree to our Privacy Policy</li>
          <li>You have the legal capacity to enter into this agreement</li>
        </ul>
        <p className="mt-3">If you do not agree to these Terms, please do not use our App.</p>
      </LegalSection>

      <LegalSection title="2. Description of Service">
        <p>Life Planner provides:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Goal Setting:</strong> Create and organize personal and professional goals</li>
          <li><strong className="dark:text-white text-gray-900">Task Management:</strong> Break down goals into actionable tasks and milestones</li>
          <li><strong className="dark:text-white text-gray-900">Progress Tracking:</strong> Monitor your progress with visual charts and statistics</li>
          <li><strong className="dark:text-white text-gray-900">Reminders:</strong> Customizable notifications to keep you on track</li>
          <li><strong className="dark:text-white text-gray-900">Cloud Sync:</strong> Access your data across multiple devices</li>
        </ul>
      </LegalSection>

      <LegalSection title="3. User Accounts">
        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">3.1 Account Creation</h3>
        <p>You may sign in using Google or Apple authentication, or continue as a guest with limited features. When creating an account, you agree to:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Provide accurate and complete information</li>
          <li>Keep your account credentials secure</li>
          <li>Notify us immediately of any unauthorized access</li>
          <li>Be responsible for all activities under your account</li>
        </ul>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">3.2 Account Termination</h3>
        <p>We reserve the right to suspend or terminate your account if you:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Violate these Terms</li>
          <li>Engage in fraudulent or illegal activities</li>
          <li>Abuse or misuse the Service</li>
          <li>Attempt to circumvent premium features without payment</li>
        </ul>
      </LegalSection>

      <LegalSection title="4. User Content">
        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">4.1 Your Content</h3>
        <p>When you create content in Life Planner (goals, tasks, notes):</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>You retain ownership of your content</li>
          <li>You grant us a license to store and sync your content to provide the Service</li>
          <li>You are responsible for the content you create</li>
          <li>Your content is private and not shared with other users</li>
        </ul>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">4.2 Prohibited Content</h3>
        <p>You agree not to use the app to store or organize:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Illegal activities or plans</li>
          <li>Content that promotes harm to yourself or others</li>
          <li>Malicious code or attempts to compromise the service</li>
        </ul>
      </LegalSection>

      <LegalSection title="5. Subscriptions and Payments">
        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">5.1 Premium Features</h3>
        <p>Some features may require a paid subscription (Life Planner Pro). By subscribing, you agree to:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Pay all applicable fees</li>
          <li>Automatic renewal unless cancelled before the renewal date</li>
          <li>Pricing and terms as displayed at the time of purchase</li>
        </ul>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">5.2 Premium Benefits</h3>
        <p>Premium subscribers receive:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Unlimited goals and tasks</li>
          <li>Advanced analytics and insights</li>
          <li>Custom themes and personalization</li>
          <li>Ad-free experience</li>
          <li>Priority support</li>
        </ul>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">5.3 Refunds</h3>
        <p>Refunds are handled according to the policies of the App Store (Apple) or Google Play Store where you made your purchase. Please contact the respective store for refund requests.</p>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">5.4 Free Features and Advertising</h3>
        <p>Free features are supported by advertisements. By using free features, you agree to view advertisements displayed through Google AdMob.</p>
      </LegalSection>

      <LegalSection title="6. Intellectual Property">
        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">6.1 Our Property</h3>
        <p>All content, features, and functionality of the App are owned by Life Planner and protected by intellectual property laws, including:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>App design and user interface</li>
          <li>Logos, trademarks, and branding</li>
          <li>Software code and algorithms</li>
          <li>Written content and graphics</li>
        </ul>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">6.2 Limited License</h3>
        <p>We grant you a limited, non-exclusive, non-transferable license to use the App for personal, non-commercial purposes.</p>
      </LegalSection>

      <LegalSection title="7. Disclaimer of Warranties">
        <div className="dark:bg-amber-500/10 bg-amber-50 border-l-4 border-amber-500 p-5 rounded-r-lg my-4">
          <h3 className="font-semibold dark:text-amber-300 text-amber-700 mb-2">Important Notice</h3>
          <p className="dark:text-amber-300 text-amber-700">THE APP IS PROVIDED "AS IS" AND "AS AVAILABLE" WITHOUT WARRANTIES OF ANY KIND. WE DISCLAIM ALL WARRANTIES, EXPRESS OR IMPLIED, INCLUDING:</p>
          <ul className="list-disc pl-6 space-y-2 dark:text-amber-300 text-amber-700 mt-2">
            <li>Uninterrupted or error-free service</li>
            <li>Fitness for a particular purpose</li>
            <li>Accuracy of any goal tracking or analytics</li>
            <li>Guaranteed achievement of your goals</li>
          </ul>
        </div>

        <div className="dark:bg-emerald-500/10 bg-emerald-50 border-l-4 border-emerald-500 p-5 rounded-r-lg my-4">
          <h3 className="font-semibold dark:text-emerald-300 text-emerald-700 mb-2">Goal Achievement Disclaimer</h3>
          <p className="dark:text-emerald-300 text-emerald-700">Life Planner is a tool to help you organize and track your goals. Success depends on your own effort and actions. We do not guarantee:</p>
          <ul className="list-disc pl-6 space-y-2 dark:text-emerald-300 text-emerald-700 mt-2">
            <li>Achievement of any specific goals</li>
            <li>Specific outcomes or results</li>
            <li>Professional advice (financial, health, career, or otherwise)</li>
          </ul>
        </div>
      </LegalSection>

      <LegalSection title="8. Limitation of Liability">
        <p>TO THE MAXIMUM EXTENT PERMITTED BY LAW:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>We shall not be liable for any indirect, incidental, special, consequential, or punitive damages</li>
          <li>Our total liability shall not exceed the amount you paid for the Service in the past 12 months</li>
          <li>We are not responsible for any decisions made based on goal tracking data</li>
          <li>We are not liable for data loss due to circumstances beyond our control</li>
        </ul>
      </LegalSection>

      <LegalSection title="9. Indemnification">
        <p>You agree to indemnify and hold harmless Life Planner, its affiliates, and their respective officers, directors, employees, and agents from any claims, damages, losses, or expenses arising from:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Your use of the App</li>
          <li>Your violation of these Terms</li>
          <li>Your violation of any third-party rights</li>
          <li>Content you create within the App</li>
        </ul>
      </LegalSection>

      <LegalSection title="10. Third-Party Services">
        <p>Our App integrates with third-party services including:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Google Services:</strong> Authentication, Analytics, and Advertising (AdMob)</li>
          <li><strong className="dark:text-white text-gray-900">Apple Services:</strong> Sign in with Apple</li>
          <li><strong className="dark:text-white text-gray-900">Firebase:</strong> Backend services and data storage</li>
          <li><strong className="dark:text-white text-gray-900">RevenueCat:</strong> Subscription management</li>
        </ul>
        <p className="mt-3">Your use of these services is subject to their respective terms and privacy policies.</p>
      </LegalSection>

      <LegalSection title="11. Modifications to Service">
        <p>We reserve the right to:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Modify or discontinue features at any time</li>
          <li>Update pricing for premium features with notice</li>
          <li>Change these Terms with notice to users</li>
        </ul>
      </LegalSection>

      <LegalSection title="12. Governing Law">
        <p>These Terms shall be governed by and construed in accordance with applicable laws. Any disputes shall be resolved through binding arbitration or in courts of competent jurisdiction.</p>
      </LegalSection>

      <LegalSection title="13. Severability">
        <p>If any provision of these Terms is found to be unenforceable, the remaining provisions will continue in full force and effect.</p>
      </LegalSection>

      <LegalSection title="14. Entire Agreement">
        <p>These Terms, together with our Privacy Policy, constitute the entire agreement between you and Life Planner regarding the use of our App.</p>
      </LegalSection>

      <LegalSection title="15. Waiver">
        <p>Our failure to enforce any right or provision of these Terms shall not constitute a waiver of such right or provision.</p>
      </LegalSection>

      <LegalHighlight title="16. Contact Information" accentColor="emerald">
        <p>If you have any questions about these Terms, please contact us:</p>
        <p className="mt-2"><strong>Email:</strong> <a href="mailto:support@tribe.az" className="text-emerald-500 hover:underline">support@tribe.az</a></p>
        <p><strong>App:</strong> Life Planner - Goals Tracker</p>
        <p><strong>Developer:</strong> tribe.az</p>
      </LegalHighlight>

      <LegalFooter links={[
        { label: 'Privacy Policy', href: '/lifeplanner/privacy-policy' },
        { label: 'Delete Account', href: '/lifeplanner/delete-account' },
        { label: 'Back to Life Planner', href: '/lifeplanner' },
      ]} />

      <p className="mt-8 text-center dark:text-white/50 text-gray-500 text-sm">
        By using Life Planner, you acknowledge that you have read, understood, and agree to be bound by these Terms and Conditions.
      </p>
    </LegalLayout>
  );
}
