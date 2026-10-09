/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;

/**
 * Explains that seeding a torrent publishes it to the P2P network and asks two separate questions:
 * whether to join the IceBridge network (search, and be found by keyword), and whether to also
 * share the catalog so people can browse everything being seeded and crawlers can index it.
 * Unchecking the catalog never takes the user off the network.
 *
 * @author gubatron
 * @author aldenml
 */
public class PublicCatalogWizardPage extends RelativeLayout implements WizardPageView {

  private OnCompleteListener listener;
  private CheckBox checkJoinNetwork;
  private CheckBox checkPublicCatalog;
  private TextView textCatalogNote;

  public PublicCatalogWizardPage(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public boolean hasPrevious() {
    return true;
  }

  @Override
  public boolean hasNext() {
    return true;
  }

  @Override
  public void load() {
    ConfigurationManager CM = ConfigurationManager.instance();
    checkJoinNetwork.setChecked(CM.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED));
    checkPublicCatalog.setChecked(CM.getBoolean(Constants.PREF_KEY_ICEBRIDGE_PUBLIC_CATALOG));
    updateCatalogEnabled();
    validate();
  }

  @Override
  public void finish() {
    ConfigurationManager CM = ConfigurationManager.instance();
    CM.setBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED, checkJoinNetwork.isChecked());
    CM.setBoolean(Constants.PREF_KEY_ICEBRIDGE_PUBLIC_CATALOG, checkPublicCatalog.isChecked());
  }

  @Override
  public void setOnCompleteListener(OnCompleteListener listener) {
    this.listener = listener;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    View.inflate(getContext(), R.layout.view_public_catalog_wizard_page, this);
    checkJoinNetwork = findViewById(R.id.view_public_catalog_wizard_page_check_join_network);
    checkPublicCatalog = findViewById(R.id.view_public_catalog_wizard_page_check_public_catalog);
    textCatalogNote = findViewById(R.id.view_public_catalog_wizard_page_catalog_note);
    checkJoinNetwork.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          updateCatalogEnabled();
          validate();
        });
    checkPublicCatalog.setOnCheckedChangeListener((buttonView, isChecked) -> validate());
  }

  protected void onComplete(boolean complete) {
    if (listener != null) {
      listener.onComplete(this, complete);
    }
  }

  /** Sharing the catalog only makes sense on the network: leaving it disables the second choice. */
  private void updateCatalogEnabled() {
    boolean joined = checkJoinNetwork.isChecked();
    checkPublicCatalog.setEnabled(joined);
    checkPublicCatalog.setTextColor(
        joined
            ? Color.WHITE
            : getContext().getResources().getColor(R.color.app_text_wizard_dark, null));
    textCatalogNote.setTextColor(
        getContext()
            .getResources()
            .getColor(joined ? R.color.app_text_wizard : R.color.app_text_wizard_dark, null));
  }

  /** Both choices are optional, so the page is always complete. */
  private void validate() {
    onComplete(true);
  }
}
