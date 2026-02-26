//
//  LifePlannerWidgetsBundle.swift
//  LifePlannerWidgets
//
//  Created by Kamran Mammadov on 2/23/26.
//  Copyright © 2026 orgName. All rights reserved.
//

import WidgetKit
import SwiftUI

@main
struct LifePlannerWidgetsBundle: WidgetBundle {
    var body: some Widget {
        DailyDashboardWidget()
        HabitCheckInWidget()
    }
}
