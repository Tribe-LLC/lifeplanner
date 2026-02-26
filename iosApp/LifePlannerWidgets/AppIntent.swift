//
//  AppIntent.swift
//  LifePlannerWidgets
//
//  Created by Kamran Mammadov on 2/23/26.
//  Copyright © 2026 orgName. All rights reserved.
//

import WidgetKit
import AppIntents

struct HabitCheckInIntent: AppIntent {
    static var title: LocalizedStringResource = "Check In Habit"
    static var description = IntentDescription("Mark a habit as completed for today")

    @Parameter(title: "Habit ID")
    var habitId: String

    init() {
        self.habitId = ""
    }

    init(habitId: String) {
        self.habitId = habitId
    }

    func perform() async throws -> some IntentResult {
        SharedDataReader.writeHabitCheckIn(habitId: habitId)
        WidgetCenter.shared.reloadTimelines(ofKind: "HabitCheckInWidget")
        WidgetCenter.shared.reloadTimelines(ofKind: "DailyDashboardWidget")
        return .result()
    }
}
