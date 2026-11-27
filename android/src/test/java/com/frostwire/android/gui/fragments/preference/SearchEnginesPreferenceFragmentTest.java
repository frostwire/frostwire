/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.preference.CheckBoxPreference;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.AndroidSearchEngineAdapter;
import com.frostwire.android.gui.AndroidSearchEngines;
import com.frostwire.android.gui.fragments.FragmentTestHost;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import java.util.EnumSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchEnginesPreferenceFragmentTest {
  private MockedStatic<ConfigurationRepository> repository;
  private MockedStatic<AndroidSearchEngines> engines;
  private final Set<SearchEngineID> notOffered = EnumSet.noneOf(SearchEngineID.class);

  @Before
  public void setUp() {
    repository = mockStatic(ConfigurationRepository.class);
    engines = mockStatic(AndroidSearchEngines.class);
    engines
        .when(() -> AndroidSearchEngines.byId(any()))
        .thenAnswer(
            invocation -> {
              SearchEngine engine = mock(SearchEngine.class);
              when(engine.isOffered()).thenReturn(!notOffered.contains(invocation.getArgument(0)));
              return engine;
            });
  }

  @After
  public void tearDown() {
    engines.close();
    repository.close();
  }

  private static CheckBoxPreference engine(SearchEnginesPreferenceFragment f, SearchEngineID id) {
    return f.findPreference(AndroidSearchEngineAdapter.preferenceKey(id));
  }

  private static CheckBoxPreference selectAll(SearchEnginesPreferenceFragment f) {
    return f.findPreference(SearchEnginesPreferenceFragment.PREF_KEY_SEARCH_SELECT_ALL);
  }

  private static SearchEngineID someEngine(SearchEnginesPreferenceFragment f) {
    for (SearchEngineID id : SearchEngineID.values()) {
      if (id != SearchEngineID.TELLURIDE_ID
          && id != SearchEngineID.DISTRIBUTED_ID
          && engine(f, id) != null
          && !Constants.PREF_KEY_SEARCH_USE_DISTRIBUTED.equals(engine(f, id).getKey())) {
        return id;
      }
    }
    throw new AssertionError("no engine preference");
  }

  @Test
  public void selectAllStartsUncheckedWhenSomeEngineIsOff() {
    SearchEnginesPreferenceFragment fragment = new SearchEnginesPreferenceFragment();
    FragmentTestHost.launch(fragment);
    assertFalse(selectAll(fragment).isChecked());
    assertEquals(
        fragment.getString(R.string.select_all), selectAll(fragment).getTitle().toString());
  }

  @Test
  public void selectAllChecksEveryEngineAndDeselectAllClearsThem() {
    SearchEnginesPreferenceFragment fragment = new SearchEnginesPreferenceFragment();
    FragmentTestHost.launch(fragment);
    SearchEngineID id = someEngine(fragment);

    selectAll(fragment).performClick();
    assertTrue(selectAll(fragment).isChecked());
    assertTrue(engine(fragment, id).isChecked());
    assertEquals(
        fragment.getString(R.string.deselect_all), selectAll(fragment).getTitle().toString());

    selectAll(fragment).performClick();
    assertFalse(selectAll(fragment).isChecked());
    assertFalse(engine(fragment, id).isChecked());
    assertEquals(
        fragment.getString(R.string.select_all), selectAll(fragment).getTitle().toString());
  }

  @Test
  public void uncheckingAnEngineUnchecksSelectAllAndCheckingTheLastOneChecksIt() {
    SearchEnginesPreferenceFragment fragment = new SearchEnginesPreferenceFragment();
    FragmentTestHost.launch(fragment);
    SearchEngineID id = someEngine(fragment);
    selectAll(fragment).performClick();
    assertTrue(selectAll(fragment).isChecked());

    engine(fragment, id).performClick(); // unchecks it
    assertFalse(engine(fragment, id).isChecked());
    assertFalse(selectAll(fragment).isChecked());
    assertEquals(
        fragment.getString(R.string.select_all), selectAll(fragment).getTitle().toString());

    engine(fragment, id).performClick(); // all are checked again
    assertTrue(engine(fragment, id).isChecked());
    assertTrue(selectAll(fragment).isChecked());
    assertEquals(
        fragment.getString(R.string.deselect_all), selectAll(fragment).getTitle().toString());
  }

  @Test
  public void enginesThatAreNotOfferedAreNotListed() {
    SearchEnginesPreferenceFragment probe = new SearchEnginesPreferenceFragment();
    FragmentTestHost.launch(probe);
    SearchEngineID hidden = someEngine(probe);

    notOffered.add(hidden);
    SearchEnginesPreferenceFragment fragment = new SearchEnginesPreferenceFragment();
    FragmentTestHost.launch(fragment);
    assertNull(engine(fragment, hidden));
    assertNotNull(selectAll(fragment));
  }

  @Test
  public void theListIsRenderedWithEachEnginesCheckedBackground() {
    SearchEnginesPreferenceFragment fragment = new SearchEnginesPreferenceFragment();
    FragmentTestHost.launch(fragment);
    selectAll(fragment).performClick();
    PreferenceTestSupport.layoutList(fragment);
    assertTrue(fragment.getListView().getChildCount() > 0);
  }
}
