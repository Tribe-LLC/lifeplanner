import Foundation

struct SharedDataReader {
    private static let appGroupID = "group.az.tribe.lifeplanner"
    private static let dashboardKey = "widget_dashboard_data"
    private static let habitsKey = "widget_habits_data"

    static func readDashboardData() -> WidgetDashboardData {
        guard let defaults = UserDefaults(suiteName: appGroupID),
              let jsonString = defaults.string(forKey: dashboardKey),
              let data = jsonString.data(using: .utf8) else {
            return .empty
        }

        do {
            return try JSONDecoder().decode(WidgetDashboardData.self, from: data)
        } catch {
            print("Failed to decode dashboard data: \(error)")
            return .empty
        }
    }

    static func readHabitsData() -> [WidgetHabitData] {
        guard let defaults = UserDefaults(suiteName: appGroupID) else {
            return []
        }

        guard let jsonString = defaults.string(forKey: habitsKey),
              let data = jsonString.data(using: .utf8) else {
            return []
        }

        // Try Codable first
        if let habits = try? JSONDecoder().decode([WidgetHabitData].self, from: data) {
            return habits
        }

        // Fallback: manual JSON parsing for kotlinx.serialization format differences
        guard let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }

        return array.compactMap { dict in
            guard let id = dict["id"] as? String,
                  let title = dict["title"] as? String else { return nil }

            let isCompleted: Bool
            if let boolVal = dict["isCompletedToday"] as? Bool {
                isCompleted = boolVal
            } else if let intVal = dict["isCompletedToday"] as? Int {
                isCompleted = intVal != 0
            } else if let strVal = dict["isCompletedToday"] as? String {
                isCompleted = strVal == "true"
            } else {
                isCompleted = false
            }

            let streak: Int
            if let intVal = dict["currentStreak"] as? Int {
                streak = intVal
            } else if let strVal = dict["currentStreak"] as? String, let parsed = Int(strVal) {
                streak = parsed
            } else {
                streak = 0
            }

            let category = dict["category"] as? String ?? ""

            return WidgetHabitData(
                id: id,
                title: title,
                isCompletedToday: isCompleted,
                currentStreak: streak,
                category: category
            )
        }
    }

    private static let pendingCheckInsKey = "widget_pending_checkins"

    static func writeHabitCheckIn(habitId: String) {
        guard let defaults = UserDefaults(suiteName: appGroupID) else { return }

        // 1. Update widget display data immediately
        var habits = readHabitsData()
        if let index = habits.firstIndex(where: { $0.id == habitId }) {
            let habit = habits[index]
            habits[index] = WidgetHabitData(
                id: habit.id,
                title: habit.title,
                isCompletedToday: true,
                currentStreak: habit.currentStreak + 1,
                category: habit.category
            )

            if let encoded = try? JSONEncoder().encode(habits),
               let jsonString = String(data: encoded, encoding: .utf8) {
                defaults.set(jsonString, forKey: habitsKey)
            }

            // Also update dashboard done count
            var dashboard = readDashboardData()
            dashboard = WidgetDashboardData(
                currentStreak: dashboard.currentStreak,
                totalXp: dashboard.totalXp + 5,
                currentLevel: dashboard.currentLevel,
                activeGoals: dashboard.activeGoals,
                habitsTotal: dashboard.habitsTotal,
                habitsDoneToday: dashboard.habitsDoneToday + 1,
                lastUpdated: dashboard.lastUpdated
            )

            if let encoded = try? JSONEncoder().encode(dashboard),
               let jsonString = String(data: encoded, encoding: .utf8) {
                defaults.set(jsonString, forKey: dashboardKey)
            }
        }

        // 2. Queue pending check-in for the main app to process into DB
        var pending = defaults.stringArray(forKey: pendingCheckInsKey) ?? []
        if !pending.contains(habitId) {
            pending.append(habitId)
            defaults.set(pending, forKey: pendingCheckInsKey)
        }

        defaults.synchronize()
    }

    static func readPendingCheckIns() -> [String] {
        guard let defaults = UserDefaults(suiteName: appGroupID) else { return [] }
        return defaults.stringArray(forKey: pendingCheckInsKey) ?? []
    }

    static func clearPendingCheckIns() {
        guard let defaults = UserDefaults(suiteName: appGroupID) else { return }
        defaults.removeObject(forKey: pendingCheckInsKey)
        defaults.synchronize()
    }
}
