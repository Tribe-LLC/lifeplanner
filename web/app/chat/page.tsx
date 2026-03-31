import type { Metadata } from 'next';
import { ChatView } from '@/components/lifeplanner/chat/chat-view';

export const metadata: Metadata = {
  title: 'AI Chat',
  description:
    'Chat with your personal AI coach — Luna, Alex, Morgan, Kai, Sam, River, or Jamie. Get personalized guidance for goals, habits, wellness, and more.',
};

export default function ChatPage() {
  return <ChatView />;
}
