package com.frostwire.android.gui.activities;

import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import com.frostwire.android.R;
import com.frostwire.android.test.utils.PreferencesManipulator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class FirstLaunchTest {

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule<MainActivity>(MainActivity.class) {
        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();
            PreferencesManipulator prefsManipulator =
                    new PreferencesManipulator(InstrumentationRegistry.getTargetContext());
           prefsManipulator.userHasNeverStartedApp();
        }
    };

    @Test
    public void firstRun_acceptTerms_finishWizard() {

        //1. We should see the terms of use dialog when we first start the app

        //This is from the TermsUseDialog started from the main activity
        //Layout: R.layout.dialog_default_scroll
        onView(withId(R.id.dialog_default_scroll_title)).check(matches(isDisplayed()));
        onView(withId(R.id.dialog_default_scroll_button_yes)).perform(click());

        // 2. We should see the first page of the wizard. It's the "Welcome to Frostwire" screen
        //This is from a the WizardActivity
        //Layout: R.layout.activity_wizard
        onView(withId(R.id.activity_wizard_view_flipper)).check(matches(isDisplayed()));
        onView(withId(R.id.activity_wizard_button_next)).perform(click());

        // 3. We should see the second page of the wizard. It's the page explaining it's for
        // authorized use only.

        //This is the check box the user must click saying they woun't use the app for copyright
        //infringement
        //Layout: R.layout.view_intent_wizard_page
        //onView(withId(R.id.view_intent_wizard_page_check_accept)).perform(click());
        onView(withId(R.id.activity_wizard_button_next)).perform(click());

        // 4. "Installation is complete" dialog
        //Layout: R.layout.view_social_buttons
        onView(withId(R.id.view_social_buttons_installation_complete_layout)).check(matches(isDisplayed()));
        onView(withId(android.R.id.button1)).perform(click());
    }


}
