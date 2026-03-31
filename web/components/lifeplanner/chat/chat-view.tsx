'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { supabase } from '@/lib/supabase';
import { COACHES } from '@/lib/constants/coaches';
import type { AIProvider } from '@/lib/constants/coaches';
import type { ChatMessage } from '@/types/chat';
import { AuthForm } from './auth-form';
import { ChatSidebar } from './chat-sidebar';
import { ChatMessageBubble, TypingIndicator } from './chat-message';
import { ChatInput } from './chat-input';
import { Menu } from 'lucide-react';

export function ChatView() {
  const [session, setSession] = useState<boolean | null>(null); // null = loading
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [currentCoachId, setCurrentCoachId] = useState('luna');
  const [currentProvider, setCurrentProvider] = useState<AIProvider>('GEMINI');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [error, setError] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const currentCoach = COACHES.find((c) => c.id === currentCoachId) ?? COACHES[0];

  // Check session on mount
  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(!!data.session);
    });

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, sess) => {
      setSession(!!sess);
    });

    return () => subscription.unsubscribe();
  }, []);

  // Auto-scroll
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming]);

  const handleNewChat = useCallback(() => {
    setMessages([]);
    setError('');
  }, []);

  const handleLogout = useCallback(async () => {
    await supabase.auth.signOut();
    setMessages([]);
  }, []);

  const handleSend = useCallback(
    async (content: string) => {
      const userMsg: ChatMessage = {
        id: `user-${Date.now()}`,
        role: 'user',
        content,
        timestamp: Date.now(),
      };
      const updatedMessages = [...messages, userMsg];
      setMessages(updatedMessages);
      setIsStreaming(true);
      setError('');

      try {
        const { data } = await supabase.auth.getSession();
        const token = data.session?.access_token;
        if (!token) throw new Error('Not authenticated');

        const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL!;
        const response = await fetch(`${supabaseUrl}/functions/v1/ai-proxy`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({
            messages: updatedMessages.map((m) => ({
              role: m.role,
              content: m.content,
            })),
            systemPrompt: currentCoach.systemPrompt,
            provider: currentProvider,
            enrichContext: true,
            stream: true,
          }),
        });

        if (!response.ok) {
          const text = await response.text();
          throw new Error(text || `Error ${response.status}`);
        }

        // Parse SSE stream
        const reader = response.body?.getReader();
        if (!reader) throw new Error('No response stream');

        const decoder = new TextDecoder();
        let buffer = '';
        let aiContent = '';
        const aiMsgId = `ai-${Date.now()}`;

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const parts = buffer.split('\n\n');
          buffer = parts.pop() || '';

          for (const part of parts) {
            const lines = part.split('\n');
            let eventType = '';
            let eventData = '';

            for (const line of lines) {
              if (line.startsWith('event: ')) eventType = line.slice(7).trim();
              else if (line.startsWith('data: ')) eventData = line.slice(6);
            }

            if (eventType === 'text' && eventData) {
              aiContent += eventData;
              setMessages((prev) => {
                const existing = prev.find((m) => m.id === aiMsgId);
                if (existing) {
                  return prev.map((m) =>
                    m.id === aiMsgId ? { ...m, content: aiContent } : m
                  );
                }
                return [
                  ...prev,
                  { id: aiMsgId, role: 'assistant', content: aiContent, timestamp: Date.now() },
                ];
              });
            } else if (eventType === 'error') {
              try {
                const errObj = JSON.parse(eventData);
                throw new Error(errObj.error || 'Stream error');
              } catch (e) {
                if (e instanceof Error && e.message !== 'Stream error') throw e;
                throw new Error(eventData || 'Stream error');
              }
            }
          }
        }
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Failed to get response';
        setError(message);
      } finally {
        setIsStreaming(false);
      }
    },
    [messages, currentCoach, currentProvider]
  );

  // Loading state
  if (session === null) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#0f0f1e]">
        <div className="text-gray-400">Loading...</div>
      </div>
    );
  }

  // Auth screen
  if (!session) {
    return <AuthForm onAuth={() => setSession(true)} />;
  }

  // Chat screen
  return (
    <div className="flex h-screen bg-[#0f0f1e] text-white overflow-hidden">
      <ChatSidebar
        currentCoachId={currentCoachId}
        currentProvider={currentProvider}
        onCoachChange={setCurrentCoachId}
        onProviderChange={setCurrentProvider}
        onNewChat={handleNewChat}
        onLogout={handleLogout}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <div className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <header className="flex items-center gap-3 px-4 py-3 border-b border-white/10 bg-[#0f0f1e]/80 backdrop-blur-sm shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="md:hidden p-2 rounded-lg hover:bg-white/10 transition-colors"
          >
            <Menu size={20} />
          </button>
          <div className="flex items-center gap-2">
            <span className="text-xl">{currentCoach.emoji}</span>
            <div>
              <h1 className="font-semibold text-sm">LifePlanner AI</h1>
              <p className="text-xs text-gray-400">{currentCoach.name} — {currentCoach.title}</p>
            </div>
          </div>
        </header>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto px-4 py-6">
          <div className="max-w-3xl mx-auto">
            {messages.length === 0 && (
              <div className="text-center py-20">
                <div className="text-5xl mb-4">{currentCoach.emoji}</div>
                <h2 className="text-xl font-semibold mb-2">
                  Hi, I&apos;m {currentCoach.name}
                </h2>
                <p className="text-gray-400 max-w-md mx-auto">
                  {currentCoach.title}. How can I help you today?
                </p>
              </div>
            )}

            {messages.map((msg) => (
              <ChatMessageBubble key={msg.id} message={msg} />
            ))}

            {isStreaming && messages[messages.length - 1]?.role !== 'assistant' && (
              <TypingIndicator />
            )}

            {error && (
              <div className="max-w-[80%] mx-auto bg-red-500/10 border border-red-500/30 rounded-xl px-4 py-3 text-red-300 text-sm mb-4">
                {error}
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>
        </div>

        {/* Input */}
        <ChatInput onSend={handleSend} disabled={isStreaming} />
      </div>
    </div>
  );
}
