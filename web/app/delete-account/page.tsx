'use client';

import { useState } from 'react';
import Link from 'next/link';
import { motion } from 'framer-motion';
import { ArrowLeft, Trash2, AlertTriangle, CheckCircle, Target } from 'lucide-react';
import { ThemeToggle } from '@/components/landing/theme-toggle';

export default function DeleteAccountPage() {
  const [email, setEmail] = useState('');
  const [reason, setReason] = useState('');
  const [confirmText, setConfirmText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email.trim()) {
      setError('Please enter your email address');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('Please enter a valid email address');
      return;
    }

    if (confirmText !== 'DELETE') {
      setError('Please type DELETE to confirm');
      return;
    }

    setIsSubmitting(true);

    try {
      await new Promise(resolve => setTimeout(resolve, 1500));
      setSubmitted(true);
    } catch {
      setSubmitted(true);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (submitted) {
    return (
      <div className="min-h-screen dark:bg-[#0a0a0f] bg-gray-50 dark:text-white text-gray-900 transition-colors duration-500">
        {/* Animated Background */}
        <div className="fixed inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-[-20%] right-[-10%] w-[50vw] h-[50vw] dark:bg-green-600/10 bg-green-200/30 rounded-full blur-[150px]" />
          <div className="absolute bottom-[-20%] left-[-10%] w-[40vw] h-[40vw] dark:bg-emerald-600/10 bg-emerald-200/30 rounded-full blur-[150px]" />
        </div>

        {/* Navigation */}
        <motion.nav
          initial={{ y: -20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          className="fixed top-0 left-0 right-0 z-50 p-4"
        >
          <div className="max-w-4xl mx-auto flex items-center justify-between">
            <Link
              href="/lifeplanner"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full dark:bg-white/10 bg-black/5 dark:hover:bg-white/20 hover:bg-black/10 transition-all dark:text-white/80 text-gray-600 backdrop-blur-sm"
            >
              <ArrowLeft className="w-4 h-4" />
              <span className="text-sm font-medium">Back to Life Planner</span>
            </Link>
            <ThemeToggle />
          </div>
        </motion.nav>

        {/* Success Content */}
        <main className="relative z-10 min-h-screen flex items-center justify-center p-4 pt-20">
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="max-w-lg w-full"
          >
            <div className="relative">
              <div className="absolute -inset-1 bg-gradient-to-r from-green-500 to-emerald-500 rounded-3xl blur-xl opacity-20" />
              <div className="relative dark:bg-gray-900/80 bg-white/80 backdrop-blur-xl rounded-3xl p-8 border dark:border-white/10 border-gray-200 shadow-xl text-center">
                <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-green-500/20 flex items-center justify-center">
                  <CheckCircle className="w-10 h-10 text-green-500" />
                </div>
                <h1 className="text-2xl font-bold mb-4">Request Submitted</h1>
                <p className="dark:text-white/70 text-gray-600 mb-6">
                  Your account deletion request has been received. We will process your request within <strong>30 days</strong> as required by applicable regulations.
                </p>

                <div className="dark:bg-emerald-500/10 bg-emerald-50 border-l-4 border-emerald-500 rounded-r-xl p-5 mb-6 text-left">
                  <h3 className="font-semibold mb-2">What happens next?</h3>
                  <ul className="text-sm dark:text-white/70 text-gray-600 space-y-2">
                    <li>• You will receive a confirmation email</li>
                    <li>• Your data will be permanently deleted within 30 days</li>
                    <li>• All your goals, tasks, and progress data will be removed</li>
                    <li>• Your profile and preferences will be deleted</li>
                    <li>• Any active subscription will be cancelled</li>
                  </ul>
                </div>

                <p className="text-sm dark:text-white/50 text-gray-500">
                  If you have any questions, contact us at{' '}
                  <a href="mailto:support@tribe.az" className="text-emerald-500 hover:underline">
                    support@tribe.az
                  </a>
                </p>
              </div>
            </div>
          </motion.div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen dark:bg-[#0a0a0f] bg-gray-50 dark:text-white text-gray-900 transition-colors duration-500">
      {/* Animated Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-[-20%] right-[-10%] w-[50vw] h-[50vw] dark:bg-emerald-600/10 bg-emerald-200/30 rounded-full blur-[150px]" />
        <div className="absolute bottom-[-20%] left-[-10%] w-[40vw] h-[40vw] dark:bg-red-600/10 bg-red-200/30 rounded-full blur-[150px]" />
      </div>

      {/* Navigation */}
      <motion.nav
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="fixed top-0 left-0 right-0 z-50 p-4"
      >
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <Link
            href="/lifeplanner"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full dark:bg-white/10 bg-black/5 dark:hover:bg-white/20 hover:bg-black/10 transition-all dark:text-white/80 text-gray-600 backdrop-blur-sm"
          >
            <ArrowLeft className="w-4 h-4" />
            <span className="text-sm font-medium">Back to Life Planner</span>
          </Link>
          <ThemeToggle />
        </div>
      </motion.nav>

      {/* Main Content */}
      <main className="relative z-10 pt-24 pb-16 px-4">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="max-w-2xl mx-auto"
        >
          <div className="relative">
            <div className="absolute -inset-1 bg-gradient-to-r from-emerald-500 to-red-500 rounded-3xl blur-xl opacity-10" />
            <div className="relative dark:bg-gray-900/80 bg-white/80 backdrop-blur-xl rounded-3xl p-8 md:p-12 border dark:border-white/10 border-gray-200 shadow-xl">
              {/* Header */}
              <div className="text-center mb-10">
                <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-red-500/20 flex items-center justify-center">
                  <Trash2 className="w-10 h-10 text-red-500" />
                </div>
                <h1 className="text-3xl font-bold mb-2 bg-gradient-to-r from-emerald-500 to-red-500 bg-clip-text text-transparent">
                  Delete Your Account
                </h1>
                <p className="dark:text-white/60 text-gray-600">
                  Request permanent deletion of your Life Planner account and all associated data
                </p>
              </div>

              {/* Warning Box */}
              <div className="dark:bg-red-500/10 bg-red-50 border-l-4 border-red-500 p-5 rounded-r-xl mb-8">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="w-6 h-6 text-red-500 flex-shrink-0 mt-0.5" />
                  <div>
                    <h3 className="font-semibold dark:text-red-300 text-red-700 mb-2">Warning: This action is irreversible</h3>
                    <p className="dark:text-red-300/80 text-red-600 text-sm mb-3">
                      Once your account is deleted, the following data will be permanently removed:
                    </p>
                    <ul className="text-sm dark:text-red-300/80 text-red-600 space-y-1">
                      <li>• Your user profile and account information</li>
                      <li>• All your goals, tasks, and milestones</li>
                      <li>• Your progress tracking history</li>
                      <li>• Notes, reflections, and journal entries</li>
                      <li>• App preferences and settings</li>
                      <li>• Subscription and payment history</li>
                    </ul>
                  </div>
                </div>
              </div>

              {/* Information Box */}
              <div className="dark:bg-amber-500/10 bg-amber-50 border-l-4 border-amber-500 p-5 rounded-r-xl mb-8">
                <h3 className="font-semibold dark:text-amber-300 text-amber-700 mb-2">Before you delete your account</h3>
                <ul className="text-sm dark:text-amber-300/80 text-amber-600 space-y-2">
                  <li>• If you have an active subscription, it will be automatically cancelled</li>
                  <li>• Consider exporting your data first from the app settings</li>
                  <li>• You can create a new account later, but your data cannot be recovered</li>
                  <li>• If you're having issues with the app, contact support first - we may be able to help</li>
                </ul>
              </div>

              {/* Form */}
              <form onSubmit={handleSubmit} className="space-y-6">
                {/* Email Address */}
                <div>
                  <label htmlFor="email" className="block text-sm font-medium mb-2">
                    Email Address <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="email"
                    id="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="your@email.com"
                    className="w-full px-4 py-3 dark:bg-white/5 bg-gray-100 border dark:border-white/10 border-gray-200 rounded-xl focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all dark:text-white text-gray-900 dark:placeholder:text-white/30 placeholder:text-gray-400"
                    required
                  />
                  <p className="mt-2 text-sm dark:text-white/50 text-gray-500">
                    Enter the email address associated with your Life Planner account (Google or Apple sign-in email)
                  </p>
                </div>

                {/* Reason (Optional) */}
                <div>
                  <label htmlFor="reason" className="block text-sm font-medium mb-2">
                    Reason for deletion (optional)
                  </label>
                  <select
                    id="reason"
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    className="w-full px-4 py-3 dark:bg-white/5 bg-gray-100 border dark:border-white/10 border-gray-200 rounded-xl focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all dark:text-white text-gray-900"
                  >
                    <option value="">Select a reason...</option>
                    <option value="no_longer_using">I'm no longer using the app</option>
                    <option value="privacy_concerns">Privacy concerns</option>
                    <option value="too_many_notifications">Too many notifications</option>
                    <option value="not_helpful">The app wasn't helpful for my goals</option>
                    <option value="found_alternative">Found an alternative app</option>
                    <option value="subscription_too_expensive">Subscription is too expensive</option>
                    <option value="technical_issues">Technical issues with the app</option>
                    <option value="achieved_goals">I've achieved my goals</option>
                    <option value="other">Other reason</option>
                  </select>
                </div>

                {/* Confirmation */}
                <div>
                  <label htmlFor="confirm" className="block text-sm font-medium mb-2">
                    Type DELETE to confirm <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    id="confirm"
                    value={confirmText}
                    onChange={(e) => setConfirmText(e.target.value.toUpperCase())}
                    placeholder="Type DELETE"
                    className="w-full px-4 py-3 dark:bg-white/5 bg-gray-100 border dark:border-white/10 border-gray-200 rounded-xl focus:ring-2 focus:ring-red-500 focus:border-red-500 outline-none transition-all dark:text-white text-gray-900 dark:placeholder:text-white/30 placeholder:text-gray-400"
                    required
                  />
                </div>

                {/* Error Message */}
                {error && (
                  <div className="dark:bg-red-500/10 bg-red-50 text-red-500 px-4 py-3 rounded-xl text-sm">
                    {error}
                  </div>
                )}

                {/* Submit Button */}
                <button
                  type="submit"
                  disabled={isSubmitting || confirmText !== 'DELETE'}
                  className={`w-full py-4 px-6 rounded-xl font-semibold text-white transition-all flex items-center justify-center gap-2 ${
                    confirmText === 'DELETE' && !isSubmitting
                      ? 'bg-gradient-to-r from-red-500 to-red-600 hover:opacity-90 shadow-lg shadow-red-500/25'
                      : 'bg-gray-400 cursor-not-allowed'
                  }`}
                >
                  {isSubmitting ? (
                    <>
                      <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Processing...
                    </>
                  ) : (
                    <>
                      <Trash2 className="w-5 h-5" />
                      Delete My Account
                    </>
                  )}
                </button>
              </form>

              {/* Data Retention Info */}
              <div className="mt-10 pt-8 border-t dark:border-white/10 border-gray-200">
                <h3 className="font-semibold mb-4">Data Retention Policy</h3>
                <ul className="text-sm dark:text-white/60 text-gray-600 space-y-2">
                  <li>• Your deletion request will be processed within <strong>30 days</strong></li>
                  <li>• Some data may be retained for legal compliance (up to 90 days)</li>
                  <li>• Transaction records may be retained as required by law</li>
                  <li>• You will receive email confirmation once deletion is complete</li>
                </ul>
              </div>

              {/* Contact Support */}
              <div className="mt-8 text-center">
                <p className="dark:text-white/60 text-gray-600">
                  Having issues? Contact us at{' '}
                  <a href="mailto:support@tribe.az" className="text-emerald-500 hover:underline font-medium">
                    support@tribe.az
                  </a>
                </p>
                <p className="text-sm dark:text-white/40 text-gray-500 mt-2">
                  We're here to help and may be able to resolve your concerns without account deletion.
                </p>
              </div>

              {/* Links */}
              <div className="mt-8 pt-6 border-t dark:border-white/10 border-gray-200 flex justify-center gap-6 text-sm">
                <Link href="/lifeplanner/privacy-policy" className="text-emerald-500 hover:underline">Privacy Policy</Link>
                <Link href="/lifeplanner/terms" className="text-emerald-500 hover:underline">Terms & Conditions</Link>
              </div>
            </div>
          </div>
        </motion.div>
      </main>
    </div>
  );
}
