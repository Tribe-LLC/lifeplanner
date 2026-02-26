import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Timeline Provider

struct HabitEntry: TimelineEntry {
    let date: Date
    let habits: [WidgetHabitData]
}

struct HabitTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> HabitEntry {
        HabitEntry(date: Date(), habits: [.sample])
    }

    func getSnapshot(in context: Context, completion: @escaping (HabitEntry) -> Void) {
        let habits = SharedDataReader.readHabitsData()
        completion(HabitEntry(date: Date(), habits: habits))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HabitEntry>) -> Void) {
        let habits = SharedDataReader.readHabitsData()
        let entry = HabitEntry(date: Date(), habits: habits)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 30, to: Date())!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }
}

// HabitCheckInIntent is defined in AppIntent.swift

// MARK: - Widget Views

struct HabitCheckInEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: HabitEntry

    var body: some View {
        if entry.habits.isEmpty {
            EmptyHabitsWidgetView()
        } else {
            let maxHabits = family == .systemLarge ? 6 : 3
            let showStreak = family == .systemLarge
            HabitListView(
                habits: Array(entry.habits.prefix(maxHabits)),
                showStreak: showStreak
            )
        }
    }
}

struct EmptyHabitsWidgetView: View {
    var body: some View {
        VStack(spacing: 4) {
            Text("No habits yet")
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(.secondary)
            Text("Tap to add your first habit")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct HabitListView: View {
    let habits: [WidgetHabitData]
    let showStreak: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Header
            HStack {
                Text("Today's Habits")
                    .font(.subheadline)
                    .fontWeight(.bold)
                Spacer()
                let doneCount = habits.filter(\.isCompletedToday).count
                Text("\(doneCount)/\(habits.count)")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.green)
            }

            // Habit rows
            ForEach(habits) { habit in
                HabitRowView(habit: habit, showStreak: showStreak)
            }
        }
    }
}

struct HabitRowView: View {
    let habit: WidgetHabitData
    let showStreak: Bool

    var body: some View {
        HStack(spacing: 8) {
            // Checkbox
            if habit.isCompletedToday {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
                    .font(.title3)
            } else {
                Button(intent: HabitCheckInIntent(habitId: habit.id)) {
                    Image(systemName: "circle")
                        .foregroundColor(.gray)
                        .font(.title3)
                }
                .buttonStyle(.plain)
            }

            // Title
            Text(habit.title)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(habit.isCompletedToday ? .secondary : .primary)
                .lineLimit(1)

            Spacer()

            // Streak (large widget only)
            if showStreak && habit.currentStreak > 0 {
                HStack(spacing: 2) {
                    Text("\u{1F525}")
                        .font(.caption2)
                    Text("\(habit.currentStreak)")
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            }
        }
        .padding(.vertical, 2)
        .padding(.horizontal, 6)
        .background(Color(.systemGray6))
        .cornerRadius(6)
    }
}

// MARK: - Widget Definition

struct HabitCheckInWidget: Widget {
    let kind = "HabitCheckInWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: HabitTimelineProvider()) { entry in
            HabitCheckInEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Habit Check-In")
        .description("Check in your habits directly from your home screen.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - Previews

#Preview(as: .systemMedium) {
    HabitCheckInWidget()
} timeline: {
    HabitEntry(date: .now, habits: [
        WidgetHabitData(id: "1", title: "Morning Meditation", isCompletedToday: true, currentStreak: 5, category: "EMOTIONAL"),
        WidgetHabitData(id: "2", title: "Exercise", isCompletedToday: false, currentStreak: 3, category: "PHYSICAL"),
        WidgetHabitData(id: "3", title: "Read 30 min", isCompletedToday: false, currentStreak: 0, category: "CAREER"),
    ])
}

#Preview(as: .systemLarge) {
    HabitCheckInWidget()
} timeline: {
    HabitEntry(date: .now, habits: [
        WidgetHabitData(id: "1", title: "Morning Meditation", isCompletedToday: true, currentStreak: 5, category: "EMOTIONAL"),
        WidgetHabitData(id: "2", title: "Exercise", isCompletedToday: false, currentStreak: 3, category: "PHYSICAL"),
        WidgetHabitData(id: "3", title: "Read 30 min", isCompletedToday: false, currentStreak: 0, category: "CAREER"),
        WidgetHabitData(id: "4", title: "Drink Water", isCompletedToday: true, currentStreak: 12, category: "PHYSICAL"),
        WidgetHabitData(id: "5", title: "Journal", isCompletedToday: false, currentStreak: 1, category: "EMOTIONAL"),
        WidgetHabitData(id: "6", title: "Budget Review", isCompletedToday: false, currentStreak: 0, category: "FINANCIAL"),
    ])
}
