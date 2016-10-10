package com.frostwire.android.gui.activities;


import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.frostwire.android.R;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
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
public class FilterArchiveTest {

    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void filterArchiveTest() {
        ViewInteraction checkBox = onView(
                allOf(withId(R.id.view_general_wizard_page_check_seed_finished_torrents), withText("Seed Finished Torrents"), isDisplayed()));
        checkBox.perform(click());

        ViewInteraction checkBox2 = onView(
                allOf(withId(R.id.view_general_wizard_page_check_ux_stats), withText("Anonymous Usage Statistics"), isDisplayed()));
        checkBox2.perform(click());

        ViewInteraction button = onView(
                allOf(withId(R.id.activity_wizard_button_next), withText("Next"),
                        withParent(withId(R.id.activity_wizard_buttons)),
                        isDisplayed()));
        button.perform(click());

        ViewInteraction checkBox3 = onView(
                allOf(withId(R.id.view_intent_wizard_page_check_accept_copyright), withText("I will not use FrostWire for copyright infringement"), isDisplayed()));
        checkBox3.perform(click());

        ViewInteraction checkBox4 = onView(
                allOf(withId(R.id.view_intent_wizard_page_check_accept_tou), withText("I have read and agree to the"), isDisplayed()));
        checkBox4.perform(click());

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

        ViewInteraction checkableRelativeLayout = onView(
                allOf(childAtPosition(
                        allOf(withId(R.id.left_drawer),
                                withParent(withId(R.id.activity_main_left_drawer))),
                        5),
                        isDisplayed()));
        checkableRelativeLayout.perform(click());

        ViewInteraction linearLayout2 = onView(
                allOf(childAtPosition(
                        allOf(withId(android.R.id.list),
                                withParent(withClassName(is("android.widget.LinearLayout")))),
                        4),
                        isDisplayed()));
        linearLayout2.perform(click());

        ViewInteraction checkBox5 = onView(
                allOf(withId(R.id.view_preference_checkbox_header_checkbox), isDisplayed()));
        checkBox5.perform(click());

        ViewInteraction linearLayout3 = onView(
                allOf(withContentDescription("Search Settings, Navigate up"),
                        withParent(allOf(withClassName(is("com.android.internal.widget.ActionBarView")),
                                withParent(withClassName(is("com.android.internal.widget.ActionBarContainer"))))),
                        isDisplayed()));
        linearLayout3.perform(click());

        ViewInteraction linearLayout4 = onView(
                allOf(withContentDescription("Settings, Navigate up"),
                        withParent(allOf(withClassName(is("com.android.internal.widget.ActionBarView")),
                                withParent(withClassName(is("com.android.internal.widget.ActionBarContainer"))))),
                        isDisplayed()));
        linearLayout4.perform(click());

    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
