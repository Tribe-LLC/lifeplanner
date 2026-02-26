import WidgetKit
import SwiftUI

// MARK: - Timeline Provider

struct DashboardEntry: TimelineEntry {
    let date: Date
    let data: WidgetDashboardData
}

struct DashboardTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> DashboardEntry {
        DashboardEntry(date: Date(), data: .empty)
    }

    func getSnapshot(in context: Context, completion: @escaping (DashboardEntry) -> Void) {
        let data = SharedDataReader.readDashboardData()
        completion(DashboardEntry(date: Date(), data: data))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DashboardEntry>) -> Void) {
        let data = SharedDataReader.readDashboardData()
        let entry = DashboardEntry(date: Date(), data: data)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 30, to: Date())!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }
}

// MARK: - Widget Views

struct SmallDashboardView: View {
    let data: WidgetDashboardData

    var body: some View {
        VStack(spacing: 4) {
            Text("\u{1F525}")
                .font(.title)

            Text("\(data.currentStreak)")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.orange)

            Text("\(data.habitsDoneToday)/\(data.habitsTotal) habits")
                .font(.caption2)
                .foregroundColor(.secondary)

            Text("Lv. \(data.currentLevel)")
                .font(.caption2)
                .fontWeight(.medium)
                .foregroundColor(.indigo)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct MediumDashboardView: View {
    let data: WidgetDashboardData

    var body: some View {
        VStack(spacing: 8) {
            // Header row
            HStack {
                Text("\u{1F525} \(data.currentStreak) day streak")
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.orange)
                Spacer()
                Text(Date(), style: .date)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }

            // Stats row
            HStack {
                StatItemView(value: "\(data.habitsDoneToday)/\(data.habitsTotal)", label: "Habits", color: .green)
                Spacer()
                StatItemView(value: "\(data.activeGoals)", label: "Goals", color: .indigo)
                Spacer()
                StatItemView(value: "\(data.currentLevel)", label: "Level", color: .indigo)
            }

            // XP progress bar
            HStack(spacing: 4) {
                Text("XP")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.secondary)

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color.gray.opacity(0.2))

                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color.indigo)
                            .frame(width: geo.size.width * data.levelProgress)
                    }
                }
                .frame(height: 6)

                Text("\(data.xpInCurrentLevel)/\(data.xpNeededForNextLevel)")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
        }
    }
}

struct StatItemView: View {
    let value: String
    let label: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(color)
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(.secondary)
        }
    }
}

// MARK: - Widget Definition

struct DailyDashboardWidget: Widget {
    let kind = "DailyDashboardWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: DashboardTimelineProvider()) { entry in
            DashboardWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Daily Dashboard")
        .description("View your streak, habits, goals, and XP at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct DashboardWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: DashboardEntry

    var body: some View {
        switch family {
        case .systemSmall:
            SmallDashboardView(data: entry.data)
        default:
            MediumDashboardView(data: entry.data)
        }
    }
}

// MARK: - Previews

#Preview(as: .systemSmall) {
    DailyDashboardWidget()
} timeline: {
    DashboardEntry(date: .now, data: WidgetDashboardData(
        currentStreak: 7,
        totalXp: 350,
        currentLevel: 3,
        activeGoals: 5,
        habitsTotal: 5,
        habitsDoneToday: 3,
        lastUpdated: "2026-02-23"
    ))
}

#Preview(as: .systemMedium) {
    DailyDashboardWidget()
} timeline: {
    DashboardEntry(date: .now, data: WidgetDashboardData(
        currentStreak: 7,
        totalXp: 350,
        currentLevel: 3,
        activeGoals: 5,
        habitsTotal: 5,
        habitsDoneToday: 3,
        lastUpdated: "2026-02-23"
    ))
}
