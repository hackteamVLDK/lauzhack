package org.easypaper.ui.components

import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.FoodWidget
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.WaterWidget
import org.easypaper.model.WidgetSpec
import org.easypaper.ui.theme.Category

/** The design-system category (tile color and glyph) of each widget kind. */
val WidgetSpec.category: Category
    get() = when (this) {
        is BloodPressureWidget -> Category.BloodPressure
        is WaterWidget -> Category.Water
        is FoodWidget -> Category.Food
        is MedicationsWidget -> Category.Medicines
        is QuestionnaireWidget -> Category.Questionnaire
        is DiaryWidget -> Category.Diary
        is DoctorQuestionsWidget -> Category.DoctorQuestion
    }
