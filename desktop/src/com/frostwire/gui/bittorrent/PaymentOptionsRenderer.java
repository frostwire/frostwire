/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.bittorrent.PaymentOptions.PaymentMethod;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;
import com.limegroup.gnutella.gui.tables.TableActionLabel;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PaymentOptionsRenderer extends FWAbstractJPanelTableCellRenderer {
    private static final Logger LOG = Logger.getLogger(PaymentOptionsRenderer.class);
    private final static ImageIcon bitcoin_enabled;
    private final static ImageIcon bitcoin_disabled;
    private final static ImageIcon paypal_enabled;
    private final static ImageIcon paypal_disabled;

    static {
        bitcoin_enabled = GUIMediator.getThemeImage("bitcoin_enabled");
        bitcoin_disabled = GUIMediator.getThemeImage("bitcoin_disabled");
        paypal_enabled = GUIMediator.getThemeImage("paypal_enabled");
        paypal_disabled = GUIMediator.getThemeImage("paypal_disabled");
    }

    private final TableActionLabel labelBitcoin;
    private final TableActionLabel labelPaypal;
    //mutable
    private PaymentOptions paymentOptions;

    public PaymentOptionsRenderer() {
        labelBitcoin = new TableActionLabel(bitcoin_enabled, bitcoin_disabled);
        labelPaypal = new TableActionLabel(paypal_enabled, paypal_disabled);
        setupUI();
    }

    private void setupUI() {
        setEnabled(true);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        //We use "Bitcoin" for the protocol (upper case B), and "bitcoins" for the units of currency (lower case b)
        labelBitcoin.setToolTipText(I18n.tr("Name your price, Send a Tip or Donation in") + " " + I18n.tr("bitcoins"));
        labelPaypal.setToolTipText(I18n.tr("Name your price, Send a Tip or Donation via Paypal"));
        initMouseListeners();
        initComponentsLayout();
    }

    private void initComponentsLayout() {
        setLayout(new MigLayout("gap 2px, fillx, center, insets 5px 5px 5px 5px", "[20px!][20px!]"));
        add(labelBitcoin, "width 20px!, growx 0, aligny top, push");
        add(labelPaypal, "width 20px!, growx 0, aligny top, push");
    }

    private void initMouseListeners() {
        labelBitcoin.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelBitcoin_mouseReleased(e);
            }
        });
        labelPaypal.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelPaypal_mouseReleased(e);
            }
        });
    }

    protected void updateUIData(Object value, JTable table, int row, int column) {
        try {
            boolean showSolid = mouseIsOverRow(table, row);
            boolean gotPaymentOptions = false;
            if (value instanceof PaymentOptions) {
                gotPaymentOptions = paymentOptions != null;
                paymentOptions = (PaymentOptions) value;
            }
            labelBitcoin.updateActionIcon(gotPaymentOptions && !StringUtils.isNullOrEmpty(paymentOptions.bitcoin), showSolid);
            labelPaypal.updateActionIcon(gotPaymentOptions && !StringUtils.isNullOrEmpty(paymentOptions.paypalUrl), showSolid);
        } catch (Throwable t) {
            LOG.error("Unable to update UI data", t);
        }
    }

    private void openPaymentOptionsURL(PaymentOptions paymentOptions, PaymentMethod method) {
        String paymentOptionsUrl;
        if (method == PaymentMethod.PAYPAL && !StringUtils.isNullOrEmpty(paymentOptions.paypalUrl)) {
            paymentOptionsUrl = paymentOptions.paypalUrl;
        } else {
            String paymentOptionsJSON = UrlUtils.encode(JsonUtils.toJson(paymentOptions).replaceAll("\n", ""));
            String title = UrlUtils.encode(paymentOptions.getItemName());
            paymentOptionsUrl = "https://www.frostwire.com/tips/?method=" + method.toString() + "&po=" + paymentOptionsJSON + "&title=" + title;
        }
        GUIMediator.openURL(paymentOptionsUrl);
    }

    private void labelBitcoin_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && labelBitcoin.isActionEnabled()) {
            if (paymentOptions != null && !StringUtils.isNullOrEmpty(paymentOptions.bitcoin)) {
                openPaymentOptionsURL(paymentOptions, PaymentMethod.BITCOIN);
            }
        }
    }

    private void labelPaypal_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && labelPaypal.isActionEnabled()) {
            if (paymentOptions != null && !StringUtils.isNullOrEmpty(paymentOptions.paypalUrl)) {
                openPaymentOptionsURL(paymentOptions, PaymentMethod.PAYPAL);
            }
        }
    }
}
