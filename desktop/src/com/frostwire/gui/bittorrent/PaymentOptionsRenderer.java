/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
            paymentOptionsUrl = "http://www.frostwire.com/tips/?method=" + method.toString() + "&po=" + paymentOptionsJSON + "&title=" + title;
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
