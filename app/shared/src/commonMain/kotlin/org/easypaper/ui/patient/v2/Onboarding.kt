package org.easypaper.ui.patient.v2

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.launch
import org.easypaper.ui.components.*
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun Onboarding(settings: PatientSettings, saving: Boolean, error: Boolean,
               onFinish: (openInvite: Boolean) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val tourPager = rememberPagerState(pageCount = { tourPages.size })
    val scope = rememberCoroutineScope()
    val motion = EasyPaperTheme.motion
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Crossfade(step, animationSpec = motion.fade(), label = "onboarding") { page ->
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.cell)
            .windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            val viewportHeight = maxHeight
            Column(Modifier.widthIn(max = spacing.patientMaxWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                .padding(horizontal = spacing.xl, vertical = spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Spacer(Modifier.height(spacing.xl))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
                    if (page == 0) WelcomeCollage()
                    if (page != 1) WordFitText(stringResource(when (page) {
                        0 -> Res.string.p2_diary_title
                        2 -> Res.string.p2_sign_in_title
                        else -> Res.string.p2_privacy
                    }), style = EasyPaperTheme.typography.largeTitle, color = colors.label,
                        modifier = Modifier.semantics { heading() }, textAlign = TextAlign.Center)
                    when (page) {
                        0 -> Text(stringResource(Res.string.p2_diary_subtitle), style = EasyPaperTheme.typography.callout,
                            color = colors.secondaryLabel, textAlign = TextAlign.Center)
                        1 -> OnboardingTour(tourPager)
                        2 -> {
                            GroupedSection {
                                TextFieldRow(settings.name.value.orEmpty(), settings.name::update,
                                    stringResource(Res.string.p2_optional_name))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                                CapsuleButton(stringResource(Res.string.p2_continue), { step = 3 }, prominent = true)
                                CapsuleButton(stringResource(Res.string.p2_continue_google), {}, enabled = false,
                                    badge = stringResource(Res.string.p2_soon))
                                CapsuleButton(stringResource(Res.string.p2_continue_email), {}, enabled = false,
                                    badge = stringResource(Res.string.p2_soon))
                            }
                            Text(stringResource(Res.string.p2_local_profile_privacy), style = EasyPaperTheme.typography.footnote,
                                color = colors.secondaryLabel, textAlign = TextAlign.Center)
                        }
                        else -> {
                            Text(stringResource(Res.string.p2_privacy_body), style = EasyPaperTheme.typography.body,
                                color = colors.label, textAlign = TextAlign.Center)
                            GroupedSection(footer = stringResource(Res.string.p2_reminders_body)) {
                                val reminderLabel = stringResource(Res.string.p2_reminders)
                                ListRow(reminderLabel, chevron = false, trailing = {
                                    IosSwitch(settings.reminders.value == "true", { settings.reminders.update(it.toString()) },
                                        Modifier.semantics { contentDescription = reminderLabel })
                                })
                            }
                            LanguagePicker(settings)
                        }
                    }
                    if (error) PatientMessage(stringResource(Res.string.p2_error), error = true)
                }
                Column(Modifier.fillMaxWidth().padding(top = spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (page == 1) {
                        val lastPage = tourPager.currentPage == tourPages.lastIndex
                        CapsuleButton(stringResource(if (lastPage) Res.string.p2_continue else Res.string.p2_tour_next), {
                            if (lastPage) step = 2 else scope.launch {
                                val nextPage = (tourPager.currentPage + 1).coerceAtMost(tourPages.lastIndex)
                                if (motion.reduceMotion) tourPager.scrollToPage(nextPage)
                                else tourPager.animateScrollToPage(nextPage)
                            }
                        }, prominent = true, enabled = !saving && !tourPager.isScrollInProgress)
                        PlainButton(stringResource(Res.string.p2_tour_skip), { step = 2 }, enabled = !saving)
                    } else if (page != 2) CapsuleButton(stringResource(when {
                        saving -> Res.string.p2_working
                        page == 0 -> Res.string.p2_get_started
                        else -> Res.string.p2_continue
                    }), { if (page == 0) step = 1 else onFinish(false) }, prominent = true, enabled = !saving)
                    if (page > 0) PlainButton(stringResource(Res.string.p2_back), { step-- }, enabled = !saving)
                }
            }
        }
    }
}

private data class TourPage(
    val category: Category,
    val icon: DrawableResource,
    val title: StringResource,
    val body: StringResource,
)

private val tourPages = listOf(
    TourPage(Category.Water, Res.drawable.ic_line_water, Res.string.p2_tour_track_title, Res.string.p2_tour_track_body),
    TourPage(Category.Questionnaire, Res.drawable.ic_checklist, Res.string.p2_tour_plan_title, Res.string.p2_tour_plan_body),
    TourPage(Category.DoctorQuestion, Res.drawable.ic_qr_code_2, Res.string.p2_tour_share_title, Res.string.p2_tour_share_body),
    TourPage(Category.Medicines, Res.drawable.ic_check_circle, Res.string.p2_tour_private_title, Res.string.p2_tour_private_body),
)

@Composable
private fun OnboardingTour(pager: PagerState) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
            val page = tourPages[index]
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
                Box(Modifier.fillMaxWidth().height(spacing.tourIllustrationHeight)
                    .background(page.category.soft(colors), EasyPaperTheme.shapes.widget), contentAlignment = Alignment.Center) {
                    Icon(painterResource(page.icon), null, Modifier.size(spacing.heroIcon), tint = page.category.softText(colors))
                }
                WordFitText(stringResource(page.title), style = EasyPaperTheme.typography.title1, color = colors.label,
                    modifier = Modifier.semantics { heading() }, textAlign = TextAlign.Center)
                // three lines reserved: every page has the same height, so the picture and title do not jump on swipe
                Text(stringResource(page.body), style = EasyPaperTheme.typography.body,
                    color = colors.secondaryLabel, textAlign = TextAlign.Center, minLines = 3)
            }
        }
        val pageDescription = stringResource(Res.string.p2_tour_page, pager.currentPage + 1, tourPages.size)
        Row(Modifier.semantics { contentDescription = pageDescription },
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            repeat(tourPages.size) { index ->
                Box(Modifier.size(spacing.tourPageIndicatorSize)
                    .background(if (pager.currentPage == index) colors.tint else colors.separator, EasyPaperTheme.shapes.capsule))
            }
        }
    }
}

@Composable
private fun WelcomeCollage() {
    val spacing = EasyPaperTheme.spacing
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CollageTile(Category.BloodPressure, tall = true)
            CollageTile(Category.Food, tall = false)
        }
        Column(Modifier.weight(1f).padding(top = spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CollageTile(Category.Water, tall = false)
            CollageTile(Category.Medicines, tall = true)
        }
    }
}

@Composable
private fun CollageTile(category: Category, tall: Boolean) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Box(Modifier.fillMaxWidth().height(if (tall) spacing.collageTall else spacing.collageShort)
        .background(category.soft(colors), EasyPaperTheme.shapes.widget), contentAlignment = Alignment.Center) {
        Icon(painterResource(category.lineIcon), null, Modifier.size(spacing.heroIcon), tint = category.softText(colors))
    }
}
