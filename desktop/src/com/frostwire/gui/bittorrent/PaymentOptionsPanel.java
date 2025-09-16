/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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
import com.frostwire.gui.bittorrent.CryptoCurrencyTextField.CurrencyURIPrefix;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LimeTextField;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

class PaymentOptionsPanel extends JPanel {
    private final JCheckBox confirmationCheckbox;
    private final CryptoCurrencyTextField bitcoinAddress;
    private final LimeTextField paypalUrlAddress;

    public PaymentOptionsPanel() {
        initBorder();
        confirmationCheckbox = new JCheckBox("<html><strong>" + I18n.tr("I am the content creator or I have the right to collect financial contributions for this work.") + "</strong><br>" + I18n.tr("I understand that incurring in financial gains from unauthorized copyrighted works can make me liable for counterfeiting and criminal copyright infringement.") + "</html>");
        bitcoinAddress = new CryptoCurrencyTextField(CurrencyURIPrefix.BITCOIN);
        paypalUrlAddress = new LimeTextField();
        setLayout(new MigLayout("fill"));
        initComponents();
        initListeners();
    }

    private void initListeners() {
        confirmationCheckbox.addActionListener(e -> onConfirmationCheckbox());
        bitcoinAddress.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                onCryptoAddressPressed(bitcoinAddress);
            }
        });
    }

    private void onConfirmationCheckbox() {
        bitcoinAddress.setEnabled(confirmationCheckbox.isSelected());
        paypalUrlAddress.setEnabled(confirmationCheckbox.isSelected());
    }

    private void onCryptoAddressPressed(CryptoCurrencyTextField textField) {
        boolean hasValidPrefixOrNoPrefix = false;
        hasValidPrefixOrNoPrefix = textField.hasValidPrefixOrNoPrefix();
        if (!textField.hasValidAddress() || !hasValidPrefixOrNoPrefix) {
            textField.setForeground(Color.red);
        } else {
            textField.setForeground(Color.black);
        }
        int caretPosition = textField.getCaretPosition();
        int lengthBefore = textField.getText().length();
        int selectionStart = textField.getSelectionStart();
        int selectionEnd = textField.getSelectionEnd();
        textField.setText(textField.getText().replaceAll(" ", ""));
        int lengthAfter = textField.getText().length();
        if (lengthAfter < lengthBefore) {
            int delta = (lengthBefore - lengthAfter);
            caretPosition -= delta;
            selectionEnd -= delta;
        }
        textField.setCaretPosition(caretPosition);
        textField.setSelectionStart(selectionStart);
        textField.setSelectionEnd(selectionEnd);
    }

    private void initComponents() {
        add(confirmationCheckbox, "aligny top, gapbottom 10px, wrap, span");
        add(new JLabel("<html>" + I18n.tr("<strong>Bitcoin</strong> receiving wallet address") + "</html>"), "wrap, span");
        add(new JLabel(GUIMediator.getThemeImage("bitcoin_accepted.png")), "aligny top");
        bitcoinAddress.setPrompt("bitcoin:1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        add(bitcoinAddress, "aligny top, growx, gapbottom 10px, wrap");
        add(new JLabel("<html>" + I18n.tr("<strong>Paypal</strong> payment/donation page url") + "</html>"), "wrap, span");
        add(new JLabel(GUIMediator.getThemeImage("paypal_accepted.png")), "aligny top");
        paypalUrlAddress.setPrompt("http://your.paypal.button/url/here");
        add(paypalUrlAddress, "aligny top, growx, push");
        onConfirmationCheckbox();
    }

    private void initBorder() {
        Border titleBorder = BorderFactory.createTitledBorder(I18n
                .tr("\"Name your price\", \"Tips\", \"Donations\" payment options"));
        Border lineBorder = BorderFactory.createLineBorder(ThemeMediator.LIGHT_BORDER_COLOR);
        Border border = BorderFactory.createCompoundBorder(lineBorder, titleBorder);
        setBorder(border);
    }

    public PaymentOptions getPaymentOptions() {
        PaymentOptions result = null;
        if (confirmationCheckbox.isSelected()) {
            boolean validBitcoin = bitcoinAddress.hasValidAddress();
            if (validBitcoin || (paypalUrlAddress.getText() != null && !paypalUrlAddress.getText().isEmpty())) {
                final String bitcoin = validBitcoin ? bitcoinAddress.normalizeValidAddress() : null;
                final String paypal = (paypalUrlAddress != null && paypalUrlAddress.getText() != null && !paypalUrlAddress.getText().isEmpty()) ? paypalUrlAddress.getText() : null;
                result = new PaymentOptions(bitcoin, paypal);
            }
        }
        return result;
    }

    public boolean hasPaymentOptions() {
        return getPaymentOptions() != null;
    }
}