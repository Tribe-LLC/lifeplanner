# Life Planner — Google Stitch Design Prompts

Copy-paste these prompts into Google Stitch to generate visual mockups of each screen. Start with the Design System Preamble for consistent styling across all screens.

---

## Design System Preamble

> Paste this into Stitch first (or prepend to any screen prompt) so it understands the visual language.

```
Design system for a modern mobile life-planning app (iOS/Android). Use these exact specifications:

TYPOGRAPHY: Satoshi font family. Weights: Regular (400), Medium (500), SemiBold (600), Bold (700), Black (900). Sizes follow Material 3: headlines 24-32sp semibold, titles 16-22sp semibold, body 14-16sp regular, labels 11-14sp medium.

COLORS (Light Theme):
- Primary: #4A6FFF (vibrant blue)
- Secondary: #7A5AF8 (purple)
- Accent: #F86E5A (warm coral)
- Success: #28C76F (green)
- Error: #EA5455 (red)
- Warning: #FF9F43 (orange)
- Background: #F8F9FC (off-white)
- Surface: #FFFFFF (white cards)
- Surface Variant: #F0F2FA
- Text Primary: #2C3345
- Text Secondary: #6E7A94
- Text Tertiary: #9AA6BC
- Divider: #E8ECF4

CATEGORY COLORS (used for goals and life areas):
- Career: #4A6FFF (blue)
- Financial: #28C76F (green)
- Physical: #FF9F43 (orange)
- Social: #7A5AF8 (purple)
- Emotional: #00CFE8 (cyan)
- Spiritual: #EA5455 (red)
- Family: #6236FF (deep purple)
- Learning: #28C3D7 (teal)

VISUAL STYLE:
- Corner radii: Small 8dp, Medium 12dp, Large 16dp, XL 20dp, XXL 24dp, Pill 100dp
- Spacing: 8dp grid (4, 8, 12, 16, 20, 24, 32dp)
- Cards: Glass morphism effect — semi-transparent white surfaces with 1dp gradient borders (white 50% alpha to 10% alpha), subtle shadows
- Gradients: Primary gradient is blue #667EEA to purple #764BA2
- Icons: Material Rounded icon set, 24dp default
- Buttons: 48dp height, rounded corners
- Bottom navigation: Pill-shaped floating bar with glass morphism, 50dp corner radius, 16dp horizontal margin, 32dp from bottom edge
- Overall feel: Clean, modern, minimal with pops of color from categories. Lots of white space. Friendly and motivational tone.
```

---

## Screen 1: Home Screen

```
Mobile app home screen for a life planner app. Light theme, #F8F9FC background. Vertical scrolling layout. Design for iPhone 15 Pro dimensions.

TOP SECTION — Hero Greeting Card:
- Full-width card with glass morphism (semi-transparent white, subtle gradient border)
- 16dp corner radius, 16dp internal padding
- Left side: "Good morning," in body text (#6E7A94), user name "Alex" below in bold headline (#2C3345)
- Below name: motivational subtitle "You have 3 goals in progress" in small text
- Right side: circular level badge "Lv 5" in primary blue #4A6FFF, and a flame streak icon with "12" next to it
- Top-right: small circular profile avatar with user initials

QUICK ACTIONS — Horizontal scrollable pill row:
- Below hero card, 12dp gap
- Row of rounded pill buttons (32dp height, 100dp corner radius, 12dp padding)
- Each pill has small icon + label: "Add Goal" (blue), "AI Suggest" (purple #7A5AF8), "New Habit" (green #28C76F), "Journal" (coral #F86E5A), "Focus" (orange #FF9F43), "Coach AI" (purple with lock icon overlay)
- Pills have light tinted backgrounds matching their icon color at 10% opacity

PRIORITY GOALS — Horizontal carousel:
- Section header row: "Priority Goals" in semibold title on left, "See all >" link in primary blue on right
- Horizontal scrollable row of compact goal cards (160dp wide, 100dp tall)
- Each card: category color left border (4dp wide), goal title in bold, circular progress indicator (small, bottom-right), percentage text
- Cards have white background, 12dp corners, subtle shadow

TODAY'S HABITS — Vertical list in card:
- Section header: "Today's Habits (2/5)" on left, "See all >" on right
- Glass card containing 3-4 habit rows separated by thin dividers (#E8ECF4)
- Each row: circular checkbox (unchecked = outline, checked = green #28C76F fill with checkmark), habit name, small streak badge "7 days" on right
- Some checkboxes filled green (completed), others empty

EXPLORE SECTION:
- "Explore" header in semibold
- Two glass cards stacked: "Yesterday's Recap" with purple history icon, "Flow Focus" with timer icon
- Each card: icon in colored circle on left, title + short description on right, chevron arrow

BOTTOM NAVIGATION:
- Floating pill-shaped bar at bottom (50dp corner radius, glass morphism effect)
- 16dp horizontal margins, 32dp from bottom
- 4 icon items evenly spaced: Home (filled, primary blue), Goals (outline), Habits (outline), Journal (outline)
- Selected item: filled icon in primary blue, others: outline icons in #9AA6BC
- Semi-transparent white background with subtle gradient border
```

---

## Screen 2: Goals Screen

```
Mobile app goals list screen. Light theme, #F8F9FC background. iPhone 15 Pro.

TOP APP BAR:
- Pinned at top, white surface background
- Left: back arrow icon (24dp, #2C3345)
- Center area: search icon that toggles a search input field
- When search active: full-width text input with magnifying glass icon, "Search goals..." placeholder in #9AA6BC
- Bar color dynamically tints to match the first visible category color

MAIN CONTENT — Scrollable list grouped by category:

CATEGORY SECTIONS (repeat for each category with goals):
- Sticky section header: category name in semibold (e.g., "Career"), goal count badge "3" in small pill (category color background at 15% opacity, category color text), expand/collapse chevron on right
- Background of header: very light tint of category color at 5% opacity

GOAL CARDS (under each category header):
- Full-width cards with 12dp corner radius, white background, subtle shadow
- Left edge: 4dp thick vertical stripe in category color (Career: #4A6FFF, Financial: #28C76F, etc.)
- Content: Goal title in bold (#2C3345), short description below in #6E7A94
- Bottom of card: horizontal progress bar (8dp tall, rounded, category color fill on #E8ECF4 track), percentage text "65%" on right
- Status badge: small pill "In Progress" in primary blue or "Not Started" in gray
- Cards have 12dp vertical gap between them

FLOATING ACTION BUTTON:
- Circular, bottom-right, 56dp
- Background: primary blue #4A6FFF (or dynamic category color)
- White "+" icon centered
- 8dp elevation shadow

EMPTY STATE (show as alternative version):
- Centered illustration area (placeholder circle)
- "Your story starts here" in headline
- "Create your first goal to get started" in secondary text
- Three pill buttons: "Quick Add", "Browse Templates" (outlined), "AI Generate" (purple #7A5AF8)

BOTTOM NAVIGATION: Same floating pill bar as Home Screen, with Goals icon filled/selected.
```

---

## Screen 3: Habit Tracker Screen

```
Mobile app habit tracker screen. Light theme, #F8F9FC background. iPhone 15 Pro.

TOP APP BAR:
- White surface background
- Title: "Habit Tracker" in bold headline left-aligned
- Right of title: rounded badge showing "2/5" (completed/total) in a pill shape
  - If incomplete: primary container background (#ECF0FF), primary blue text
  - If all complete: green background (#28C76F at 15%), green text
  - 12dp corner radius, 8dp horizontal padding

MAIN CONTENT — Scrollable vertical list with 12dp gaps:

HABIT CARDS (SwipeableHabitCard, repeat 4-5 times):
- Full-width cards, 12dp corner radius, white surface, subtle shadow
- Layout: Row with checkbox on left, content in middle, streak on right
- Left: Large circular checkbox (28dp)
  - Unchecked: 2dp border in #CBD0DD, empty inside
  - Checked: filled green #28C76F circle with white checkmark
- Middle column:
  - Habit name in bold (#2C3345), e.g., "Morning Meditation"
  - Description in small text (#6E7A94), e.g., "10 min mindfulness"
  - Small category pill: colored dot + category name (e.g., blue dot + "Emotional")
  - Frequency badge: "Daily" in tiny outline pill
- Right side:
  - Flame icon + streak number "12" in bold
  - Small "days" label below

Show variety: some habits checked (green checkbox), some unchecked. Mix of categories (different colored dots).

EXTENDED FLOATING ACTION BUTTON:
- Bottom-right, rounded pill shape (not circular)
- Primary blue #4A6FFF background, white text and icon
- "+" icon + "New Habit" label
- 6dp elevation shadow

BOTTOM NAVIGATION: Same floating pill bar, Habits icon filled/selected.
```

---

## Screen 4: Journal Screen

```
Mobile app journal screen. Light theme, #F8F9FC background. iPhone 15 Pro.

TOP SECTION — Journal Hero:
- "42 entries" count in small label text (#6E7A94)
- Title area with large text styling

MOOD CALENDAR:
- Full-width card, 16dp corner radius, white surface
- Month header row: left arrow, "March 2026" center in semibold, right arrow
- 7-column grid (Mon-Sun headers in tiny #9AA6BC text)
- Day cells (40dp squares):
  - Empty days: light gray #F0F2FA background
  - Days with entries: show mood emoji in center (various: happy face, neutral, sad, excited)
  - Today: highlighted border ring in primary blue
  - Selected day: filled primary blue background with white text
- Expandable — show collapse/expand toggle at bottom

JOURNAL ENTRY CARDS (scrollable list below calendar):
- 12dp gaps between cards, 16dp horizontal padding
- Each card: 12dp corner radius, white surface, subtle shadow
- Layout:
  - Top row: Large mood emoji (24dp) on left, date "Mar 22, 2026 - 8:30 AM" on right in #9AA6BC small text
  - Title: "Morning reflections" in bold (#2C3345)
  - Content preview: 2-3 lines of text in #6E7A94, truncated with ellipsis
  - Bottom row: small chips for linked goal (blue pill) and linked habit (green pill) if present
- Show 3-4 cards with different moods and content

EMPTY STATE (alternative version):
- Centered: "Your story starts here" in headline
- "Tap Write to capture your first thought" in secondary text
- Large journal/pencil illustration placeholder

EXTENDED FAB:
- Bottom-right, pill shape
- Primary blue background, white content
- Pencil icon + "Write" label

BOTTOM NAVIGATION: Same floating pill bar, Journal icon filled/selected.
```

---

## Screen 5: Profile Screen

```
Mobile app profile/settings screen. Light theme, #F8F9FC background. iPhone 15 Pro. Scrollable vertical layout.

USER PROFILE HEADER CARD:
- Full-width glass card, 16dp corner radius, 16dp padding
- Top row: Large circular avatar (64dp) with user initials "AK" on gradient background (blue #667EEA to purple #764BA2), white bold text
- Next to avatar: Display name "Alex Kim" in bold headline, pencil edit icon button
- Below name: email "alex@email.com" in #6E7A94 small text
- Below email: XP progress bar (full width, 8dp tall, rounded, primary blue fill), "Level 5 - 1,250/2,000 XP" label
- Sync status indicator: small cloud icon with green checkmark on right side of header

STATS ROW:
- Three equal columns in a card, 12dp corner radius
- Each column: large bold number on top, small label below
- "5" / Level | "1,250" / Total XP | "12" / Day Streak
- Thin vertical dividers between columns
- Surface variant background #F0F2FA

SECTION: "AI Coach & Achievements" — semibold section header in #6E7A94

COACH CARD:
- Glass card, 16dp corners
- Coach emoji/avatar (40dp circle) on left
- "Personal Coach" title, "Continue chatting" subtitle
- Chevron right arrow on far right

ACHIEVEMENTS CARD:
- Glass card, 12dp corners
- "8 of 24 earned" subtitle
- Row of 3 recent badge icons (small colored circles with icons inside)
- "See All" link with chevron on right

SECTION: "Insights & Analytics"

MENU ITEM — Life Balance:
- Row: heart icon (24dp, primary blue) | "Life Balance" bold + "Assess your life areas" subtitle | chevron right
- 16dp padding, thin divider below

SECTION: "Settings"

MENU ITEMS (stacked, each separated by thin #E8ECF4 divider):
- AI Provider: brain icon | "AI Provider" + "Gemini" subtitle | chevron
- Reminders: bell icon | "Reminders" + "Notification preferences" | chevron
- Backup & Sync: cloud icon | "Backup & Sync" + "Export and restore your data" | chevron
- Day Retrospective: history icon | "Day Retrospective" + "Browse past days" | chevron
- Send Feedback: message icon | "Send Feedback" + "Report bugs, request features" | chevron

SECTION: "Account"

SIGN OUT ITEM:
- Logout icon in error red #EA5455 | "Sign Out" in red text + email subtitle | chevron
- Red tint to indicate destructive action

BOTTOM NAVIGATION: Same floating pill bar, no item selected (profile accessed differently).
```

---

## Screen 6: Focus Timer — Setup State

```
Mobile app focus timer setup screen. Light theme, #F8F9FC background. iPhone 15 Pro.

TOP APP BAR:
- "Focus Timer" title in bold, left-aligned
- Back arrow on left
- White surface background

MAIN CONTENT — Scrollable vertical layout, 16dp horizontal padding:

TIMER MODE TOGGLE:
- Segmented control / toggle switch: "Free Flow" and "Timed" options
- Selected option: primary blue background, white text
- Unselected: surface variant background, secondary text

STATS LINE:
- Inline text: "45m today" in orange #FF9F43 bold + " - 12h all time" in #6E7A94
- Small text, left-aligned

MOOD PICKER:
- "How are you feeling?" label in semibold
- Horizontal row of 5 mood chips (pill shapes, 32dp height):
  - Each has emoji + mood name: Energized, Focused, Calm, Tired, Stressed
  - Selected: primary container background, primary text
  - Unselected: surface variant, secondary text

MILESTONE SELECTION:
- "What will you work on?" label
- 3 suggested milestone cards (glass cards, 12dp corners):
  - Each shows: goal category color dot, milestone title, parent goal name in small text
  - Selected: primary blue border highlight
- "Show more" expandable link if more milestones

DURATION CUSTOMIZER (visible in Timed mode):
- "Session length" label
- Row: minus button (circular outline) | "25 min" large bold number | plus button (circular outline)
- Preset chips below: "15m", "25m", "45m", "60m" pill buttons

AMBIENT SOUND PICKER:
- "Ambient Sound" label
- FlowRow of pill chips: "None", "Rain", "Forest", "Ocean", "Coffee Shop", "Lo-Fi"
- Selected: primary container, others: surface variant

THEME PICKER:
- "Visual Theme" label
- FlowRow of pill chips: "Minimal", "Aurora", "Waves", "Stars"

START BUTTON (sticky at bottom):
- Full-width button, 48dp height, primary blue #4A6FFF, white "Start Focus Session" text
- 16dp corner radius, centered
- 16dp bottom margin above nav bar area
```

---

## Screen 7: Focus Timer — Running State

```
Mobile app focus timer active/running state. Full-screen immersive design. iPhone 15 Pro.

NO TOP BAR — fully immersive.

BACKGROUND:
- Full-screen animated gradient background
- Deep blue-purple gradient (#0D0D2B to #1A1A4E to #2D1B69)
- Subtle animated aurora/wave effect (show as gentle gradient shifts)

CENTER — Progress Ring:
- Large circular progress ring (240dp diameter), centered on screen
- Ring: 8dp thick stroke, primary blue #4A6FFF fill on dark track
- Inside ring: Large countdown timer "18:42" in white, bold, display-size font
- Below timer inside ring: small "remaining" label in white 60% opacity

BELOW RING:
- Current milestone name in white semibold: "Write project proposal"
- Parent goal in white 60% opacity small text: "Career Growth"

CONTROL BUTTONS (centered, below content):
- Two circular buttons side by side, 56dp each
- Pause button: white circle with blue pause icon (two vertical bars)
- Stop button: outlined white circle with red X icon
- 24dp gap between buttons

BOTTOM CONTROLS:
- Small ambient sound indicator: speaker icon + "Rain" in white small text
- Theme indicator: palette icon + "Aurora" text
- Mood emoji display
- All in white at 70% opacity, horizontally arranged

OVERALL FEEL: Dark, calm, immersive. Minimal UI to avoid distraction. The progress ring is the clear focal point.
```

---

## Screen 8: Focus Timer — Completed State

```
Mobile app focus timer completion celebration screen. iPhone 15 Pro.

BACKGROUND:
- Same dark gradient as running state but slightly brighter
- Confetti/sparkle particles scattered across screen (small colorful dots: blue, purple, gold, coral)

CENTER CONTENT — Vertically centered:
- Large checkmark icon in green #28C76F circle (80dp)
- "Focus Complete!" in white, bold headline below
- "25 minutes of deep work" in white 70% opacity

XP GAIN DISPLAY:
- Animated badge: "+50 XP" in gold/orange #FF9F43 text, bold
- Star sparkle icons around it
- "Level 5 > Level 6" if level up occurred (celebration emphasis)

MILESTONE STATUS:
- If milestone completed: green checkmark + "Milestone completed!" text
- Progress update: "Project Proposal: 75% > 100%"

ACTION BUTTONS (bottom area):
- "Done" — full-width primary blue button, white text
- "Start Another" — outlined button below, primary blue border and text
- 16dp gap between buttons, 24dp horizontal padding
```

---

## Screen 9: AI Chat Screen

```
Mobile app AI coach chat screen. Light theme. iPhone 15 Pro.

TOP APP BAR:
- Left: back arrow
- Coach avatar: 36dp circle with emoji face (e.g., a friendly robot or person emoji) on gradient background
- Title column: "Luna" in bold title, "Life Coach" subtitle in small #6E7A94 text
- White surface background

CHAT MESSAGES (scrollable, full height):

COACH MESSAGE (left-aligned):
- Small coach avatar (24dp) on left margin
- Message bubble: surface variant #F0F2FA background, 16dp corner radius (top-left square corner), 12dp padding
- Text in #2C3345, body size
- Example: "Hi Alex! How are you feeling about your goals today? I noticed you've been making great progress on your Career goals."

USER MESSAGE (right-aligned):
- Bubble: primary container #ECF0FF background, 16dp corner radius (top-right square corner)
- Text in #0C2379, bold weight
- Example: "I'm feeling good but struggling with consistency on my habits"

COACH MESSAGE with suggestions:
- Message bubble as above with response text
- Below bubble: row of suggestion chip buttons
  - Pill shapes, outlined border in primary blue, primary blue text
  - Examples: "Tell me more", "Set a reminder", "Create a habit"
  - 8dp gaps between chips

Show 4-5 alternating messages to fill the screen naturally.

BOTTOM INPUT BAR:
- White surface background, thin top border #E8ECF4
- Row layout: outlined text field (flex width, 48-120dp height, 24dp corner radius, #9AA6BC placeholder "Type your message...")
- Send button: circular, 40dp, primary blue #4A6FFF background, white send arrow icon
- 12dp padding all around
```

---

## Screen 10: Achievements Screen

```
Mobile app achievements/badges gallery screen. Light theme, #F8F9FC background. iPhone 15 Pro.

TOP APP BAR:
- Large collapsible header
- Title: "Badges" in bold headline
- Subtitle: "12 of 24 earned" in #6E7A94
- Back arrow on left

MAIN CONTENT — Scrollable vertical layout:

BADGE CATEGORIES (repeat for each: Streak, Goals, Habits, Journal, Special):

CATEGORY HEADER:
- Category name in semibold title (#2C3345), e.g., "Streak Badges"
- 16dp top margin between categories

BADGE GRID — 4 columns, equal spacing:
- Each badge cell (~80dp square):
  - Circular background (64dp diameter)
    - EARNED: filled with badge-specific color (various: blue, green, purple, gold)
    - LOCKED: filled with #F0F2FA (gray), slightly desaturated
  - Icon centered inside circle (32dp, white for earned, #9AA6BC for locked)
  - Badge name below circle in tiny label text, centered
  - Locked badges: subtle lock overlay icon in corner

EXAMPLE BADGES:
- "First Step" (green, checkmark) — earned
- "7-Day Streak" (orange, flame) — earned
- "30-Day Streak" (gold, fire) — locked, with lock icon
- "Goal Crusher" (blue, trophy) — earned
- "Journal Master" (purple, book) — locked
- Show 4 per row, 2-3 rows per category

BADGE DETAIL BOTTOM SHEET (shown partially open at bottom):
- 24dp top corner radius, white background
- Drag handle bar at top (40dp wide, 4dp tall, #CBD0DD)
- Large badge icon (80dp circle, colored)
- Badge name: "7-Day Streak" in bold headline
- Description: "Complete habits 7 days in a row" in #6E7A94, centered
- Horizontal divider line
- Status: green checkmark + "Earned" + date "Mar 15, 2026"
- OR: lock icon + "Not Yet Earned" + progress bar (e.g., "5/7 days - 71%")
```

---

## Screen 11: Analytics Dashboard

```
Mobile app analytics/insights screen. Light theme, #F8F9FC background. iPhone 15 Pro. Scrollable vertical layout.

TOP APP BAR:
- Large collapsible header
- Back arrow on left
- Surface background

HERO STATS CARD:
- Full-width card with gradient background (light primary container to surface)
- Trophy icon + "Your Journey" bold title
- 2-column grid of stat boxes:
  - "Total Goals": large number "12" in indigo #6366F1, flag icon, "goals set" subtitle
  - "Success Rate": large "78%" in green #10B981, trending-up icon, "completion rate" subtitle
- Each stat box: white background, 12dp corners, 16dp padding, subtle shadow

PROGRESS OVERVIEW CARD:
- Timeline icon + "Progress Overview" header
- 3-column grid of mini stat cards:
  - "Active": "5" in large font, orange play icon (#F59E0B), orange-tinted background
  - "Completed": "8" in large font, green checkmark (#10B981), green-tinted background
  - "Due Soon": "2" in large font, red schedule icon (#EF4444), red-tinted background
- Each mini card: 12dp corners, color at 8% opacity background

CATEGORY BREAKDOWN CARD:
- Pie chart icon + "Categories" header
- List of category rows:
  - Each row: colored square (24dp, rounded 6dp) with category initial letter (white), category name, "avg 65%" progress text, count "4" on right
  - Category colors: Career blue, Financial green, Physical orange, etc.
  - 12dp gaps between rows

TIMELINE DISTRIBUTION CARD:
- Schedule icon + "Timeline Distribution" header
- 3-column grid:
  - "Short Term" card: blue #3B82F6 accent, count "4"
  - "Mid Term" card: purple #8B5CF6 accent, count "5"
  - "Long Term" card: cyan #06B6D4 accent, count "3"
- Each with icon, count, and label

PERFORMANCE INSIGHTS CARD:
- Lightbulb icon + "Smart Insights" header
- 3 insight rows, each with:
  - Emoji in colored circle (green for excellent, blue for good, orange for moderate)
  - Insight title in bold + description in secondary text
  - Example: "Consistency Champion" / "Your habit streak is impressive at 12 days"
  - Example: "Career Focus" / "Most goals in Career category — well balanced!"
```

---

## Screen 12: Life Balance Screen

```
Mobile app life balance assessment screen. Light theme, #F8F9FC background. iPhone 15 Pro. Scrollable.

TOP APP BAR:
- "Life Balance" title in bold
- Back arrow left, refresh icon right
- Surface container background

OVERALL SCORE CARD:
- Full-width card, primary container background (#ECF0FF)
- "Overall Balance" small label in #6E7A94
- Large score number "72" in display-size bold primary blue #4A6FFF
- Small trend indicator: up arrow + "3% from last week" in green #28C76F
- 16dp corner radius, 20dp padding

BALANCE WHEEL / RADAR CHART:
- Full-width card, white surface, 16dp corners
- Octagonal radar chart centered (200dp diameter)
- 8 axes radiating from center, each labeled with life area name at the tip:
  Career, Financial, Physical, Social, Emotional, Spiritual, Family, Learning
- Filled polygon shape connecting score points on each axis
- Fill: primary blue at 20% opacity, border: primary blue 2dp
- Each axis colored with its category color at the label
- Score dots (8dp circles) at each axis intersection point

AREA BREAKDOWN:
- "Area Scores" section header
- Stacked rows (full-width, 12dp gaps):
  - Each row: colored icon circle (32dp, category color) on left
  - Area name in bold
  - Horizontal progress bar (flex width, 8dp tall, rounded): filled portion in category color, track in #E8ECF4
  - Score percentage "85%" on far right in bold
  - Sort: highest score at top
- Example: Physical 85% (orange bar), Career 78% (blue), Financial 72% (green), Social 65% (purple), Learning 60% (teal), Emotional 55% (cyan), Family 50% (deep purple), Spiritual 45% (red)

INSIGHTS SECTION:
- "Insights" header
- Cards with colored left border (4dp):
  - Priority pill badge: "High" (red), "Medium" (orange), "Low" (blue)
  - Insight text: "Your Spiritual score is below average — consider adding a daily reflection habit"
  - "Get Advice" text button in primary blue
```

---

## Screen 13: Day Retrospective Screen

```
Mobile app retrospective/daily review screen. Light theme, #F8F9FC background. iPhone 15 Pro. Scrollable.

TOP APP BAR:
- "Day Retrospective" title in bold
- Back arrow on left
- Surface container background

DATE NAVIGATOR:
- Glass card, 16dp corners, full-width
- Row: left chevron button | calendar icon + "Today" (or "Yesterday" or "Mar 22, 2026") in bold + day name "Mon" below | right chevron button (disabled/gray if today)
- Tappable date area

DAY SUMMARY CARD:
- Glass card with gradient accent bar at top (4dp tall, blue #667EEA to purple #764BA2 horizontal gradient)
- 4 equal columns:
  - Mood: emoji + "Happy" label
  - Habits: checkmark emoji + "3/5 habits"
  - Focus: timer emoji + "45m focus"
  - Changes: arrows emoji + "2 changes"
- Each column: centered, small icon/emoji on top, label below in tiny text

MOOD & JOURNAL SECTION:
- "Mood & Journal" section header in semibold
- Glass card with journal entries:
  - Each entry row: large mood emoji (32dp) on left, title "Morning reflections" in bold + 2-line preview text in #6E7A94 on right
  - Thin dividers between entries

HABITS SECTION:
- "Habits (3/5)" section header
- Glass card with habit rows:
  - Each row: colored icon square (36dp, rounded 8dp, category color background) on left, habit title in middle, status circle on right (green checkmark 28dp if done, red X if missed)
  - Thin dividers between rows

FOCUS SESSIONS SECTION:
- "Focus Sessions" header
- Glass card with session rows:
  - Timer icon in orange circle (36dp, #FF6B35) on left
  - "25m (completed)" title + "25m planned" subtitle on middle
  - Orange pill badge "+50 XP" on right

GOAL CHANGES SECTION:
- "Goal Changes" header
- Glass card with timeline layout:
  - Vertical line connecting events
  - Each event: time label "2:30 PM" on left, colored dot on timeline (blue for status change, green for progress), description "Career Growth: In Progress > Completed" on right
```

---

## Screen 14: Welcome / Splash Screen

```
Mobile app welcome splash screen. Dark cyberpunk/neon aesthetic. iPhone 15 Pro. Full-screen immersive.

BACKGROUND:
- Full-screen vertical gradient:
  - Top: very dark blue #0D0D2B
  - Middle: deep purple #1A1A4E transitioning to #2D1B69
  - Bottom: back to dark blue #0D0D2B
- Subtle animated glow effect in the center (soft purple/blue radial gradient, pulsing)
- Dark overlay gradients for depth (black at 20-70% opacity)

CENTER CONTENT — Vertically and horizontally centered:
- Large bold text, three lines, monospace font:
  Line 1: "Design"
  Line 2: "Your"
  Line 3: "Future"
- Text effect: neon gradient coloring
  - Left edge of text: cyan #00F0FF
  - Center: white
  - Right edge: pink #FF00E5
- Font: Extra bold, display size (36-40sp), letter spacing 3sp, line height 52sp
- Subtle glow/bloom effect around the text (matching neon colors at low opacity)
- Blinking cursor "|" after the last character in neon green #39FF14 (typewriter effect frozen frame)

NO NAVIGATION ELEMENTS — This is a splash/loading screen that auto-transitions.

OVERALL FEEL: Dramatic, futuristic, motivational. Dark with vibrant neon accents. Minimal — just the powerful statement centered on screen.
```

---

## Screen 15: Goal Detail Screen

```
Mobile app goal detail screen. Light theme. iPhone 15 Pro. Scrollable vertical layout.

HEADER — Collapsible gradient header:
- Full-width gradient background using goal's category color (e.g., Career: #4A6FFF to #2C42B0)
- White text: Goal title "Launch Side Project" in bold headline
- Back arrow (white) on left, overflow menu (3 dots, white) on right
- Collapses on scroll, title moves to standard top bar position

STATUS TOGGLE ROW:
- Horizontal row of 5 small pill buttons:
  - "Not Started" (gray), "In Progress" (blue, selected/filled), "Completed" (green), "On Hold" (orange), "Cancelled" (red)
  - Selected: filled with color + white text
  - Others: outline border + colored text
- 16dp horizontal padding, scrollable if overflow

NOTES CARD:
- Glass card, 16dp corners
- Icon: document icon in header row + "Notes" label + small edit pencil button (circular, primary container)
- Body: "This goal is about building and launching a side project by Q2..." in body text
- Max 5 lines visible

AI REASONING CARD:
- Glass card with purple accent
- AutoAwesome/sparkle icon in purple #7C4DFF + "Why this goal?" title + small "AI" badge (purple pill)
- Body: "Based on your career trajectory and interests, this goal aligns with..." in #6E7A94
- Expandable (3 lines collapsed with "Read more")

MILESTONES CARD:
- Glass card, 16dp corners
- Header row: flag icon + "Milestones" title + progress badge "2/4" (pill) + circular add button
- Milestone list:
  - Each row: checkbox icon on left (green circle checkmark if done, empty circle if pending), milestone title, due date in small text on right
  - Example: [checked] "Research market" - "Mar 10"
  - Example: [checked] "Write business plan" - "Mar 20"
  - Example: [unchecked] "Build MVP" - "Apr 15"
  - Example: [unchecked] "Launch beta" - "May 1"
- Thin dividers between items

REFLECTIONS CARD:
- Glass card, 16dp corners
- Header: book icon + "Reflections" title + count badge "3" + circular add button
- List of entries (max 3 shown):
  - Each: mood emoji (24dp) on left, entry title + date on right
  - Example: happy face + "Making great progress" + "Mar 22"
- "View all 3 reflections" link at bottom in primary blue
```

---

## Screen 16: Bottom Navigation Bar (Component)

```
Mobile app bottom navigation bar component. Show isolated on #F8F9FC background. iPhone 15 Pro, positioned at bottom.

SHAPE & POSITION:
- Floating bar, NOT edge-to-edge
- 16dp horizontal margins from screen edges
- 32dp from bottom edge of screen
- Pill shape: 50dp corner radius (fully rounded ends)
- Height: 64dp

GLASS MORPHISM EFFECT:
- Background: white at 85% opacity (semi-transparent)
- 1dp border with gradient: white at 50% alpha on top-left to white at 10% alpha on bottom-right
- Very subtle shadow (2dp elevation)
- Slight blur/frosted glass feel

NAVIGATION ITEMS — 4 evenly spaced icons:
- Home: house icon
- Goals: target/flag icon
- Habits: checkmark-in-circle icon
- Journal: book/document icon

SELECTED STATE (show Home as selected):
- Icon: filled variant, primary blue #4A6FFF color
- Small indicator pill below icon: primary container #ECF0FF background

UNSELECTED STATE (other 3 items):
- Icon: outlined variant, #9AA6BC color
- No indicator

NO TEXT LABELS — icons only for clean minimal look.

Show the bar floating above the background with clear margins, demonstrating the glass/transparent effect.
```

---

## Usage Tips for Google Stitch
could
1. **Start with the Design System Preamble** — paste it as context before your first screen prompt
2. **One screen per generation** — paste one screen prompt at a time for best results
3. **Iterate** — after generating, tell Stitch to adjust specific elements ("make the progress bar thicker", "change the card shadows")
4. **Consistency** — reference "same style as previous screens" in follow-up prompts
5. **Variants** — ask Stitch for "dark mode version" or "empty state version" after the main design
6. **Export** — download each generated screen as PNG for your design library
