'use client';

import { COACHES, AI_PROVIDERS, type AIProvider } from '@/lib/constants/coaches';
import { MessageSquarePlus, LogOut } from 'lucide-react';

interface ChatSidebarProps {
  currentCoachId: string;
  currentProvider: AIProvider;
  onCoachChange: (coachId: string) => void;
  onProviderChange: (provider: AIProvider) => void;
  onNewChat: () => void;
  onLogout: () => void;
  isOpen: boolean;
  onClose: () => void;
}

export function ChatSidebar({
  currentCoachId,
  currentProvider,
  onCoachChange,
  onProviderChange,
  onNewChat,
  onLogout,
  isOpen,
  onClose,
}: ChatSidebarProps) {
  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 md:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={`fixed md:relative z-50 md:z-auto top-0 left-0 h-full w-72 bg-[#12122a] border-r border-white/10 flex flex-col transition-transform duration-300 md:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* New Chat button */}
        <div className="p-4">
          <button
            onClick={() => {
              onNewChat();
              onClose();
            }}
            className="w-full flex items-center gap-2 px-4 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-medium transition-colors"
          >
            <MessageSquarePlus size={18} />
            New Chat
          </button>
        </div>

        {/* Coach selector */}
        <div className="px-4 pb-3">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2 block">
            Coach
          </label>
          <div className="space-y-1">
            {COACHES.map((coach) => (
              <button
                key={coach.id}
                onClick={() => {
                  onCoachChange(coach.id);
                  onClose();
                }}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors text-left ${
                  currentCoachId === coach.id
                    ? 'bg-indigo-600/30 text-white'
                    : 'text-gray-300 hover:bg-white/5'
                }`}
              >
                <span className="text-lg">{coach.emoji}</span>
                <div>
                  <div className="font-medium">{coach.name}</div>
                  <div className="text-xs text-gray-500">{coach.title}</div>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Provider selector */}
        <div className="px-4 pb-4">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2 block">
            AI Provider
          </label>
          <div className="flex gap-1 bg-white/5 rounded-lg p-1">
            {AI_PROVIDERS.map((p) => (
              <button
                key={p.id}
                onClick={() => onProviderChange(p.id)}
                className={`flex-1 px-2 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  currentProvider === p.id
                    ? 'bg-indigo-600 text-white'
                    : 'text-gray-400 hover:text-white'
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Logout */}
        <div className="p-4 border-t border-white/10">
          <button
            onClick={onLogout}
            className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/5 text-sm transition-colors"
          >
            <LogOut size={16} />
            Sign Out
          </button>
        </div>
      </aside>
    </>
  );
}
