# App Store & Play Store Screenshots

Organized for one-click import via [AppScreens](https://appscreens.com) "Upload Folder".

## Folder Structure

```
screenshots/
├── iphone_6.7/en/          # iPhone 6.7" (1290x2796)
├── ipad_12/en/             # iPad 12" (2048x2732)
├── galaxy_s25_6.2/en/      # Samsung Galaxy S25 6.2" (1080x2340)
├── galaxy_tab_s8_14.6/en/  # Galaxy Tab S8 Ultra 14.6" (1848x2960)
└── README.md
```

## Naming Convention

Zero-padded, language-prefixed, natural sort order:

```
en_01_home.png
en_02_goals.png
en_03_habits.png
en_04_journal.png
en_05_ai_coach.png
en_06_focus_timer.png
en_07_achievements.png
en_08_life_balance.png
```

## Screenshot Sequence

| # | Screen | What to show |
|---|--------|-------------|
| 01 | Home | Dashboard with streak, XP bar, widgets |
| 02 | Goals | Goal list with progress bars and categories |
| 03 | Habits | Habit tracker with daily check-ins |
| 04 | Journal | Journal entries with mood indicators |
| 05 | AI Coach | Chat with Luna or coach council |
| 06 | Focus Timer | Focus session with timer and theme |
| 07 | Achievements | Badges and level progress |
| 08 | Life Balance | Radar chart with 8 life areas |

## Capturing Screenshots

### Android (adb)
```bash
# Phone (Galaxy S25 - 1080x2340)
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./galaxy_s25_6.2/en/en_01_home.png

# Tablet (Galaxy Tab S8 Ultra - 1848x2960)
adb pull /sdcard/screenshot.png ./galaxy_tab_s8_14.6/en/en_01_home.png
```

### iOS Simulator (xcrun)
```bash
# iPhone 6.7"
xcrun simctl io booted screenshot ./iphone_6.7/en/en_01_home.png

# iPad 12"
xcrun simctl io booted screenshot ./ipad_12/en/en_01_home.png
```

## Import to AppScreens

1. Click **Upload Folder** in AppScreens
2. Select the `screenshots/` folder
3. AppScreens auto-detects language (`en_`), aspect ratio, and sort order
4. Review and click **Done**

## Adding Languages

Create new subfolders per language:
```
iphone_6.7/az/az_01_home.png
iphone_6.7/ru/ru_01_home.png
```
