import { LegalLayout, LegalSection, LegalHighlight, LegalLink, LegalFooter } from '@/components/landing/legal-layout';

export default function LifePlannerPrivacyPolicyPage() {
  return (
    <LegalLayout
      title="Privacy Policy"
      lastUpdated="March 14, 2025"
      backLink="/"
      backLabel="Back to Life Planner"
      accentColor="emerald"
    >
      <p className="dark:text-white/80 text-gray-700 mb-8 text-lg">
        This Privacy Policy describes how <span className="font-bold text-emerald-500">Life Planner: AI Coach</span> (&quot;Life Planner,&quot; &quot;we,&quot; &quot;our,&quot; or &quot;us&quot;),
        operated by tribe.az, collects, uses, shares, and protects your personal information when you use our mobile application
        available on Android (Google Play) and iOS (App Store). This policy applies to all users worldwide.
      </p>

      <p className="dark:text-white/80 text-gray-700 mb-8">
        By downloading, installing, or using Life Planner, you agree to the collection and use of information in accordance with this policy.
        If you do not agree, please do not use our application.
      </p>

      <LegalSection title="1. Information We Collect">
        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">1.1 Information You Provide Directly</h3>
        <p>When you use Life Planner, you may voluntarily provide the following categories of information:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Account Information:</strong> Name, email address, and profile picture when you sign in with Google or Apple via Firebase Authentication</li>
          <li><strong className="dark:text-white text-gray-900">Profile Preferences:</strong> Display name and timezone settings</li>
          <li><strong className="dark:text-white text-gray-900">Goals and Milestones:</strong> Goal titles, descriptions, categories, progress data, milestones with due dates, and dependency relationships between goals</li>
          <li><strong className="dark:text-white text-gray-900">Habit Data:</strong> Habits you create, daily check-ins, streak counts, and frequency configurations</li>
          <li><strong className="dark:text-white text-gray-900">Journal Entries:</strong> Written reflections, mood ratings (5-point scale), tags, and associations with goals or habits</li>
          <li><strong className="dark:text-white text-gray-900">Focus Session Data:</strong> Timer duration, selected themes, linked milestones, and session completion records</li>
          <li><strong className="dark:text-white text-gray-900">Life Balance Assessments:</strong> Self-reported scores across eight life domains (Career, Financial, Physical Health, Social, Emotional, Spiritual, Family, Personal Growth) with trend indicators</li>
          <li><strong className="dark:text-white text-gray-900">AI Coaching Conversations:</strong> Messages you send to AI coaching personas and the AI-generated responses</li>
        </ul>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">1.2 Information Collected Automatically</h3>
        <p>We automatically collect certain information when you use the app:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Device Information:</strong> Device model, operating system version, unique device identifiers, and language settings</li>
          <li><strong className="dark:text-white text-gray-900">Gamification Data:</strong> Experience points (XP), level progression, badges earned, and challenge completion records</li>
          <li><strong className="dark:text-white text-gray-900">Usage Analytics:</strong> Features accessed, interaction patterns, session duration, and notification engagement</li>
          <li><strong className="dark:text-white text-gray-900">Diagnostic Data:</strong> Error logs, crash reports, and application performance metrics</li>
        </ul>

        <h3 className="font-semibold dark:text-white text-gray-900 mt-4 mb-2">1.3 Information from Third-Party Services</h3>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Authentication Providers:</strong> When you sign in with Google or Apple via Firebase Auth, we receive your basic profile information (name, email, profile picture) as authorized by you</li>
          <li><strong className="dark:text-white text-gray-900">Analytics Services:</strong> Aggregated usage data to help us understand how the app is used</li>
          <li><strong className="dark:text-white text-gray-900">Advertising Networks:</strong> Google AdMob may collect advertising identifiers and device information for free-tier users to serve relevant advertisements</li>
        </ul>
      </LegalSection>

      <LegalSection title="2. Legal Basis for Processing (EEA/UK Users)">
        <p>If you are located in the European Economic Area (EEA) or the United Kingdom, we process your personal data based on the following legal grounds under the General Data Protection Regulation (GDPR):</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Contract Performance:</strong> Processing necessary to provide you with our services (goal tracking, habit management, AI coaching, cloud sync)</li>
          <li><strong className="dark:text-white text-gray-900">Legitimate Interest:</strong> Analytics and app improvement, fraud prevention, and ensuring security</li>
          <li><strong className="dark:text-white text-gray-900">Consent:</strong> Where you have provided explicit consent, such as for personalized advertising, push notifications, and optional AI features. You may withdraw consent at any time</li>
          <li><strong className="dark:text-white text-gray-900">Legal Obligation:</strong> Processing required to comply with applicable laws</li>
        </ul>
      </LegalSection>

      <LegalSection title="3. How We Use Your Information">
        <p>We use the information we collect for the following purposes:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Core Services:</strong> To provide and operate goal management, habit tracking, journaling, focus sessions, life balance assessments, and retrospective reviews</li>
          <li><strong className="dark:text-white text-gray-900">AI Coaching:</strong> To deliver personalized AI coaching through built-in and custom coaching personas. Your messages are processed by your selected third-party AI provider (Google Gemini, OpenAI ChatGPT, or xAI Grok) via a secure server-side proxy</li>
          <li><strong className="dark:text-white text-gray-900">AI-Powered Features:</strong> To generate performance reviews (weekly, monthly, quarterly, yearly), suggest SMART goals with milestones, assist with journal entries, and provide life balance insights</li>
          <li><strong className="dark:text-white text-gray-900">Gamification:</strong> To calculate experience points, track level progression and achievements, manage challenges, and deliver motivational rewards</li>
          <li><strong className="dark:text-white text-gray-900">Cross-Device Sync:</strong> To synchronize your data across multiple devices when you are signed in</li>
          <li><strong className="dark:text-white text-gray-900">Notifications:</strong> To send reminders, habit check-in prompts, and progress updates (with your permission)</li>
          <li><strong className="dark:text-white text-gray-900">Service Improvement:</strong> To analyze usage patterns, fix bugs, and improve the user experience</li>
          <li><strong className="dark:text-white text-gray-900">Advertising:</strong> To display advertisements to free-tier users via Google AdMob</li>
          <li><strong className="dark:text-white text-gray-900">Legal Compliance:</strong> To comply with applicable laws, regulations, and legal processes</li>
        </ul>
      </LegalSection>

      <LegalSection title="4. AI Processing and Third-Party AI Providers">
        <p>Life Planner integrates AI-powered features including coaching, goal generation, journal assistance, and performance reviews. This section explains how your data is processed in relation to these features:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>All AI requests are routed through a secure server-side proxy (Supabase Edge Functions). No third-party AI API keys are stored on or accessible from your device</li>
          <li>You may choose between three AI providers: <strong className="dark:text-white text-gray-900">Google Gemini</strong>, <strong className="dark:text-white text-gray-900">OpenAI ChatGPT</strong>, or <strong className="dark:text-white text-gray-900">xAI Grok</strong></li>
          <li>Only the minimum data necessary for each specific AI request (e.g., your message and relevant context) is transmitted to the selected provider</li>
          <li>We do not use your personal data to train, fine-tune, or improve any AI models. Each provider&apos;s own data processing policies govern how they handle requests</li>
          <li>AI-generated content (coaching responses, reviews, goal suggestions) is stored within your account for your personal reference</li>
          <li>You may opt out of AI features entirely and continue using all non-AI functionality of the app</li>
        </ul>
        <p className="mt-3">For details on how each AI provider handles data, please refer to their respective privacy policies:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Google Gemini: Google AI Privacy Notice</li>
          <li>OpenAI: OpenAI Privacy Policy</li>
          <li>xAI: xAI Privacy Policy</li>
        </ul>
      </LegalSection>

      <LegalSection title="5. Data Storage, Sync, and Retention">
        <p>Life Planner is built as an <strong className="dark:text-white text-gray-900">offline-first</strong> application with optional cloud synchronization:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Local Storage:</strong> All data is stored locally on your device using SQLite. The app is fully functional without an internet connection</li>
          <li><strong className="dark:text-white text-gray-900">Cloud Sync:</strong> When you create an account and sign in, your data is encrypted and synced to our cloud infrastructure (Supabase, powered by PostgreSQL) for cross-device access. Sync is bidirectional with automatic conflict resolution</li>
          <li><strong className="dark:text-white text-gray-900">Encryption:</strong> All data is encrypted in transit using TLS 1.2+ and encrypted at rest on our cloud servers</li>
          <li><strong className="dark:text-white text-gray-900">Guest Mode:</strong> If you use the app without creating an account, all data remains exclusively on your device and is never transmitted to any external server</li>
          <li><strong className="dark:text-white text-gray-900">Data Retention:</strong> We retain your data for as long as your account is active. Upon account deletion, your data is soft-deleted immediately and permanently purged from our systems within 30 days</li>
          <li><strong className="dark:text-white text-gray-900">Server Location:</strong> Cloud data is hosted on servers located in the United States and the European Union via Supabase infrastructure</li>
        </ul>
      </LegalSection>

      <LegalSection title="6. Data Sharing and Disclosure">
        <p>We share your information only as described below. We do not sell, rent, or trade your personal information to any third party.</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Cloud Infrastructure (Supabase):</strong> Data storage, synchronization, authentication, and serverless AI proxy functions</li>
          <li><strong className="dark:text-white text-gray-900">Authentication (Firebase):</strong> Google and Apple sign-in services</li>
          <li><strong className="dark:text-white text-gray-900">AI Providers:</strong> Google (Gemini), OpenAI (ChatGPT), or xAI (Grok) — only the data required for your selected AI interaction is shared, based on your chosen provider</li>
          <li><strong className="dark:text-white text-gray-900">Subscription Management (RevenueCat):</strong> In-app purchase and subscription processing</li>
          <li><strong className="dark:text-white text-gray-900">Advertising (Google AdMob):</strong> Ad serving for free-tier users only. Premium subscribers do not see advertisements</li>
          <li><strong className="dark:text-white text-gray-900">Legal Obligations:</strong> We may disclose information when required by law, regulation, legal process, or enforceable governmental request</li>
          <li><strong className="dark:text-white text-gray-900">Business Transfers:</strong> In the event of a merger, acquisition, or sale of assets, your information may be transferred as part of that transaction. You will be notified of any such change</li>
        </ul>
      </LegalSection>

      <LegalSection title="7. Data Security">
        <p>We implement industry-standard technical and organizational security measures to protect your personal information:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>TLS 1.2+ encryption for all data in transit</li>
          <li>AES-256 encryption for data at rest on cloud servers</li>
          <li>Secure authentication via Firebase Auth (Google and Apple Sign-In) with OAuth 2.0</li>
          <li>Server-side AI proxy architecture to prevent exposure of API credentials on client devices</li>
          <li>Row-level security (RLS) policies enforced at the database level to isolate user data</li>
          <li>Regular security audits and vulnerability assessments</li>
          <li>Principle of least privilege for all system access</li>
        </ul>
        <p className="mt-3">While we strive to protect your data using commercially reasonable measures, no method of electronic transmission or storage is completely secure. We cannot guarantee absolute security.</p>
      </LegalSection>

      <LegalSection title="8. Your Privacy Rights">
        <p>Depending on your location, you may be entitled to the following rights under applicable data protection laws (including GDPR, CCPA/CPRA, LGPD, PIPEDA, POPIA, and other regional regulations):</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Right of Access:</strong> Request a copy of the personal data we hold about you</li>
          <li><strong className="dark:text-white text-gray-900">Right to Rectification:</strong> Request correction of inaccurate or incomplete personal data</li>
          <li><strong className="dark:text-white text-gray-900">Right to Erasure:</strong> Request deletion of your personal data and account</li>
          <li><strong className="dark:text-white text-gray-900">Right to Data Portability:</strong> Receive your data in a structured, machine-readable format</li>
          <li><strong className="dark:text-white text-gray-900">Right to Restrict Processing:</strong> Request limitation of processing of your personal data</li>
          <li><strong className="dark:text-white text-gray-900">Right to Object:</strong> Object to certain processing activities, including direct marketing and profiling</li>
          <li><strong className="dark:text-white text-gray-900">Right to Withdraw Consent:</strong> Withdraw previously given consent at any time without affecting the lawfulness of prior processing</li>
          <li><strong className="dark:text-white text-gray-900">Right to Opt-Out of Sale/Sharing:</strong> We do not sell your personal information. California residents may still exercise this right under CCPA/CPRA</li>
          <li><strong className="dark:text-white text-gray-900">Right to Non-Discrimination:</strong> We will not discriminate against you for exercising your privacy rights</li>
        </ul>
        <p className="mt-3">To exercise any of these rights, please visit our <LegalLink href="/lifeplanner/delete-account">account deletion page</LegalLink> or contact us at <a href="mailto:support@tribe.az" className="text-emerald-500 hover:underline">support@tribe.az</a>. We will respond to verified requests within 30 days (or sooner where required by law).</p>
      </LegalSection>

      <LegalSection title="9. California Residents (CCPA/CPRA)">
        <p>If you are a California resident, you have additional rights under the California Consumer Privacy Act (CCPA) and the California Privacy Rights Act (CPRA):</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>We do not sell or share your personal information for cross-context behavioral advertising</li>
          <li>You may request to know what categories and specific pieces of personal information we have collected</li>
          <li>You may request deletion of your personal information</li>
          <li>You may designate an authorized agent to make requests on your behalf</li>
        </ul>
        <p className="mt-3">To submit a verifiable consumer request, contact us at <a href="mailto:support@tribe.az" className="text-emerald-500 hover:underline">support@tribe.az</a>.</p>
      </LegalSection>

      <LegalSection title="10. International Data Transfers">
        <p>Life Planner is available globally. Your information may be transferred to, stored, and processed in countries other than your country of residence, including the United States, where our cloud infrastructure and service providers operate.</p>
        <p className="mt-3">For transfers of personal data from the EEA, UK, or Switzerland, we rely on:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Standard Contractual Clauses (SCCs) approved by the European Commission</li>
          <li>Adequacy decisions where applicable</li>
          <li>Your explicit consent where no other legal mechanism applies</li>
        </ul>
        <p className="mt-3">We ensure that all international data transfers are subject to appropriate safeguards as required by applicable data protection laws.</p>
      </LegalSection>

      <LegalSection title="11. Children's Privacy">
        <p>Life Planner is not directed at children under the age of 13 (or the applicable minimum age in your jurisdiction, such as 16 in certain EEA countries). We do not knowingly collect personal information from children below the applicable age threshold.</p>
        <p className="mt-3">If you are a parent or guardian and believe your child has provided us with personal information, please contact us immediately at <a href="mailto:support@tribe.az" className="text-emerald-500 hover:underline">support@tribe.az</a>. We will take steps to delete such information promptly.</p>
      </LegalSection>

      <LegalSection title="12. Cookies and Tracking Technologies">
        <p>The Life Planner mobile app does not use browser cookies. However, the following tracking technologies may be used:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li><strong className="dark:text-white text-gray-900">Device Identifiers:</strong> Used for analytics and crash reporting</li>
          <li><strong className="dark:text-white text-gray-900">Advertising Identifiers:</strong> Used by Google AdMob for free-tier ad personalization. You can reset or opt out of personalized ads through your device settings</li>
          <li><strong className="dark:text-white text-gray-900">Firebase Analytics:</strong> Collects anonymized usage data to help us improve the app</li>
        </ul>
      </LegalSection>

      <LegalSection title="13. Third-Party Services and Links">
        <p>Our app may contain links to or integrate with third-party websites and services that are not operated by us. We are not responsible for the privacy practices or content of these third parties. We strongly encourage you to review the privacy policies of any third-party service you access.</p>
      </LegalSection>

      <LegalSection title="14. Changes to This Privacy Policy">
        <p>We may update this Privacy Policy from time to time to reflect changes in our practices, technology, legal requirements, or other factors. When we make material changes, we will:</p>
        <ul className="list-disc pl-6 space-y-2">
          <li>Update the &quot;Last Updated&quot; date at the top of this page</li>
          <li>Notify you through the app or via email for significant changes</li>
        </ul>
        <p className="mt-3">Your continued use of Life Planner after any changes to this Privacy Policy constitutes your acceptance of the updated policy.</p>
      </LegalSection>

      <LegalHighlight title="15. Contact Us" accentColor="emerald">
        <p>If you have any questions, concerns, or requests regarding this Privacy Policy or our data practices, please contact us:</p>
        <p className="mt-2"><strong>Email:</strong> <a href="mailto:support@tribe.az" className="text-emerald-500 hover:underline">support@tribe.az</a></p>
        <p><strong>App:</strong> Life Planner: AI Coach</p>
        <p><strong>Developer:</strong> tribe.az</p>
        <p className="mt-3">If you are located in the EEA and believe your data protection rights have not been adequately addressed, you have the right to lodge a complaint with your local supervisory authority.</p>
      </LegalHighlight>

      <LegalFooter links={[
        { label: 'Terms & Conditions', href: '/lifeplanner/terms' },
        { label: 'Delete Account', href: '/lifeplanner/delete-account' },
        { label: 'Back to Life Planner', href: '/lifeplanner' },
      ]} />

      <p className="mt-8 text-center dark:text-white/50 text-gray-500 text-sm">
        By using Life Planner: AI Coach, you acknowledge that you have read and understood this Privacy Policy.
      </p>
    </LegalLayout>
  );
}
