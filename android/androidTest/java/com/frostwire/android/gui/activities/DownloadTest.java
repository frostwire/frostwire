 package com.frostwire.android.gui.activities;

 import android.preference.CheckBoxPreference;
 import android.support.test.InstrumentationRegistry;
 import android.support.test.espresso.DataInteraction;
 import android.support.test.espresso.Espresso;
 import android.support.test.espresso.NoMatchingViewException;
 import android.support.test.espresso.ViewAssertion;
 import android.support.test.espresso.ViewInteraction;
 import android.support.test.espresso.matcher.BoundedMatcher;
 import android.support.test.espresso.matcher.PreferenceMatchers;
 import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
 import android.widget.CheckBox;
 import android.widget.LinearLayout;
 import android.widget.ListView;
 import android.widget.RelativeLayout;
 import android.widget.TextView;

 import com.frostwire.android.R;
import com.frostwire.android.test.utils.PreferencesManipulator;
import com.frostwire.android.test.utils.WaitUntilListViewNotEmptyIdlingResource;
import com.frostwire.android.test.utils.WaitUntilVisibleIdlingResource;
import com.frostwire.search.FileSearchResult;
import com.frostwire.transfers.Transfer;

 import org.hamcrest.CoreMatchers;
 import org.hamcrest.CustomMatcher;
 import org.hamcrest.Description;
import org.hamcrest.Matcher;
 import org.hamcrest.Matchers;
 import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.pressKey;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
 import static android.support.test.espresso.matcher.ViewMatchers.isChecked;
 import static android.support.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
 import static android.support.test.espresso.matcher.ViewMatchers.withClassName;
 import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
 import static android.support.test.espresso.matcher.ViewMatchers.withId;
 import static android.support.test.espresso.matcher.ViewMatchers.withParent;
 import static android.support.test.espresso.matcher.ViewMatchers.withResourceName;
 import static android.support.test.espresso.matcher.ViewMatchers.withTagKey;
 import static android.support.test.espresso.matcher.ViewMatchers.withText;
 import static org.hamcrest.CoreMatchers.is;
 import static org.hamcrest.core.AllOf.allOf;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class DownloadTest {

    boolean mSelectAllIsChecked;

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule<MainActivity>(MainActivity.class) {
        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();
            PreferencesManipulator prefsManipulator =
                    new PreferencesManipulator(InstrumentationRegistry.getTargetContext());
            prefsManipulator.userHasStartedAppOnce();
        }
    };

    @Test
    public void searchAudio_download() {

        //muckachina test part one (filtering Archive source)
        // public void filterArchive() {

        ViewInteraction linearLayout = onView(
                allOf(withContentDescription("FrostWire, Open navigation drawer"),
                        withParent(allOf(withClassName(is("com.android.internal.widget.ActionBarView")),
                                withParent(withClassName(is("com.android.internal.widget.ActionBarContainer"))))),
                        isDisplayed()));
        linearLayout.perform(click());

        final ViewInteraction checkableRelativeLayout = onView(
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


        ViewInteraction selectAllInteraction = onView(allOf(withId(R.id.view_preference_checkbox_header_checkbox), isDisplayed()));

        // Let's make sure Archive.org is the only one checked, by deselecting all, archive.org
        // should remain checked.
        selectAllInteraction.check(new ViewAssertion() {
            @Override
            public void check(View view, NoMatchingViewException noViewFoundException) {
                CheckBox checkbox = (CheckBox) view;
                mSelectAllIsChecked = checkbox.isChecked();
            }
        });
        ensureArchiveOrgIsTheOnlyOne(selectAllInteraction);


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
        // End muckachina test part one


        //Layout: R.layout.fragment_search
        onView(withId(R.id.fragment_search_input)).check(matches(isDisplayed()));
        /*
         * The textview is in FWAutoCompleteTextView, which is in the layout of the custom view ClearableEditTextView,
         * which is in the custom view SearchInputView which is in the layout R.layout.fragment_search
         */
        onView(allOf(withId(R.id.view_clearable_edit_text_input), isDescendantOfA(withId(R.id.fragment_search_input))))
                .check(matches(isDisplayed()))
                .perform(typeText("Creative Commons"))
                .perform(pressKey(KeyEvent.KEYCODE_ENTER));


        WaitUntilVisibleIdlingResource resource = new WaitUntilVisibleIdlingResource(
                activityRule.getActivity().findViewById(R.id.fragment_search_list),
                activityRule.getActivity().findViewById(R.id.view_search_progress_text_no_results_feedback)
        );
        Espresso.registerIdlingResources(
                resource
        );

        //Layout: R.layout.view_searchinput_radiogroup
        onView(allOf(withId(R.id.view_search_input_radio_audio), isDescendantOfA(withId(R.id.fragment_search_input))))
                .check(matches(isDisplayed()))
                .perform(click());


        FirstFileSearchResultMatcher matchFirstFileSearchResult = new FirstFileSearchResultMatcher();
        //Layout for items in the search results: R.layout.view_bittorrent_search_result_list_item

        //Check that the file name contains "SoundCloud" and "Creative Commons"
        onData(matchFirstFileSearchResult)
                .inAdapterView(withId(R.id.fragment_search_list))
                //Find the download button in the list item
                .onChildView(withId(R.id.view_bittorrent_search_result_list_item_download_icon))
                //Click the download button
                .perform(click());

        //The dialog should show asking us to confirm (NewTransferDialog)
        //Layout: R.layout.dialog_default_checkbox
        onView(withId(R.id.dialog_default_checkbox_button_yes))
                .check(matches(isDisplayed()))
                .perform(click());

        Espresso.unregisterIdlingResources(resource);

        //Layout: R.layout.fragment_transfers
        //Switch to the downloading list
        onView(withId(R.id.fragment_transfers_button_select_downloading))
                .check(matches(isDisplayed()))
                .perform(click());

        //Layout of list item in list view inside of fragment_transfers: R.layout.view_transfer_item_list_item
        //Make sure we're downloading the file we chose from the search
        //Switch to the completed list
        onView(withId(R.id.fragment_transfers_button_select_completed))
                .check(matches(isDisplayed()))
                .perform(click());

        //Register the idling resource to wait for the download to finish
        WaitUntilListViewNotEmptyIdlingResource idlingResource = new WaitUntilListViewNotEmptyIdlingResource(
                (ListView) activityRule.getActivity().findViewById(R.id.fragment_transfers_list)
        );
        Espresso.registerIdlingResources(idlingResource);


        ExactFileSearchResultMatcher downloadingChosenResultMatcher = new ExactFileSearchResultMatcher(matchFirstFileSearchResult.mSearchResult);
        onData(downloadingChosenResultMatcher)
                .inAdapterView(withId(R.id.fragment_transfers_list))
                .check(matches(isDisplayed()));

        onData(downloadingChosenResultMatcher)
                .inAdapterView(withId(R.id.fragment_transfers_list))
                .check(matches(isDisplayed()));

        Espresso.unregisterIdlingResources(idlingResource);
    }

    private void ensureArchiveOrgIsTheOnlyOne(ViewInteraction selectAllInteraction) {
        // (we do this outside the ViewAssertion code as otherwise we freeze the UI thread (doPerform) and the check never
        // ends.
        // if it's not checked... let's make sure we check it, and uncheck it.
        if (!mSelectAllIsChecked) {
            selectAllInteraction.perform(click());
            selectAllInteraction.perform(click());
        }
        // if it's checked, let's make sure we uncheck it.
        else {
            selectAllInteraction.perform(click());
        }
    }

    //muckachina test second part
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
    //end muckachina test second part

    private class FirstFileSearchResultMatcher extends BoundedMatcher<Object, FileSearchResult> {
        public FileSearchResult mSearchResult;

        public FirstFileSearchResultMatcher() {
            super(FileSearchResult.class);
        }

        @Override
        protected boolean matchesSafely(FileSearchResult item) {
            if (mSearchResult != null) {
                //We already found a match. We only want 1 result
                return false;
            }
            if (item.getDisplayName().toLowerCase().contains("creative commons")
                    && item.getSource().toLowerCase().contains("archive.org")) {
                mSearchResult = item;
                return true;
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Match first FileSearchResult");
        }
    }

    private class ExactFileSearchResultMatcher extends BoundedMatcher<Object, Transfer> {
        public final FileSearchResult mSearchResultToMatch;

        public ExactFileSearchResultMatcher(FileSearchResult searchResult) {
            super(Transfer.class);
            mSearchResultToMatch = searchResult;
        }

        @Override
        protected boolean matchesSafely(Transfer item) {
            return item.getDisplayName().equals(mSearchResultToMatch.getDisplayName());
        }

        @Override
        public void describeTo(Description description) {

        }
    }

}