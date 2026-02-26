import Foundation

struct WidgetDashboardData: Codable {
    let currentStreak: Int
    let totalXp: Int
    let currentLevel: Int
    let activeGoals: Int
    let habitsTotal: Int
    let habitsDoneToday: Int
    let lastUpdated: String

    static let empty = WidgetDashboardData(
        currentStreak: 0,
        totalXp: 0,
        currentLevel: 1,
        activeGoals: 0,
        habitsTotal: 0,
        habitsDoneToday: 0,
        lastUpdated: ""
    )

    var xpForNextLevel: Int {
        Int(Double(100 * (currentLevel + 1)) * 1.5)
    }

    var xpInCurrentLevel: Int {
        totalXp - totalXpForLevel(currentLevel)
    }

    var xpNeededForNextLevel: Int {
        xpForNextLevel - Int(Double(100 * currentLevel) * 1.5)
    }

    var levelProgress: Double {
        guard xpNeededForNextLevel > 0 else { return 0 }
        return min(Double(xpInCurrentLevel) / Double(xpNeededForNextLevel), 1.0)
    }

    private func totalXpForLevel(_ level: Int) -> Int {
        var total = 0
        for l in 1..<level {
            total += Int(Double(100 * l) * 1.5)
        }
        return total
    }
}

struct WidgetHabitData: Codable, Identifiable {
    let id: String
    let title: String
    let isCompletedToday: Bool
    let currentStreak: Int
    let category: String

    static let sample = WidgetHabitData(
        id: "sample",
        title: "Morning Meditation",
        isCompletedToday: false,
        currentStreak: 5,
        category: "EMOTIONAL"
    )
}
