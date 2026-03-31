export interface BlogPost {
  slug: string;
  title: string;
  description: string;
  date: string;
  readTime: string;
  category: string;
  tags: string[];
  content: string;
}

const posts: BlogPost[] = [
  {
    slug: 'why-i-stopped-setting-goals-and-started-building-systems',
    title: 'Why I Stopped Setting Goals and Started Building Systems',
    description: 'I used to set ambitious New Year goals every January. By March, I\'d forgotten most of them. Here\'s what changed everything.',
    date: '2026-03-15',
    readTime: '5 min read',
    category: 'Personal Growth',
    tags: ['goals', 'systems', 'habits', 'personal growth'],
    content: `
I used to be a goal-setting addict. Every January, I'd sit down with a fresh notebook and write out twenty ambitious goals. Learn Spanish. Read 50 books. Run a marathon. Meditate daily.

By March, the notebook was buried under a pile of other things I'd started and not finished.

The problem wasn't that my goals were wrong. They were fine goals. The problem was that I was treating goal-setting as the finish line when it's barely the starting line.

## The Trap of "Someday" Thinking

Here's something I noticed about myself: I'd spend more time *planning* my ideal life than actually *living* it. I'd research the perfect running app, the best meditation technique, the optimal reading schedule. All of that research felt productive. It wasn't.

There's a psychological trick your brain plays on you — just writing down a goal gives you a small dopamine hit. Your brain partially processes the intention as an accomplishment. So you feel good about your goals without doing any of the actual work.

## What Actually Changed Things

A friend told me something that stuck: "You don't rise to the level of your goals. You fall to the level of your systems."

That clicked. I stopped asking "What do I want to achieve?" and started asking "What do I want my Tuesday to look like?"

Instead of "read 50 books," I put a book on my nightstand and read for 15 minutes before sleep. Instead of "meditate daily," I sat on a cushion for two minutes after my morning coffee. That's it. Two minutes.

The funny thing is, once you're sitting there, you usually stay for ten.

## Small Things Compound

The math of small daily improvements is staggering when you think about it. Getting 1% better each day means you're 37 times better after a year. But nobody thinks in those terms because 1% feels like nothing on any given day.

I think that's why most productivity advice misses the mark. It focuses on the big dramatic transformation — the 5 AM wake-up, the complete life overhaul. But the people I know who've genuinely changed their lives did it through boring consistency, not dramatic reinvention.

## The Role of Tracking (Without Obsessing)

I'll be honest — I tried the "just do it and don't track anything" approach. It didn't work for me. Without some kind of record, I'd lose momentum without even noticing.

But there's a fine line between tracking that supports you and tracking that becomes its own form of procrastination. I don't need seventeen metrics for my reading habit. I just need to know: did I read today, yes or no?

That simplicity matters. The moment your tracking system becomes more complex than the habit itself, something's gone wrong.

## What I'd Tell My Younger Self

Stop planning. Start doing the smallest possible version of the thing you want. Do it today. Do it again tomorrow. Let the streak carry you.

And when you miss a day — because you will — don't let it become two days. That's the only rule that really matters.
    `.trim(),
  },
  {
    slug: 'the-journaling-habit-that-surprised-me',
    title: 'The Journaling Habit That Surprised Me',
    description: 'I never thought of myself as a "journaling person." Three months later, it\'s the one habit I refuse to skip.',
    date: '2026-03-10',
    readTime: '6 min read',
    category: 'Journaling',
    tags: ['journaling', 'mental health', 'self-reflection', 'habits'],
    content: `
I want to be upfront about something: I used to think journaling was kind of self-indulgent. Writing about your feelings in a notebook felt like something for people with more free time than me.

I was wrong, and I'm not too proud to admit it.

## How It Started

Last December, I was going through a rough patch at work. Nothing dramatic — just the accumulated weight of too many decisions, too little sleep, and a general feeling that I was running on autopilot. A therapist suggested I try writing down my thoughts for five minutes each evening.

I almost didn't do it. But I figured, what's five minutes?

The first few entries were terrible. "Today was fine. Work was busy. Had pasta for dinner." Riveting stuff.

But around day ten, something shifted. I stopped reporting the day and started actually thinking on paper. I'd write something like "I'm annoyed at the meeting this morning" and then ask myself *why*, and the answer would surprise me. It usually wasn't about the meeting at all.

## What Journaling Actually Does

The best way I can describe it is this: your mind is like a browser with 40 tabs open. Journaling is the process of going through those tabs one by one, deciding which ones to close, and bookmarking the ones that matter.

Before I started journaling, my thoughts would loop. The same worry would circle back three, four, five times a day. Writing it down once — really thinking it through on paper — seemed to release it. Not always, but often enough to matter.

There's research backing this up. Expressive writing has been shown to reduce stress, improve sleep, and even boost immune function. But honestly, I didn't need the research. I could feel the difference within two weeks.

## The Mood Connection

One thing I didn't expect was how much I'd learn from tracking my mood alongside my journal entries. Not with any fancy system — just a simple "how do I feel today?" rating.

After a month, I noticed something: my low-mood days almost always followed nights where I'd been on my phone past midnight. The correlation was so clear it was almost embarrassing. I'd been blaming my mood on work stress, but the data told a different story.

That's the thing about writing things down. It's hard to lie to yourself when the evidence is right there in your own handwriting.

## Why Most People Quit

I think people quit journaling for two reasons. First, they think they need to write a lot. You don't. Some of my most useful entries are three sentences long. Second, they think every entry needs to be profound. It doesn't. "Today was boring and I'm tired" is a perfectly valid journal entry.

The only rule I follow is this: write honestly. Even if it's just "I don't feel like writing today." That counts.

## Three Months Later

I'm writing this three months into my journaling habit, and it's become the one thing in my routine I genuinely protect. I've skipped workouts. I've skipped meditation. I haven't skipped journaling.

The reason is simple: it's the only habit that makes every other habit easier. When you understand what you're feeling and why, you make better decisions about everything else.

If you're skeptical — and I get it, I was too — just try five minutes tonight. Write about your day. Be honest. See what happens. You might surprise yourself.
    `.trim(),
  },
  {
    slug: 'what-i-learned-from-tracking-my-time-for-30-days',
    title: 'What I Learned from Tracking My Time for 30 Days',
    description: 'I decided to track every hour of my day for a month. The results were uncomfortable but eye-opening.',
    date: '2026-03-05',
    readTime: '7 min read',
    category: 'Productivity',
    tags: ['time management', 'productivity', 'focus', 'self-awareness'],
    content: `
Last month, I decided to do something uncomfortable. I tracked how I spent every waking hour for 30 days straight.

Not in a fancy time-tracking app. Just a simple note at the end of each hour: what did I actually do? Not what I planned to do. Not what I told myself I was doing. What I actually did.

The results were humbling.

## The Gap Between Perception and Reality

I considered myself a fairly productive person. I work hard, I have goals, I stay busy. But "busy" and "productive" aren't the same thing, and this experiment made that painfully clear.

Here's what I thought my average workday looked like: 6 hours of focused work, 1 hour of meetings, 1 hour of admin tasks.

Here's what it actually looked like: 3.5 hours of focused work, 1.5 hours of meetings, 1 hour of email, and 2 hours of... I'm not entirely sure. Context switching. Scrolling. "Quick" tasks that took 30 minutes. The in-between stuff that doesn't feel like anything but adds up to everything.

## The Phone Problem

I already knew I spent too much time on my phone. But I didn't know it was 3.2 hours per day on average.

The worst part? Most of that time happened in small chunks. Five minutes here, eight minutes there. Never enough to feel like a problem in the moment. But 20 sessions of five minutes is over an hour and a half, and that's time I was completely unaware of losing.

The solution wasn't some dramatic digital detox. I just started leaving my phone in another room during focus blocks. Simple, but weirdly difficult at first. After a week, it felt normal.

## The Myth of Multitasking

Week two revealed something I'd been in denial about: I multitask constantly, and I'm terrible at it. Everyone is, actually — research has shown that what we call multitasking is really rapid context switching, and each switch costs you about 23 minutes of refocus time.

I was switching tasks every 15-20 minutes on average. Which means I was spending more time *recovering* from switches than actually working on anything. No wonder I felt busy but unproductive.

## What Changed

The biggest shift was surprisingly simple: I started batching similar tasks together. All email in two 30-minute windows. All meetings clustered on Tuesday and Thursday mornings. Deep work in uninterrupted 90-minute blocks.

I also started something I call the "one thing" rule. Before each day, I identify the single most important task and do it first, before checking email, before meetings, before anything else. Some days that one thing takes 30 minutes. Some days it takes three hours. But knowing it's done by lunch changes the entire feel of the day.

## The Pomodoro Discovery

During week three, I experimented with the Pomodoro technique — 25 minutes of focused work followed by a 5-minute break. I'd heard about it for years and always dismissed it as too rigid.

It turns out the rigidity is the point. Having a timer running creates just enough urgency to keep you from drifting. And the mandatory breaks prevent the kind of exhaustion that makes you reach for your phone as an escape.

I don't use it for everything now, but for tasks I tend to procrastinate on, it's remarkably effective. There's something about "just 25 minutes" that makes any task feel approachable.

## Would I Do It Again?

Tracking every hour for 30 days was tedious. I won't pretend otherwise. But the awareness it created has lasted much longer than the experiment.

I don't track every hour anymore. But I do check in with myself a few times a day: "What did I just spend the last hour doing? Was that the best use of my time?" Usually the answer is yes. Sometimes it's not, and that's okay — the question itself keeps me honest.

If you're feeling busy but not productive, I'd challenge you to try even one week of honest time tracking. You might not like what you find. But you can't fix what you can't see.
    `.trim(),
  },
  {
    slug: 'the-case-for-life-balance-and-why-hustle-culture-gets-it-wrong',
    title: 'The Case for Life Balance (And Why Hustle Culture Gets It Wrong)',
    description: 'We celebrate people who work 80-hour weeks. But the most fulfilled people I know have something different: balance across multiple life areas.',
    date: '2026-02-28',
    readTime: '6 min read',
    category: 'Life Balance',
    tags: ['life balance', 'wellness', 'burnout', 'hustle culture'],
    content: `
There's a popular narrative online that goes something like this: if you're not grinding, you're falling behind. Sleep is for the weak. Hustle until your haters ask if you're hiring.

I bought into this for years. And it worked — sort of. I advanced in my career, hit financial targets, and looked successful from the outside. But I was also exhausted, lonely, and weirdly empty for someone who had "everything going for them."

## The Imbalance Problem

Here's what nobody tells you about pouring everything into one area of your life: the other areas don't just pause. They deteriorate.

When I was working 70-hour weeks, my friendships faded. Not dramatically — nobody sent an angry text. People just... stopped inviting me to things. Because I'd said no too many times. My health declined gradually. I wasn't sick, just tired all the time, eating poorly, skipping workouts. My relationship suffered because I was physically present but mentally still at work.

The irony is that the career I was sacrificing everything for started suffering too. Turns out, a burned-out person running on caffeine and willpower doesn't do their best work.

## Rethinking What Balance Means

Balance doesn't mean spending equal time on everything. That's impossible and not even desirable. Balance means no single area of your life is in crisis mode while you pour energy into another.

I think of it like a garden. You don't water every plant the same amount. But if you completely ignore a section for months, things die. And once they're dead, they're much harder to bring back than if you'd just given them a little water along the way.

## The Seven Areas That Matter

Through a lot of trial and error (mostly error), I've identified seven areas that, when they're all at least "okay," make me feel genuinely good about my life:

**Career** — Am I growing and finding meaning in my work?

**Financial** — Do I feel secure, not anxious, about money?

**Physical** — Am I taking care of my body?

**Social** — Do I have meaningful connections with people I care about?

**Emotional** — Am I processing my feelings or just suppressing them?

**Spiritual** — Do I have a sense of purpose beyond my to-do list?

**Family** — Am I showing up for the people closest to me?

None of these need to be perfect. They just can't be ignored.

## The Weekly Check-In

The most impactful habit I've adopted is a simple weekly review where I rate each area from 1-10 and ask myself: "Which area needs attention this week?"

Sometimes the answer is obvious — if I haven't exercised in two weeks, physical health gets priority. Sometimes it's subtle — I'll realize I haven't called my parents in a month, or that I've been avoiding a difficult emotion.

The point isn't to optimize. It's to stay aware. Because the things that wreck your life are rarely sudden catastrophes. They're the slow drift of neglect that you don't notice until it's become a crisis.

## What Hustle Culture Misses

I'm not against hard work. Some seasons of life demand intense focus on one thing. Starting a business, finishing a degree, dealing with a health crisis — these require temporary imbalance, and that's fine.

But hustle culture has turned a temporary survival strategy into a permanent lifestyle. And the people promoting it are often either in their twenties with no real responsibilities, or rich enough to outsource the parts of life they're neglecting.

The most fulfilled people I know — not the richest, not the most famous, but the most genuinely happy — have something in common. They're intentional about all seven areas. They're not perfect at any of them. But they're paying attention.

That, I've come to believe, is the real definition of success.
    `.trim(),
  },
  {
    slug: 'why-your-morning-routine-doesnt-need-to-start-at-5am',
    title: 'Why Your Morning Routine Doesn\'t Need to Start at 5 AM',
    description: 'The internet is obsessed with early mornings. But the best routine is the one you actually follow — even if it starts at 8.',
    date: '2026-02-20',
    readTime: '5 min read',
    category: 'Habits',
    tags: ['morning routine', 'habits', 'productivity', 'sleep'],
    content: `
Open any productivity blog and you'll find the same advice: wake up at 5 AM, meditate, exercise, journal, eat a healthy breakfast, and tackle your most important work — all before the rest of the world is awake.

It sounds great. It also doesn't work for most people, and I wish someone had told me that sooner.

## My 5 AM Experiment

I tried the 5 AM routine for six weeks. I set my alarm, laid out my workout clothes the night before, and went to bed at 9:30 PM like a responsible adult.

Week one was exciting. I felt virtuous, productive, superior to everyone still sleeping. Week two was fine. Week three, the alarm started feeling like an enemy. By week five, I was hitting snooze until 6:30 and feeling guilty about it, which was worse than never trying at all.

The problem wasn't discipline. The problem was that I'm not a morning person, and no amount of motivational quotes was going to change my biology.

## Chronotypes Are Real

There's actual science behind this. Your chronotype — whether you're naturally an early bird or a night owl — is largely genetic. About 25% of people are genuine morning types, 25% are evening types, and the rest fall somewhere in the middle.

Forcing yourself into the wrong chronotype doesn't make you more productive. It makes you sleep-deprived. And sleep deprivation tanks your cognitive performance, willpower, and mood — the exact things you need for a productive day.

## What Actually Matters

After my failed 5 AM experiment, I stepped back and asked: what's the actual point of a morning routine? It's not the time on the clock. It's about starting your day with intention instead of reaction.

That means: before you check email, before you scroll social media, before you respond to anyone else's agenda, you do something for yourself. Whether that's at 5 AM or 8 AM doesn't matter even a little bit.

My current morning routine starts at 7:15. I make coffee, sit quietly for ten minutes (not formal meditation, just sitting), write three lines in my journal, and review my one priority for the day. The whole thing takes 20 minutes.

It's not Instagram-worthy. Nobody would make a YouTube video about it. But I've done it consistently for eight months, which is about seven months longer than my 5 AM phase lasted.

## The Consistency Principle

The best morning routine is the one you actually do. Every day. Without negotiating with yourself about whether today is the day you "deserve" to sleep in.

That means it needs to be:

**Short enough** that you can't talk yourself out of it. If your routine takes 90 minutes, any disruption — a bad night's sleep, an early meeting, a sick kid — gives you an excuse to skip the whole thing.

**Enjoyable enough** that you don't dread it. If you hate meditation, don't meditate. If running makes you miserable, don't run in the morning. Put things in your routine that you actually look forward to.

**Flexible enough** to survive real life. Some mornings I skip the journaling and just drink my coffee. That's fine. The routine adapts to me, not the other way around.

## Permission to Be Normal

I think the 5 AM cult has done real damage to people's relationship with mornings. It's created this idea that if you're not up before dawn being exceptional, you're lazy. You're not.

You're a person with a specific biology, a specific life situation, and specific needs. Build your routine around those realities, not around someone else's highlight reel.

Start tomorrow. Whatever time you naturally wake up. Do one small thing for yourself before the world starts demanding your attention. That's all a morning routine needs to be.
    `.trim(),
  },
];

export function getAllPosts(): BlogPost[] {
  return posts.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}

export function getPostBySlug(slug: string): BlogPost | undefined {
  return posts.find((p) => p.slug === slug);
}

export function getAllSlugs(): string[] {
  return posts.map((p) => p.slug);
}
