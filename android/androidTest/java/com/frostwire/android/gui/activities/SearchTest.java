package com.frostwire.android.gui.activities;

import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.Espresso;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;

import com.frostwire.android.R;
import com.frostwire.android.test.utils.PreferencesManipulator;
import com.frostwire.android.test.utils.WaitUntilVisibleIdlingResource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.pressKey;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.core.AllOf.allOf;


@RunWith(AndroidJUnit4.class)  //To access to an activity without extending ActivityInstrumentationTestCase2
@LargeTest
public class SearchTest {

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
    public void search() {
        //Layout: R.layout.fragment_search
        onView(withId(R.id.fragment_search_input)).check(matches(isDisplayed()));
        /*
         * The textview is in FWAutoCompleteTextView, which is in the layout of the custom view ClearableEditTextView,
         * which is in the custom view SearchInputView which is in the layout R.layout.fragment_search
         */
        onView(allOf(withId(R.id.view_clearable_edit_text_input), isDescendantOfA(withId(R.id.fragment_search_input))))
                .check(matches(isDisplayed()))
                .perform(typeText("android")) //Search android so I can get results on applications too
                .perform(pressKey(KeyEvent.KEYCODE_ENTER)); //press enter


        WaitUntilVisibleIdlingResource resource = new WaitUntilVisibleIdlingResource(
                activityRule.getActivity().findViewById(R.id.fragment_search_list),
                activityRule.getActivity().findViewById(R.id.view_search_progress_text_no_results_feedback)
        );
        Espresso.registerIdlingResources(
                resource
        );

        //Layout: R.layout.view_searchinput_radiogroup
        //Checking results in all labels

        checkForAtLeastOneResult(R.id.view_search_input_radio_audio);
        checkForAtLeastOneResult(R.id.view_search_input_radio_videos);
        checkForAtLeastOneResult(R.id.view_search_input_radio_pictures);
        checkForAtLeastOneResult(R.id.view_search_input_radio_applications);
        checkForAtLeastOneResult(R.id.view_search_input_radio_documents);
        checkForAtLeastOneResult(R.id.view_search_input_radio_torrents);

        Espresso.unregisterIdlingResources(resource);

    }
    //Check for at least one result in all labels
    private void checkForAtLeastOneResult(int radioButtonId) {
        onView(allOf(withId(radioButtonId), isDescendantOfA(withId(R.id.fragment_search_input))))
                .check(matches(isDisplayed()))
                .perform(click());

        onData(anything())
                .inAdapterView(withId(R.id.fragment_search_list)).atPosition(0).check(matches(isDisplayed()));
    }


}
