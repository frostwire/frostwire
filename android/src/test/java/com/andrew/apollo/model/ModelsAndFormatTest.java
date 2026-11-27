/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.format.PrefixHighlighter;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.MusicViewHolder;
import com.frostwire.android.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The Apollo models compare by their fields; the formatters and the list recycler. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ModelsAndFormatTest {

  @Test
  public void songsAreEqualWhenEveryFieldIsAndPrintTheirName() {
    Song song = new Song(1, "Hey", "Ann", "LP", 100);

    assertEquals(song, new Song(1, "Hey", "Ann", "LP", 100));
    assertEquals(song.hashCode(), new Song(1, "Hey", "Ann", "LP", 100).hashCode());
    assertEquals(song, song);
    assertNotEquals(song, new Song(2, "Hey", "Ann", "LP", 100));
    assertNotEquals(song, new Song(1, "Ho", "Ann", "LP", 100));
    assertNotEquals(song, new Song(1, "Hey", "Bob", "LP", 100));
    assertNotEquals(song, new Song(1, "Hey", "Ann", "EP", 100));
    assertNotEquals(song, new Song(1, "Hey", "Ann", "LP", 101));
    assertNotEquals(song, null);
    assertNotEquals(song, "Hey");
    assertEquals("Hey", song.toString());
    assertEquals(
        "null fields are fine", new Song(1, null, null, null, 0), new Song(1, null, null, null, 0));
    assertEquals(-1, song.mPlaylistMemberId);
  }

  @Test
  public void albumsAreEqualWhenEveryFieldIsAndPrintTheirName() {
    Album album = new Album(1, "LP", "Ann", 10, "1999");

    assertEquals(album, new Album(1, "LP", "Ann", 10, "1999"));
    assertEquals(album.hashCode(), new Album(1, "LP", "Ann", 10, "1999").hashCode());
    assertEquals(album, album);
    assertNotEquals(album, new Album(2, "LP", "Ann", 10, "1999"));
    assertNotEquals(album, new Album(1, "EP", "Ann", 10, "1999"));
    assertNotEquals(album, new Album(1, "LP", "Bob", 10, "1999"));
    assertNotEquals(album, new Album(1, "LP", "Ann", 11, "1999"));
    assertNotEquals(album, new Album(1, "LP", "Ann", 10, "2000"));
    assertNotEquals(album, null);
    assertNotEquals(album, "LP");
    assertEquals("LP", album.toString());
    assertEquals(new Album(1, null, null, 0, null), new Album(1, null, null, 0, null));
  }

  @Test
  public void artistsAreEqualWhenEveryFieldIsAndPrintTheirName() {
    Artist artist = new Artist(1, "Ann", 14, 2);

    assertEquals(artist, new Artist(1, "Ann", 14, 2));
    assertEquals(artist.hashCode(), new Artist(1, "Ann", 14, 2).hashCode());
    assertEquals(artist, artist);
    assertNotEquals(artist, new Artist(2, "Ann", 14, 2));
    assertNotEquals(artist, new Artist(1, "Bob", 14, 2));
    assertNotEquals(artist, new Artist(1, "Ann", 15, 2));
    assertNotEquals(artist, new Artist(1, "Ann", 14, 3));
    assertNotEquals(artist, null);
    assertNotEquals(artist, "Ann");
    assertEquals("Ann", artist.toString());
    assertEquals(new Artist(1, null, 0, 0), new Artist(1, null, 0, 0));
  }

  @Test
  public void playlistsAndGenresAreEqualByIdAndName() {
    Playlist playlist = new Playlist(1, "Road");
    Genre genre = new Genre(2, "Rock");

    assertEquals(playlist, new Playlist(1, "Road"));
    assertEquals(playlist.hashCode(), new Playlist(1, "Road").hashCode());
    assertNotEquals(playlist, new Playlist(2, "Road"));
    assertNotEquals(playlist, new Playlist(1, "Gym"));
    assertNotEquals(playlist, null);
    assertNotEquals(playlist, "Road");
    assertEquals(playlist, playlist);
    assertEquals("Road", playlist.toString());
    assertEquals(genre, new Genre(2, "Rock"));
    assertEquals(genre.hashCode(), new Genre(2, "Rock").hashCode());
    assertNotEquals(genre, new Genre(3, "Rock"));
    assertNotEquals(genre, new Genre(2, "Jazz"));
    assertNotEquals(genre, null);
    assertNotEquals(genre, "Rock");
    assertEquals(genre, genre);
    assertEquals("Rock", genre.toString());
  }

  @Test
  public void capitalizingUpperCasesEachWordAfterTheDelimiters() {
    assertEquals("Hello World", Capitalize.capitalize("hello world"));
    assertEquals("Hello-World Again", Capitalize.capitalize("hello-world again", '-', ' '));
    assertEquals("a b", Capitalize.capitalize("a b", new char[0]));
    assertEquals("", Capitalize.capitalize(""));
    assertNull(Capitalize.capitalize(null));
  }

  @Test
  public void aTypedPrefixIsHighlightedAtTheStartOfTheFirstWordItMatches() {
    PrefixHighlighter highlighter =
        new PrefixHighlighter(ApplicationProvider.getApplicationContext());

    CharSequence result = highlighter.apply("The Beatles", new char[] {'B', 'E'});

    SpannableString spanned = (SpannableString) result;
    ForegroundColorSpan[] spans = spanned.getSpans(0, spanned.length(), ForegroundColorSpan.class);
    assertEquals(1, spans.length);
    assertEquals(4, spanned.getSpanStart(spans[0]));
    assertEquals(6, spanned.getSpanEnd(spans[0]));
  }

  @Test
  public void aPrefixThatMatchesNothingLeavesTheTextAlone() {
    PrefixHighlighter highlighter =
        new PrefixHighlighter(ApplicationProvider.getApplicationContext());

    assertEquals("The Beatles", highlighter.apply("The Beatles", new char[] {'Z'}));
    assertEquals("ab", highlighter.apply("ab", new char[] {'A', 'B', 'C'}));
    assertEquals("", highlighter.apply("", new char[] {'A'}));
    assertEquals("abc", highlighter.apply("abc", new char[0]));
    assertEquals("abc", highlighter.apply("abc", null));
    assertEquals("-", highlighter.apply("-", new char[] {'A'}));
  }

  @Test
  public void settingHighlightedTextOnAViewNeedsAViewTextAndPrefix() {
    PrefixHighlighter highlighter =
        new PrefixHighlighter(ApplicationProvider.getApplicationContext());
    TextView view = new TextView(ApplicationProvider.getApplicationContext());

    highlighter.setText(view, "Queen", new char[] {'Q'});
    assertTrue(view.getText() instanceof android.text.Spanned);
    view.setText("untouched");
    highlighter.setText(view, "", new char[] {'Q'});
    highlighter.setText(view, "Queen", null);
    highlighter.setText(view, "Queen", new char[0]);
    highlighter.setText(null, "Queen", new char[] {'Q'});

    assertEquals("untouched", view.getText().toString());
  }

  @Test
  public void recyclingAListRowClearsItsImagesAndTexts() {
    android.content.Context context = ApplicationProvider.getApplicationContext();
    FrameLayout row = new FrameLayout(context);
    ImageView image = viewWithId(new ImageView(context), R.id.list_item_image);
    ImageView background = viewWithId(new ImageView(context), R.id.list_item_background);
    TextView lineOne = viewWithId(new TextView(context), R.id.line_one);
    TextView lineTwo = viewWithId(new TextView(context), R.id.line_two);
    TextView lineThree = viewWithId(new TextView(context), R.id.line_three);
    for (View v : new View[] {image, background, lineOne, lineTwo, lineThree}) {
      row.addView(v);
    }
    image.setImageDrawable(new ColorDrawable(Color.RED));
    background.setImageDrawable(new ColorDrawable(Color.BLUE));
    lineOne.setText("one");
    lineTwo.setText("two");
    lineThree.setText("three");

    RecycleHolder holder = new RecycleHolder();
    holder.onMovedToScrapHeap(row);

    assertFalse("the album art is gone", image.getDrawable() instanceof ColorDrawable);
    assertFalse("the background is gone", background.getDrawable() instanceof ColorDrawable);
    assertEquals("", lineOne.getText().toString());
    assertEquals("", lineTwo.getText().toString());
    assertEquals("", lineThree.getText().toString());
    MusicViewHolder tag = (MusicViewHolder) row.getTag();
    assertSame(row, tag.mConvertView.get());
    holder.onMovedToScrapHeap(row);
    assertSame("the holder is reused", tag, row.getTag());
    holder.onMovedToScrapHeap(null);
    assertFalse(new MusicViewHolder.DataHolder().mParentId != -1);
  }

  private static <V extends View> V viewWithId(V view, int id) {
    view.setId(id);
    return view;
  }
}
