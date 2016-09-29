package com.frostwire.android.gui.activities;


import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import com.frostwire.android.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withClassName;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class InstallingTest {

    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void installingTest() {
        ViewInteraction checkBox = onView(
                allOf(withId(R.id.view_general_wizard_page_check_ux_stats), withText("Anonymous Usage Statistics"), isDisplayed()));
        checkBox.perform(click());

        ViewInteraction button = onView(
                allOf(withId(R.id.activity_wizard_button_next), withText("Next"),
                        withParent(withId(R.id.activity_wizard_buttons)),
                        isDisplayed()));
        button.perform(click());

        ViewInteraction checkBox2 = onView(
                allOf(withId(R.id.view_intent_wizard_page_check_accept_copyright), withText("I will not use FrostWire for copyright infringement"), isDisplayed()));
        checkBox2.perform(click());

        ViewInteraction checkBox3 = onView(
                allOf(withId(R.id.view_intent_wizard_page_check_accept_tou), withText("I have read and agree to the"), isDisplayed()));
        checkBox3.perform(click());

        ViewInteraction button2 = onView(
                allOf(withId(R.id.activity_wizard_button_next), withText("Finish"),
                        withParent(withId(R.id.activity_wizard_buttons)),
                        isDisplayed()));
        button2.perform(click());

        ViewInteraction button3 = onView(
                allOf(withId(android.R.id.button1), withText("OK"), isDisplayed()));
        button3.perform(click());

        ViewInteraction linearLayout = onView(
                allOf(withContentDescription("FrostWire, Open navigation drawer"),
                        withParent(allOf(withClassName(is("com.android.internal.widget.ActionBarView")),
                                withParent(withClassName(is("com.android.internal.widget.ActionBarContainer"))))),
                        isDisplayed()));
        linearLayout.perform(click());

        ViewInteraction linearLayout2 = onView(
                allOf(withContentDescription("FrostWire, Close navigation drawer"),
                        withParent(allOf(withClassName(is("com.android.internal.widget.ActionBarView")),
                                withParent(withClassName(is("com.android.internal.widget.ActionBarContainer"))))),
                        isDisplayed()));
        linearLayout2.perform(click());

    }

}
